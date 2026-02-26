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
import com.nullij.androidcodestudio.internals.TemplateAccessor
import io.github.nullij.plugins.templates.flutter.constants.Constants as CV
import io.github.nullij.plugins.templates.flutter.sources.android.module.moduleSrcs
import io.github.nullij.plugins.templates.flutter.sources.android.root.rootSrcs
import io.github.nullij.plugins.templates.flutter.sources.android.root.flutSrcs
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Advanced template creator with assets extraction, gradle wrapper, etc Uses reflection to work as
 * a plugin without importing IDE classes
 * @author nullij @ https://github.com/nullij
 */
object FlutterTemplateCreator {

  private const val TAG = "FlutterTemplateCreator"

  private var appContext: Context? = null
  private var pluginClassLoader: ClassLoader? = null

  // This should match your plugin structure in assets
  // assets/templates/MyPlugin/gradle/...
  // assets/templates/MyPlugin/resources/...
  private const val ASSETS_BASE = "templates/MyPlugin"
  private const val ASSETS_GRADLE = "$ASSETS_BASE/gradle"
  private const val ASSETS_RESOURCES = "$ASSETS_BASE/resources"

  // Plugin id = parent folder name of plugin.apk  →  "FlutterTemplate"
  private const val PLUGIN_ID       = "FlutterTemplate"
  private const val PLUGIN_LIBS_DIR = "plugin_libs"

  /*
  * Required for setting up the context during execution
  * @param context
  */
  @JvmStatic
  fun setContext(context: Context) {
    appContext = context
    pluginClassLoader = this.javaClass.classLoader
  }

  fun getContext(): Context? {
    return appContext
  }

  /*
  * Main function that's going to be called 
    ...
      "pluginConfig": {
        ...
        "methodName": "create"
      }
    ...
  * @param context
  * @param listener
  * @param options
  */
  @JvmStatic
  fun create(context: Context, listener: Any?, options: Any) {
    TemplateAccessor.callListenerMethod(listener, "onTemplateCreationStarted")

    try {
      val opts = TemplateAccessor.extractOptions(options)
      val projectRoot = File(opts.saveLocation, "${opts.projectName}/android")
      projectRoot.mkdirs()

      // Sync Constants with options so sources pick up the right values
      CV.packageName = opts.packageId
      CV.minSdk = opts.minSdk

      /* @step 1 */
      /* Find plugin assets
      * they're extracted by ActionLoader
      */
      val pluginContext = getContext() ?: context
      
      /* @step 2 */
      // Check cache directories for extracted plugin assets
      val cacheDir = context.cacheDir
      
      /* @step 3 */
      // Try to find assets in extracted plugin directory
      val pluginAssets = findPluginAssets(context)
      
      /* @step 4 */
      // Create standard project structure
      val structure = TemplateAccessor.createStandardStructure(projectRoot, opts.packageId)
      
      /* @step 5 */
      /* Copy gradle wrapper files
      * Since this is a flutter template, the gradle wrapper files goes in android/[gradlew, gradlew.bat]
      */
      if (pluginAssets != null && pluginAssets.exists()) {
        copyGradleWrapperFromExtracted(pluginAssets, projectRoot)
        copyResourceFilesFromExtracted(pluginAssets, projectRoot)
      } else {
        logAndThrow("Plugin assets not found")
      }
      
      /* @step 6 */
      // Create MainActivity
      val fileExt = if (opts.languageType == "KOTLIN") "kt" else "java"
      TemplateAccessor.createFile(
          structure.javaDir,
          "MainActivity.$fileExt",
          if (opts.languageType == "KOTLIN") {
            moduleSrcs.mainActivityAsKotlin
          } else {
            moduleSrcs.mainActivityAsJava
          },
      )

      // Create AndroidManifest.xml
      structure.manifestFile.writeText(moduleSrcs.AndroidManifestAsXml)

      // Create build.gradle / build.gradle.kts (app level)
      val buildGradleExt = if (opts.useKts) ".kts" else ""
      TemplateAccessor.createFile(
          File(projectRoot, "app"),
          "build.gradle$buildGradleExt",
          if (opts.useKts) {
            moduleSrcs.buildGradleSrcAsKts
          } else {
            moduleSrcs.buildGradleSrcAsGroovy
          },
      )

      // Create top-level build.gradle / build.gradle.kts
      TemplateAccessor.createFile(
          projectRoot,
          "build.gradle$buildGradleExt",
          if (opts.useKts) rootSrcs.buildGradleSrcAsKts else rootSrcs.buildGradleSrcAsGroovy,
      )

      // Create settings.gradle / settings.gradle.kts
      TemplateAccessor.createFile(
          projectRoot,
          "settings.gradle$buildGradleExt",
          if (opts.useKts) rootSrcs.settingsSrcAsKts else rootSrcs.settingsSrcAsGroovy,
      )

      // Create gradle.properties
      TemplateAccessor.createFile(projectRoot, "gradle.properties", rootSrcs.gradlePropertiesAsProps)

      // Create local.properties
      TemplateAccessor.createFile(projectRoot, "local.properties", rootSrcs.localPropertiesAsProps)

      // Create app/CMakeLists.txt
      TemplateAccessor.createFile(File(projectRoot, "app"), "CMakeLists.txt", moduleSrcs.cmakeListAsTxt)

      // Create app/proguard-rules.pro
      TemplateAccessor.createFile(File(projectRoot, "app"), "proguard-rules.pro", moduleSrcs.proguardRulesAsPro)

      // Create .gitignore
      TemplateAccessor.createFile(projectRoot, ".gitignore", createGitignore())

      // Create flutter root files (outside android/)
      // projectRoot is android/, flutter root is its parent
      val flutterRoot = projectRoot.parentFile
      val libDir = File(flutterRoot, "lib").apply { mkdirs() }
      val l10nDir = File(flutterRoot, "lib/l10n").apply { mkdirs() }
      TemplateAccessor.createFile(flutterRoot, "pubspec.yaml", flutSrcs.pubSpecAsYaml)
      TemplateAccessor.createFile(flutterRoot, "l10n.yaml", flutSrcs.l10nAsYaml)
      TemplateAccessor.createFile(libDir, "main.dart", flutSrcs.mainAsDart)
      TemplateAccessor.createFile(l10nDir, "app_en.arb", flutSrcs.appEnAsArb)

      // Try to find and call onTemplateCreated method
      if (listener != null) {
        try {
          // Look for onTemplateCreated(boolean, String, File) method
          val methods = listener.javaClass.methods
          val methodWithFile =
              methods.find { method ->
                method.name == "onTemplateCreated" &&
                    method.parameterCount == 3 &&
                    method.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                    method.parameterTypes[1] == String::class.java &&
                    method.parameterTypes[2] == File::class.java
              }

          if (methodWithFile != null) {
            methodWithFile.invoke(listener, true, "", flutterRoot)
          } else {
            Log.w(TAG, "Method onTemplateCreated(boolean, String, File) not found")
            TemplateAccessor.callListenerMethod(
                listener,
                "onTemplateCreated",
                true,
                "Project created at: ${flutterRoot.absolutePath}",
            )
          }
        } catch (e: Exception) {
          logAndThrow("Error calling listener ${e}")
        }
      }
    } catch (e: Exception) {
      logAndThrow("Error creating template ${e}")
      TemplateAccessor.callListenerMethod(listener, "onTemplateCreationFailed", e)
      TemplateAccessor.callListenerMethod(
          listener,
          "onTemplateCreated",
          false,
          "Error: ${e.message}",
      )
    }
  }

