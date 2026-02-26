/*
 *  This file is part of AndroidCodeStudio Accessors.
 *
 *  AndroidCodeStudio Accessors is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio Accessors is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio Accessors.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.nullij.androidcodestudio.internals

import android.content.Context
import java.io.File

/**
 * Accessor for creating and registering templates, Makes it easy for
 * developers to create custom templates without importing IDE classes
 * @author nullij @ https://github.com/nullij
 */
object TemplateAccessor {

  private const val TEMPLATE_CLASS =
      "com.nullij.androidcodestudio.template.creator.android.Template"
  private const val TEMPLATE_OPTIONS_CLASS =
      "com.nullij.androidcodestudio.template.creator.android.TemplateOptions"
  private const val LISTENER_CLASS =
      "com.nullij.androidcodestudio.template.creator.AtcInterface\$TemplateCreationListener"
  private const val LANGUAGE_TYPE_CLASS =
      "com.nullij.androidcodestudio.project.manager.builder.LanguageType"

  /** Create a new template */
  fun createTemplate(
      displayName: String,
      templateType: String = "ACTIVITY",
      configureOptions: (() -> Unit)? = null,
      onCreate: (suspend (Context, Any?, Any) -> Unit),
  ): Any {
    return TemplateProxy(displayName, templateType, configureOptions, onCreate)
  }

  /** Proxy class that implements Template interface */
  private class TemplateProxy(
      private val displayName: String,
      private val templateType: String,
      private val configureOptions: (() -> Unit)?,
      private val onCreate: suspend (Context, Any?, Any) -> Unit,
  ) : java.lang.reflect.InvocationHandler {

    override fun invoke(
        proxy: Any?,
        method: java.lang.reflect.Method,
        args: Array<out Any>?,
    ): Any? {
      return when (method.name) {
        "getDisplayName" -> displayName
        "getTemplateType" -> {
          val templateTypeClass =
              Class.forName(
                  "com.nullij.androidcodestudio.template.creator.android.Template\$TemplateType"
              )
          templateTypeClass.enumConstants.find { it.toString() == templateType }
        }
        "configureOptions" -> {
          configureOptions?.invoke()
        }
        "create" -> {
          kotlinx.coroutines.runBlocking {
            val context = args?.get(0) as Context
            val listener = args.getOrNull(1)
            val options = args[2]
            onCreate(context, listener, options)
          }
        }
        else -> null
      }
    }
  }

  /** Helper to create template instance using reflection */
  fun createTemplateInstance(
      displayName: String,
      templateType: String = "ACTIVITY",
      configureOptions: (() -> Unit)? = null,
      onCreate: suspend (Context, Any?, Any) -> Unit,
  ): Any {
    val templateClass = Class.forName(TEMPLATE_CLASS)
    val proxy = TemplateProxy(displayName, templateType, configureOptions, onCreate)

    return java.lang.reflect.Proxy.newProxyInstance(
        templateClass.classLoader,
        arrayOf(templateClass),
        proxy,
    )
  }

  /** Helper to create directories */
  fun createDirectories(baseDir: File, vararg paths: String): File {
    val targetDir = File(baseDir, paths.joinToString(File.separator))
    targetDir.mkdirs()
    return targetDir
  }

  /** Helper to create a file with content */
  fun createFile(dir: File, fileName: String, content: String): File {
    val file = File(dir, fileName)
    file.writeText(content)
    return file
  }

  /** Helper to get package path from package ID */
  fun getPackagePath(packageId: String): String {
    return packageId.replace('.', '/')
  }

  /** Helper to create standard Android project structure */
  fun createStandardStructure(projectDir: File, packageId: String): ProjectStructure {
    val packagePath = getPackagePath(packageId)

    val mainSrcDir = createDirectories(projectDir, "app", "src", "main")
    val javaDir = createDirectories(mainSrcDir, "java", packagePath)
    val resDir = createDirectories(mainSrcDir, "res")
    val layoutDir = createDirectories(resDir, "layout")
    val valuesDir = createDirectories(resDir, "values")
    val drawableDir = createDirectories(resDir, "drawable")
    val manifestFile = File(mainSrcDir, "AndroidManifest.xml")

    return ProjectStructure(
        projectDir = projectDir,
        mainSrcDir = mainSrcDir,
        javaDir = javaDir,
        resDir = resDir,
        layoutDir = layoutDir,
        valuesDir = valuesDir,
        drawableDir = drawableDir,
        manifestFile = manifestFile,
        packageId = packageId,
        packagePath = packagePath,
    )
  }

  /** Get field from TemplateOptions using reflection */
  fun getOptionsField(options: Any, fieldName: String): Any? {
    return try {
      val field = options.javaClass.getDeclaredField(fieldName)
      field.isAccessible = true
      field.get(options)
    } catch (e: Exception) {
      null
    }
  }

  /** Call listener method using reflection */
  fun callListenerMethod(listener: Any?, methodName: String, vararg args: Any?) {
    if (listener == null) return

    try {
      val method = listener.javaClass.methods.find { it.name == methodName }
      method?.invoke(listener, *args)
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  /** Extract common options from TemplateOptions object */
  fun extractOptions(options: Any): TemplateOptionsData {
    return TemplateOptionsData(
        projectName = getOptionsField(options, "projectName") as? String ?: "",
        packageId = getOptionsField(options, "packageId") as? String ?: "",
        minSdk = getOptionsField(options, "minSdk") as? Int ?: 21,
        useKts = getOptionsField(options, "useKts") as? Boolean ?: false,
        saveLocation = getOptionsField(options, "saveLocation") as? File ?: File("."),
        languageType = getOptionsField(options, "languageType")?.toString() ?: "KOTLIN",
    )
  }

  /** Data class representing project structure */
  data class ProjectStructure(
      val projectDir: File,
      val mainSrcDir: File,
      val javaDir: File,
      val resDir: File,
      val layoutDir: File,
      val valuesDir: File,
      val drawableDir: File,
      val manifestFile: File,
      val packageId: String,
      val packagePath: String,
  )

  /** Data class for extracted template options */
  data class TemplateOptionsData(
      val projectName: String,
      val packageId: String,
      val minSdk: Int,
      val useKts: Boolean,
      val saveLocation: File,
      val languageType: String,
  )
}
