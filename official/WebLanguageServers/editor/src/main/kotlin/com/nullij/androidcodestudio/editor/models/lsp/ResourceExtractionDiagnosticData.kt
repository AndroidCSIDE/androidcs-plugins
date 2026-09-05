/*
 *  This file is part of ACSIDE.
 *
 *  ACSIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ACSIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ACSIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Data class ResourceExtractionDiagnosticData.
 *
 * @author nullij @ https://github.com/nullij
 */
data class ResourceExtractionDiagnosticData(
    val stringValue: String,
    val contextName: String,
    val attributeFullName: String = "",
    val quoteChar: Char = '"',
    val sourceKind: SourceKind = SourceKind.XML_ATTRIBUTE,
    val formatArgs: List<FormatArg> = emptyList(),
    val isComposableScope: Boolean = false,
) {

  /** Enum class SourceKind. */
  enum class SourceKind {

    /** Class XML_ATTRIBUTE. */
    XML_ATTRIBUTE,

    /** Class JAVA_KOTLIN_LITERAL. */
    JAVA_KOTLIN_LITERAL,
  }

  /** Data class FormatArg. */
  data class FormatArg(
      val expression: String,
      val placeholder: String,
      val index: Int,
      val type: FormatType = FormatType.STRING,
  )

  /** Enum class FormatType. */
  enum class FormatType(val specifier: String, val label: String) {

    /** Class STRING. */
    STRING("s", "String (%s)"),

    /** Class INTEGER. */
    INTEGER("d", "Integer (%d)"),

    /** Class FLOAT. */
    FLOAT("f", "Float (%f)"),

    /** Class CHARACTER. */
    CHARACTER("c", "Char (%c)"),
  }

  /** Companion object Companion. */
  companion object {

    /** Represents the diagnostic code. */
    const val DIAGNOSTIC_CODE = "hardcoded-string"

    /** Represents the diagnostic source. */
    const val DIAGNOSTIC_SOURCE = "android-resources"
  }
}
