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

package org.jetbrains.kotlin.cli.jvm

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettings
import java.util.*
import java.util.jar.Manifest


internal inline fun Properties.getString(propertyName: String, otherwise: () -> String): String =
        getProperty(propertyName) ?: otherwise()

object JvmRuntimeVersionsConsistencyChecker {
    private val LOG = Logger.getInstance(JvmRuntimeVersionsConsistencyChecker::class.java)

    private fun fatal(message: String): Nothing {
        LOG.error(message)
        throw AssertionError(message)
    }

    private fun <T> T?.assertNotNull(message: () -> String): T =
            if (this == null) fatal(message()) else this

    // TODO replace with ERROR after bootstrapping
    private val VERSION_ISSUE_SEVERITY = CompilerMessageSeverity.WARNING

    private const val META_INF = "META-INF"
    private const val MANIFEST_MF = "$META_INF/MANIFEST.MF"

    private const val MANIFEST_KOTLIN_VERSION_ATTRIBUTE = "manifest.impl.attribute.kotlin.version"
    private const val MANIFEST_KOTLIN_VERSION_VALUE = "manifest.impl.value.kotlin.version"
    private const val MANIFEST_KOTLIN_RUNTIME_COMPONENT = "manifest.impl.attribute.kotlin.runtime.component"
    private const val MANIFEST_KOTLIN_RUNTIME_COMPONENT_CORE = "manifest.impl.value.kotlin.runtime.component.core"

    private const val KOTLIN_STDLIB_MODULE = "$META_INF/kotlin-stdlib.kotlin_module"
    private const val KOTLIN_REFLECT_MODULE = "$META_INF/kotlin-reflection.kotlin_module"

    private val KOTLIN_VERSION_ATTRIBUTE: String
    private val CURRENT_COMPILER_VERSION: LanguageVersion

    private val KOTLIN_RUNTIME_COMPONENT_ATTRIBUTE: String
    private val KOTLIN_RUNTUME_COMPONENT_CORE: String

    init {
        val manifestProperties: Properties = try {
            JvmRuntimeVersionsConsistencyChecker::class.java
                    .getResourceAsStream("/kotlinManifest.properties")
                    .let { input -> Properties().apply { load(input) } }
        }
        catch (e: Exception) {
            LOG.error(e)
            throw e
        }

        KOTLIN_VERSION_ATTRIBUTE = manifestProperties.getProperty(MANIFEST_KOTLIN_VERSION_ATTRIBUTE)
                .assertNotNull { "$MANIFEST_KOTLIN_VERSION_ATTRIBUTE not found in kotlinManifest.properties" }

        CURRENT_COMPILER_VERSION = run {
            val kotlinVersionString = manifestProperties.getProperty(MANIFEST_KOTLIN_VERSION_VALUE)
                    .assertNotNull { "$MANIFEST_KOTLIN_VERSION_VALUE not found in kotlinManifest.properties" }

            LanguageVersion.fromFullVersionString(kotlinVersionString)
                    .assertNotNull { "Incorrect Kotlin version: $kotlinVersionString" }
        }

        if (CURRENT_COMPILER_VERSION != LanguageVersion.LATEST) {
            fatal("Kotlin compiler version $CURRENT_COMPILER_VERSION in kotlinManifest.properties doesn't match ${LanguageVersion.LATEST}")
        }

        KOTLIN_RUNTIME_COMPONENT_ATTRIBUTE = manifestProperties.getProperty(MANIFEST_KOTLIN_RUNTIME_COMPONENT)
                .assertNotNull { "$MANIFEST_KOTLIN_RUNTIME_COMPONENT not found in kotlinManifest.properties" }
        KOTLIN_RUNTUME_COMPONENT_CORE = manifestProperties.getProperty(MANIFEST_KOTLIN_RUNTIME_COMPONENT_CORE)
                .assertNotNull { "$MANIFEST_KOTLIN_RUNTIME_COMPONENT_CORE not found in kotlinManifest.properties" }
    }

    class FileWithLanguageVersion(val component: String, val file: VirtualFile, val version: LanguageVersion) {
        override fun toString(): String =
                "${file.name}:$version ($component)"
    }