  /**
   * Get plugin's own Context that can access plugin assets This is important for loading assets from
   * the plugin
   */
  private fun getPluginContext(): Context? {
    val ctx = appContext ?: return null
    return try {
      val createPackageContext =
          Context::class
              .java
              .getMethod("createPackageContext", String::class.java, Int::class.javaPrimitiveType)
      val packageName =
          pluginClassLoader?.let { cl ->
            this.javaClass.name
                .substringBeforeLast('.')
                .substringBeforeLast('.') // Get base package
          } ?: return ctx
      ctx
    } catch (e: Exception) {
      Log.e(TAG, "Error creating plugin context, using app context", e)
      ctx
    }
  }

  /**
   * Find the extracted plugin assets directory.
   * DexActionLoader extracts APK assets to:
   *   cache/plugin_libs/<pluginId>/assets/
   * so we look there directly instead of scanning or re-extracting.
   */
  private fun findPluginAssets(context: Context): File? {
    val stableDir = File(context.cacheDir, "$PLUGIN_LIBS_DIR/$PLUGIN_ID/assets")
    if (stableDir.exists() && stableDir.list()?.isNotEmpty() == true) {
      Log.d(TAG, "Found plugin assets at: ${stableDir.absolutePath}")
      return stableDir
    }
    Log.e(TAG, "Plugin assets not found at: ${stableDir.absolutePath}")
    return null
  }

