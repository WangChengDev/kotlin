/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.js.inline.util

import com.google.dart.compiler.backend.js.ast.*
import com.google.dart.compiler.backend.js.ast.metadata.staticRef

import org.jetbrains.kotlin.js.inline.util.collectors.InstanceCollector
import org.jetbrains.kotlin.js.inline.util.collectors.PropertyCollector
import org.jetbrains.kotlin.js.translate.expression.*
import org.jetbrains.kotlin.js.translate.utils.JsAstUtils
import java.util.*

fun collectFunctionReferencesInside(scope: JsNode): List<JsName> =
        collectReferencedNames(scope).filter { it.staticRef is JsFunction }

private fun collectReferencedNames(scope: JsNode): Set<JsName> {
    val references = IdentitySet<JsName>()

    object : RecursiveJsVisitor() {
        override fun visitBreak(x: JsBreak) { }

        override fun visitContinue(x: JsContinue) { }

        override fun visit(x: JsVars.JsVar) {
            val initializer = x.initExpression
            if (initializer != null) {
                accept(initializer)
            }
        }

        override fun visitNameRef(nameRef: JsNameRef) {
            super.visitNameRef(nameRef)
            val name = nameRef.name
            if (name != null) {
                references += name
            }
        }
    }.accept(scope)

    return references
}

fun collectUsedNames(scope: JsNode): Set<JsName> {
    val references = IdentitySet<JsName>()

    object : RecursiveJsVisitor() {
        override fun visitBreak(x: JsBreak) { }

        override fun visitContinue(x: JsContinue) { }

        override fun visit(x: JsVars.JsVar) {
            val initializer = x.initExpression
            if (initializer != null) {
                accept(initializer)
            }
        }

        override fun visitNameRef(nameRef: JsNameRef) {
            super.visitNameRef(nameRef)
            val name = nameRef.name
            if (name != null && nameRef.qualifier == null) {
                references.add(name)
            }
        }

        override fun visitFunction(x: JsFunction) {
            references += x.collectFreeVariables()
        }
    }.accept(scope)

    return references
}

fun collectDefinedNames(scope: JsNode): Set<JsName> {
    val names: MutableMap<String, JsName> = HashMap()

    object : RecursiveJsVisitor() {
        override fun visit(x: JsVars.JsVar) {
            val initializer = x.initExpression
            if (initializer != null) {
                accept(initializer)
            }
            addNameIfNeeded(x.name)
        }

        override fun visitExpressionStatement(x: JsExpressionStatement) {
            val expression = x.expression
            if (expression is JsFunction) {
                val name = expression.name
                if (name != null) {
                    addNameIfNeeded(name)
                }
            }
            super.visitExpressionStatement(x)
        }

        // Skip function expression, since it does not introduce name in scope of containing function.
        // The only exception is function statement, that is handled with the code above.
        override fun visitFunction(x: JsFunction) { }

        private fun addNameIfNeeded(name: JsName) {
            val ident = name.ident
            val nameCollected = names[ident]
            assert(nameCollected == null || nameCollected === name) { "ambiguous identifier $name" }
            names[ident] = name
        }
    }.accept(scope)

    return names.values.toSet()
}

fun JsFunction.collectFreeVariables() = collectUsedNames(body) - collectDefinedNames(body) - parameters.map { it.name }

fun JsFunction.collectLocalVariables() = collectDefinedNames(body) + parameters.map { it.name }

fun collectJsProperties(scope: JsNode): IdentityHashMap<JsName, JsExpression> {
    val collector = PropertyCollector()
    collector.accept(scope)
    return collector.properties
}

fun collectNamedFunctions(scope: JsNode) = collectNamedFunctionsAndMetadata(scope).mapValues { it.value.first }

fun collectNamedFunctionsOrMetadata(scope: JsNode) = collectNamedFunctionsAndMetadata(scope).mapValues { it.value.second }

fun collectNamedFunctionsAndMetadata(scope: JsNode): Map<JsName, Pair<JsFunction, JsExpression>> {
    val namedFunctions = mutableMapOf<JsName, Pair<JsFunction, JsExpression>>()

    scope.accept(object : RecursiveJsVisitor() {
        override fun visitBinaryExpression(x: JsBinaryOperation) {
            val assignment = JsAstUtils.decomposeAssignment(x)
            if (assignment != null) {
                val (left, right) = assignment
                if (left is JsNameRef) {
                    val name = left.name
                    val function = extractFunction(right)
                    if (function != null && name != null) {
                        namedFunctions[name] = Pair(function, right)
                    }
                }
            }
            super.visitBinaryExpression(x)
        }

        override fun visit(x: JsVars.JsVar) {
            val initializer = x.initExpression
            val name = x.name
            if (initializer != null && name != null) {
                val function = extractFunction(initializer)
                if (function != null) {
                    namedFunctions[name] = Pair(function, initializer)
                }
            }
            super.visit(x)
        }

        override fun visitFunction(x: JsFunction) {
            val name = x.name
            if (name != null) {
                namedFunctions[name] = Pair(x, x)
            }
            super.visitFunction(x)
        }

        private fun extractFunction(expression: JsExpression) = when (expression) {
            is JsFunction -> expression
            else -> InlineMetadata.decompose(expression)?.function
        }
    })

    return namedFunctions
}

fun <T : JsNode> collectInstances(klass: Class<T>, scope: JsNode): List<T> {
    return with(InstanceCollector(klass, visitNestedDeclarations = false)) {
        accept(scope)
        collected
    }
}
