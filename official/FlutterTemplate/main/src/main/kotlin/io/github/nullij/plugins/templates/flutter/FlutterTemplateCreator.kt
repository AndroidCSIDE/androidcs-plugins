/*
 *  This file is part of FlutterTemplatePlugin.
 *
 *  FlutterTemplatePlugin is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  FlutterTemplatePlugin is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with FlutterTemplatePlugin.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.nullij.plugins.templates.flutter

import android.content.Context
import android.util.Log
import com.nullij.androidcodestudio.plugins.api.PluginApi
import io.github.nullij.plugins.templates.flutter.constants.Constants as CV
import io.github.nullij.plugins.templates.flutter.sources.android.module.moduleSrcs
import io.github.nullij.plugins.templates.flutter.sources.android.root.flutSrcs
import io.github.nullij.plugins.templates.flutter.sources.android.root.rootSrcs
import java.io.File

/**
 * Flutter project template creator.
 *
 * Invoked by the IDE via templates.json: className =
 * "io.github.nullij.plugins.templates.flutter.FlutterTemplateCreator" methodName = "create"
 *
 * All IDE interaction goes through [PluginApi.templates] — no internal TemplateAccessor or other
 * com.nullij.androidcodestudio.internals.* class is referenced here, keeping the plugin inside the
 * RestrictedClassLoader sandbox.
 *
 * @author nullij @ https://github.com/nullij
 */
object FlutterTemplateCreator {

    private const val TAG = "FlutterTemplateCreator"

    private const val ASSETS_BASE = "templates/MyPlugin"
    private const val ASSETS_GRADLE = "$ASSETS_BASE/gradle"
    private const val ASSETS_RESOURCES = "$ASSETS_BASE/resources"

    private const val PLUGIN_ID = "FlutterTemplate"
    private const val PLUGIN_LIBS_DIR = "plugin_libs"

    /*
     * Required for setting up the context during execution
     * @param context
     */
    @JvmStatic fun setContext(context: Context) {}

    // ─── Entry point (called by IDE via templates.json) ───────────────────────