    class RuntimeJarsInfo(
            val coreJars: List<FileWithLanguageVersion>
    ) {
        val hasAnyJarsToCheck: Boolean get() = coreJars.isNotEmpty()
    }

    fun checkCompilerClasspathConsistency(
            messageCollector: MessageCollector,
            languageVersionSettings: LanguageVersionSettings?,
            classpathJars: List<VirtualFile>
    ) {
        val runtimeJarsInfo = collectRuntimeJarsInfo(classpathJars)
        if (!runtimeJarsInfo.hasAnyJarsToCheck) return

        val languageVersion = languageVersionSettings?.languageVersion ?: CURRENT_COMPILER_VERSION

        // Even if language version option was explicitly specified, the JAR files SHOULD NOT be newer than the compiler.
        runtimeJarsInfo.coreJars.forEach {
            checkNotNewerThanCompiler(messageCollector, it)
        }

        runtimeJarsInfo.coreJars.forEach {
            checkCompatibleWithLanguageVersion(messageCollector, it, languageVersion)
        }

        checkMatchingVersions(messageCollector, runtimeJarsInfo)
    }

    private fun checkNotNewerThanCompiler(messageCollector: MessageCollector, jar: FileWithLanguageVersion) {
        if (jar.version > CURRENT_COMPILER_VERSION) {
            messageCollector.issue("Run-time JAR file $jar is newer than compiler version $CURRENT_COMPILER_VERSION")
        }
    }

    private fun checkCompatibleWithLanguageVersion(messageCollector: MessageCollector, jar: FileWithLanguageVersion, languageVersion: LanguageVersion) {
        if (jar.version < languageVersion) {
            messageCollector.issue("Run-time JAR file $jar is older than required for language version $languageVersion")
        }
    }

    private fun checkMatchingVersions(messageCollector: MessageCollector, runtimeJarsInfo: RuntimeJarsInfo) {
        val oldestCoreJar = runtimeJarsInfo.coreJars.minBy { it.version } ?: return
        val newestCoreJar = runtimeJarsInfo.coreJars.maxBy { it.version } ?: return

        if (oldestCoreJar.version != newestCoreJar.version) {
            messageCollector.issue("Run-time JAR file $oldestCoreJar is not compatible with JAR file $newestCoreJar")
        }
    }

    private fun MessageCollector.issue(message: String) {
        report(VERSION_ISSUE_SEVERITY, message, CompilerMessageLocation.NO_LOCATION)
    }

    private fun collectRuntimeJarsInfo(classpathJars: List<VirtualFile>): RuntimeJarsInfo {
        val kotlinCoreJars = ArrayList<FileWithLanguageVersion>(2)

        for (jar in classpathJars) {
            val manifest = try {
                val manifestFile = jar.findFileByRelativePath(MANIFEST_MF) ?: continue
                Manifest(manifestFile.inputStream)
            }
            catch (e: Exception) {
                continue
            }

            val runtimeComponent = getKotlinRuntimeComponent(jar, manifest) ?: continue
            val version = manifest.getKotlinLanguageVersion()

            if (runtimeComponent == KOTLIN_RUNTUME_COMPONENT_CORE) {
                kotlinCoreJars.add(FileWithLanguageVersion(runtimeComponent, jar, version))
            }
        }

        return RuntimeJarsInfo(kotlinCoreJars)
    }

    private fun getKotlinRuntimeComponent(jar: VirtualFile, manifest: Manifest): String? {
        manifest.mainAttributes.getValue(KOTLIN_RUNTIME_COMPONENT_ATTRIBUTE)?.let { return it }

        if (jar.findFileByRelativePath(KOTLIN_STDLIB_MODULE) != null) return KOTLIN_RUNTUME_COMPONENT_CORE
        if (jar.findFileByRelativePath(KOTLIN_REFLECT_MODULE) != null) return KOTLIN_RUNTUME_COMPONENT_CORE

        return null
    }

    private fun Manifest.getKotlinLanguageVersion(): LanguageVersion =
            mainAttributes.getValue(KOTLIN_VERSION_ATTRIBUTE)?.let {
                LanguageVersion.fromFullVersionString(it)
            }
            ?: LanguageVersion.KOTLIN_1_0

}