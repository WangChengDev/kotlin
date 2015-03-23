/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.debugger

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.ReferenceType
import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptor
import org.jetbrains.kotlin.idea.util.application.runReadAction
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getElementTextWithContext
import org.jetbrains.kotlin.psi.psiUtil.isObjectLiteral
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.inline.InlineUtil

fun JetClassOrObject.getNameForNonAnonymousClass(addTraitImplSuffix: Boolean = true): String? {
    if (isLocal()) return null
    if (this.isObjectLiteral()) return null

    val name = getName() ?: return null

    val parentClass = PsiTreeUtil.getParentOfType(this, javaClass<JetClassOrObject>(), true)
    if (parentClass != null) {
        val shouldAddTraitImplSuffix = !(parentClass is JetClass && this is JetObjectDeclaration && this.isCompanion())
        val parentName = parentClass.getNameForNonAnonymousClass(shouldAddTraitImplSuffix)
        if (parentName == null) {
            return null
        }
        return parentName + "$" + name
    }

    val className = if (addTraitImplSuffix && this is JetClass && this.isInterface()) name + JvmAbi.TRAIT_IMPL_SUFFIX else name

    val packageFqName = this.getContainingJetFile().getPackageFqName()
    return if (packageFqName.isRoot()) className else packageFqName.asString() + "." + className
}

private fun PsiElement.getElementToCalculateClassName(): JetElement? {
    val result = PsiTreeUtil.getParentOfType(this,
                                javaClass<JetFile>(),
                                javaClass<JetClassOrObject>(),
                                javaClass<JetFunctionLiteral>(),
                                javaClass<JetClassInitializer>(),
                                javaClass<JetNamedFunction>(),
                                javaClass<JetPropertyAccessor>(),
                                javaClass<JetProperty>())
    return result
}

private fun PsiElement.getClassOfFileClassName(): JetElement? {
    return PsiTreeUtil.getParentOfType(this, javaClass<JetFile>(), javaClass<JetClassOrObject>())
}

fun DebugProcess.getAllClasses(position: SourcePosition): List<ReferenceType> {

    var depthToLocalOrAnonymousClass = 0

    fun calc(element: JetElement?, previousChild: JetElement): String? {
        when {
            element == null -> return null
            element is JetClassOrObject -> {
                if (element.isLocal()) {
                    depthToLocalOrAnonymousClass++
                    return calc(element.getElementToCalculateClassName(), element)
                }

                return element.getNameForNonAnonymousClass()
            }
            element is JetFunctionLiteral -> {
                if (!isInlinedLambda(element)) {
                    depthToLocalOrAnonymousClass++
                }
                return calc(element.getElementToCalculateClassName(), element)
            }
            element is JetClassInitializer -> {
                val parent = element.getParent().getElementToCalculateClassName()

                if (parent is JetObjectDeclaration && parent.isCompanion()) {
                    // Companion-object initializer
                    return calc(parent.getElementToCalculateClassName(), parent)
                }

                return calc(parent, element)
            }
            element is JetPropertyAccessor -> {
                return calc(element.getClassOfFileClassName(), element)
            }
            element is JetProperty -> {
                if (element.isTopLevel() || element.isLocal()) {
                    return calc(element.getElementToCalculateClassName(), element)
                }

                val containingClass = element.getClassOfFileClassName()
                if (containingClass is JetObjectDeclaration && containingClass.isCompanion()) {
                    // Properties from companion object are moved into class
                    val descriptor = element.resolveToDescriptor() as PropertyDescriptor
                    if (AsmUtil.isPropertyWithBackingFieldInOuterClass(descriptor)) {
                        return calc(containingClass.getElementToCalculateClassName(), containingClass)
                    }
                }

                return calc(containingClass, element)
            }
            element is JetNamedFunction -> {
                if (element.isLocal()) {
                    depthToLocalOrAnonymousClass++
                }
                val parent = element.getElementToCalculateClassName()
                if (parent is JetClassInitializer) {
                    // TODO BUG? anonymous functions from companion object constructor should be inner class of companion object, not class
                    return calc(parent.getElementToCalculateClassName(), parent)
                }

                return calc(parent, element)
            }
            element is JetFile -> {
                val isInLibrary = LibraryUtil.findLibraryEntry(element.getVirtualFile(), element.getProject()) != null
                if (isInLibrary) {
                    val elementAtForLibraryFile = getElementToCreateTypeMapperForLibraryFile(previousChild)
                    assert(elementAtForLibraryFile != null) {
                        "Couldn't find element at breakpoint for library file " + element.getContainingJetFile().getName() +
                        ", element = " + element.getElementTextWithContext()
                    }
                    return findPackagePartInternalNameForLibraryFile(elementAtForLibraryFile)
                }
                return PackagePartClassUtils.getPackagePartInternalName(element)
            }
            else -> throw IllegalStateException("Unsupported container ${element.javaClass}")
        }
    }

    val className = runReadAction {
        val elementToCalcClassName = position.getElementAt().getElementToCalculateClassName()
        calc(elementToCalcClassName, PsiTreeUtil.getParentOfType(position.getElementAt(), javaClass<JetElement>()))
    }

    if (className == null) {
        return emptyList()
    }

    if (depthToLocalOrAnonymousClass == 0) {
        return getVirtualMachineProxy().classesByName(className)
    }

    // the name is a parent class for a local or anonymous class
    val outers = getVirtualMachineProxy().classesByName(className)
    return outers.map { findNested(it, 0, depthToLocalOrAnonymousClass, position) }.filterNotNull()
}