    @JvmStatic
    fun create(context: Context, listener: Any?, options: Any) {
        val templates = PluginApi.templates
        templates.callListenerMethod(listener, "onTemplateCreationStarted")

        try {
            val opts = templates.extractOptions(options)
            val projectRoot = File(opts.saveLocation, "${opts.projectName}/android")
            projectRoot.mkdirs()

            CV.packageName = opts.packageId
            CV.minSdk = opts.minSdk

            /* @step 1-3  Find the pre-extracted plugin assets directory.
             * DexActionLoader extracts the APK into:
             *   cache/plugin_libs/<pluginId>/assets/
             */
            val pluginAssets = findPluginAssets(context) ?: logAndThrow("Plugin assets not found")

            /* @step 4  Standard Android directory tree */
            val structure = templates.createStandardStructure(projectRoot, opts.packageId)

            /* @step 5  Gradle wrapper + launcher resources from bundled assets */
            copyGradleWrapperFromExtracted(pluginAssets, projectRoot)
            copyResourceFilesFromExtracted(pluginAssets, projectRoot)

            /* @step 6  Source files */
            val fileExt = if (opts.languageType == "KOTLIN") "kt" else "java"
            templates.createFile(
                structure.javaDir,
                "MainActivity.$fileExt",
                if (opts.languageType == "KOTLIN") moduleSrcs.mainActivityAsKotlin
                else moduleSrcs.mainActivityAsJava,
            )

            structure.manifestFile.writeText(moduleSrcs.AndroidManifestAsXml)

            val buildExt = if (opts.useKts) ".kts" else ""
            templates.createFile(
                File(projectRoot, "app"),
                "build.gradle$buildExt",
                if (opts.useKts) moduleSrcs.buildGradleSrcAsKts
                else moduleSrcs.buildGradleSrcAsGroovy,
            )

            templates.createFile(
                projectRoot,
                "build.gradle$buildExt",
                if (opts.useKts) rootSrcs.buildGradleSrcAsKts else rootSrcs.buildGradleSrcAsGroovy,
            )

            templates.createFile(
                projectRoot,
                "settings.gradle$buildExt",
                if (opts.useKts) rootSrcs.settingsSrcAsKts else rootSrcs.settingsSrcAsGroovy,
            )

            templates.createFile(projectRoot, "gradle.properties", rootSrcs.gradlePropertiesAsProps)
            templates.createFile(projectRoot, "local.properties", rootSrcs.localPropertiesAsProps)
            templates.createFile(
                File(projectRoot, "app"),
                "CMakeLists.txt",
                moduleSrcs.cmakeListAsTxt,
            )
            templates.createFile(
                File(projectRoot, "app"),
                "proguard-rules.pro",
                moduleSrcs.proguardRulesAsPro,
            )
            templates.createFile(projectRoot, ".gitignore", createGitignore())

            /* @step 7  Flutter root files (parent of android/) */
            val flutterRoot = projectRoot.parentFile!!
            val libDir = File(flutterRoot, "lib").apply { mkdirs() }
            val l10nDir = File(flutterRoot, "lib/l10n").apply { mkdirs() }

            templates.createFile(flutterRoot, "pubspec.yaml", flutSrcs.pubSpecAsYaml)
            templates.createFile(flutterRoot, "l10n.yaml", flutSrcs.l10nAsYaml)
            templates.createFile(libDir, "main.dart", flutSrcs.mainAsDart)
            templates.createFile(l10nDir, "app_en.arb", flutSrcs.appEnAsArb)

            /* @step 8  Notify IDE — prefer the (Boolean, String, File) overload */
            val withFile =
                listener?.javaClass?.methods?.find { m ->
                    m.name == "onTemplateCreated" &&
                        m.parameterCount == 3 &&
                        m.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                        m.parameterTypes[1] == String::class.java &&
                        m.parameterTypes[2] == File::class.java
                }

            if (withFile != null) {
                withFile.invoke(listener, true, "", flutterRoot)
            } else {
                templates.callListenerMethod(
                    listener,
                    "onTemplateCreated",
                    true,
                    "Project created at: ${flutterRoot.absolutePath}",
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating Flutter template", e)
            val templates = PluginApi.templates
            templates.callListenerMethod(listener, "onTemplateCreationFailed", e)
            templates.callListenerMethod(
                listener,
                "onTemplateCreated",
                false,
                "Error: ${e.message}",
            )
        }
    }

    // ─── Asset helpers ────────────────────────────────────────────────────────

    /**
     * Locate the assets directory DexActionLoader extracted for this plugin. Path:
     * cache/plugin_libs/FlutterTemplate/assets/
     */
    private fun findPluginAssets(context: Context): File? {
        val dir = File(context.cacheDir, "$PLUGIN_LIBS_DIR/$PLUGIN_ID/assets")
        if (dir.exists() && dir.list()?.isNotEmpty() == true) {
            Log.d(TAG, "Found plugin assets at: ${dir.absolutePath}")
            return dir
        }
        Log.e(TAG, "Plugin assets not found at: ${dir.absolutePath}")
        return null
    }

    private fun copyGradleWrapperFromExtracted(assetsDir: File, projectRoot: File) {
        try {
            File(assetsDir, "$ASSETS_GRADLE/gradlew")
                .takeIf { it.exists() }
                ?.let {
                    it.copyTo(File(projectRoot, "gradlew"), overwrite = true)
                        .setExecutable(true, false)
                }
            File(assetsDir, "$ASSETS_GRADLE/gradlew.bat")
                .takeIf { it.exists() }
                ?.copyTo(File(projectRoot, "gradlew.bat"), overwrite = true)

            File(assetsDir, "$ASSETS_GRADLE/wrapper")
                .takeIf { it.exists() }
                ?.let { copyDirectory(it, File(projectRoot, "gradle/wrapper")) }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying gradle wrapper", e)
        }
    }

    private fun copyResourceFilesFromExtracted(assetsDir: File, projectRoot: File) {
        try {
            val src = File(assetsDir, ASSETS_RESOURCES)
            if (src.exists()) copyDirectory(src, File(projectRoot, "app/src/main/res"))
            else Log.w(TAG, "Resources directory not found: ${src.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying resources", e)
        }
    }

    private fun copyDirectory(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyDirectory(it, File(dst, it.name)) }
        } else {
            src.copyTo(dst, overwrite = true)
        }
    }

    // ─── Misc ─────────────────────────────────────────────────────────────────

    private fun createGitignore() =
        """
            *.iml
            .gradle
            /local.properties
            /.idea
            .DS_Store
            /build
            /captures
            .externalNativeBuild
            .cxx
            *.apk
            *.ap_
            *.aab
            *.dex
            *.class
            bin/
            gen/
            out/
        """
            .trimIndent()

    private fun logAndThrow(message: String): Nothing {
        Log.e(TAG, message)
        throw IllegalStateException(message)
    }
}