  /** Copy gradle wrapper from extracted assets */
  private fun copyGradleWrapperFromExtracted(assetsDir: File, projectRoot: File) {
    try {
      val gradlewSrc = File(assetsDir, "$ASSETS_GRADLE/gradlew")
      val gradlewDst = File(projectRoot, "gradlew")

      if (gradlewSrc.exists()) {
        gradlewSrc.copyTo(gradlewDst, overwrite = true)
        gradlewDst.setExecutable(true, false)
      }

      val gradlewBatSrc = File(assetsDir, "$ASSETS_GRADLE/gradlew.bat")
      val gradlewBatDst = File(projectRoot, "gradlew.bat")

      if (gradlewBatSrc.exists()) {
        gradlewBatSrc.copyTo(gradlewBatDst, overwrite = true)
      }

      val wrapperSrc = File(assetsDir, "$ASSETS_GRADLE/wrapper")
      val wrapperDst = File(projectRoot, "gradle/wrapper")

      if (wrapperSrc.exists()) {
        copyDirectory(wrapperSrc, wrapperDst)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error copying gradle wrapper from extracted assets", e)
    }
  }

  /** Copy resource files from extracted assets */
  private fun copyResourceFilesFromExtracted(assetsDir: File, projectRoot: File) {
    try {
      val resourcesSrc = File(assetsDir, ASSETS_RESOURCES)
      val resourcesDst = File(projectRoot, "app/src/main/res")

      if (resourcesSrc.exists()) {
        copyDirectory(resourcesSrc, resourcesDst)
      } else {
        Log.w(TAG, "Resources directory not found: ${resourcesSrc.absolutePath}")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error copying resources from extracted assets", e)
    }
  }

  /** Copy directory recursively */
  private fun copyDirectory(src: File, dst: File) {
    if (src.isDirectory) {
      dst.mkdirs()
      src.listFiles()?.forEach { file -> copyDirectory(file, File(dst, file.name)) }
    } else {
      src.copyTo(dst, overwrite = true)
    }
  }

  /** Copy gradle wrapper files from assets to project root */
  private fun copyGradleWrapper(context: Context, projectRoot: File) {
    try {
      // Debug: List available assets
      listAssets(context, "templates")

      // Copy gradlew
      copyAssetFile(context, "$ASSETS_GRADLE/gradlew", File(projectRoot, "gradlew"))
      File(projectRoot, "gradlew").setExecutable(true, false)

      // Copy gradlew.bat
      copyAssetFile(context, "$ASSETS_GRADLE/gradlew.bat", File(projectRoot, "gradlew.bat"))

      // Copy gradle/wrapper folder
      val wrapperDestDir = File(projectRoot, "gradle/wrapper")
      copyAssetFolder(context, "$ASSETS_GRADLE/wrapper", wrapperDestDir)
    } catch (e: Exception) {
      Log.e(TAG, "Error copying gradle wrapper", e)
      // Don't fail - wrapper is optional
    }
  }

  /** Copy resource files from assets to res folder */
  private fun copyResourceFiles(context: Context, projectRoot: File) {
    try {
      val resDestDir = File(projectRoot, "app/src/main/res")
      copyAssetFolder(context, ASSETS_RESOURCES, resDestDir)
    } catch (e: Exception) {
      Log.e(TAG, "Error copying resource files", e)
      // Don't fail - resources are optional
    }
  }

  /** Recursively copy folder from assets */
  private fun copyAssetFolder(context: Context, assetPath: String, destDir: File) {
    try {
      val files = context.assets.list(assetPath) ?: emptyArray()

      if (files.isEmpty()) {
        // It's a file
        copyAssetFile(context, assetPath, destDir)
      } else {
        // It's a directory
        destDir.mkdirs()
        for (fileName in files) {
          val subAssetPath = "$assetPath/$fileName"
          val subDestFile = File(destDir, fileName)
          copyAssetFolder(context, subAssetPath, subDestFile)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error copying asset folder: $assetPath", e)
    }
  }

  /** Copy single file from assets */
  private fun copyAssetFile(context: Context, assetPath: String, destFile: File) {
    try {
      destFile.parentFile?.mkdirs()
      val inputStream: InputStream = context.assets.open(assetPath)
      val outputStream = FileOutputStream(destFile)

      inputStream.use { input -> outputStream.use { output -> input.copyTo(output) } }
    } catch (e: Exception) {
      Log.e(TAG, "Error copying file: $assetPath", e)
      throw e
    }
  }

  /** Debug method to list available assets */
  private fun listAssets(context: Context, path: String) {
    try {
      val assets = context.assets.list(path)
      assets?.forEach { asset ->
        val subPath = "$path/$asset"
        val subAssets = context.assets.list(subPath)
        if (subAssets != null && subAssets.isNotEmpty()) {
          Log.d(TAG, "  $asset/ -> ${subAssets.joinToString()}")
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error listing assets in '$path'", e)
    }
  }

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

  /*
  * Function to log and throw exception
  * @param message 
  */
  fun logAndThrow(message: String) {
      Log.d(TAG, message)
      throw IllegalArgumentException(message)
  }
}