private fun getElementToCreateTypeMapperForLibraryFile(element: PsiElement?) =
        if (element is JetDeclaration) element else PsiTreeUtil.getParentOfType(element, javaClass<JetDeclaration>())

private fun DebugProcess.findNested(
        fromClass: ReferenceType,
        currentDepth: Int,
        requiredDepth: Int,
        position: SourcePosition
): ReferenceType? {
    val vmProxy = getVirtualMachineProxy()
    if (fromClass.isPrepared()) {
        try {
            if (currentDepth < requiredDepth) {
                val nestedTypes = vmProxy.nestedTypes(fromClass)
                for (nested in nestedTypes) {
                    val found = findNested(nested, currentDepth + 1, requiredDepth, position)
                    if (found != null) {
                        return found
                    }
                }
                return null
            }

            for (location in fromClass.allLineLocations()) {
                val locationLine = location.lineNumber() - 1
                if (locationLine <= 0) {
                    // such locations are not correspond to real lines in code
                    continue
                }
                val method = location.method()
                if (method == null || DebuggerUtils.isSynthetic(method) || method.isBridge()) {
                    // skip synthetic methods
                    continue
                }

                val positionLine = position.getLine()
                if (positionLine == locationLine) {
                    if (position.getElementAt() == null) return fromClass

                    val candidatePosition = JetPositionManager(this).getSourcePosition(location)
                    if (candidatePosition?.getElementAt() == position.getElementAt()) {
                        return fromClass
                    }
                }
            }
        }
        catch (ignored: AbsentInformationException) {
        }

    }
    return null
}

public fun isInlinedLambda(functionLiteral: JetFunctionLiteral): Boolean {
    val functionLiteralExpression = functionLiteral.getParent() ?: return false

    var parent = functionLiteralExpression.getParent()

    var valueArgument: PsiElement = functionLiteralExpression
    while (parent is JetParenthesizedExpression || parent is JetBinaryExpressionWithTypeRHS || parent is JetLabeledExpression) {
        valueArgument = parent
        parent = parent.getParent()
    }

    while (parent is ValueArgument || parent is JetValueArgumentList) {
        parent = parent.getParent()
    }

    if (parent !is JetElement) return false

    return InlineUtil.isInlinedArgument(functionLiteral, functionLiteral.analyze(), false)
}