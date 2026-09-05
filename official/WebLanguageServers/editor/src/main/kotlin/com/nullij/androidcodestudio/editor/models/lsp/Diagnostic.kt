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
 * Data class Diagnostic.
 *
 * @author nullij @ https://github.com/nullij
 */
data class Diagnostic(
    val range: Range,
    val severity: DiagnosticSeverity,
    val code: String? = null,
    val source: String? = null,
    val message: String,
    val fixAvailable: Boolean? = null,
    val tags: List<DiagnosticTag> = emptyList(),
    val relatedInformation: List<DiagnosticRelatedInformation> = emptyList(),
    val data: Any? = null,
) {

  /**
   * Indicates whether error
   *
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isError(): Boolean = severity == DiagnosticSeverity.ERROR

  /**
   * Indicates whether warning
   *
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isWarning(): Boolean = severity == DiagnosticSeverity.WARNING

  /**
   * Indicates whether deprecated
   *
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isDeprecated(): Boolean = tags.contains(DiagnosticTag.DEPRECATED)

  /**
   * Indicates whether unnecessary
   *
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isUnnecessary(): Boolean = tags.contains(DiagnosticTag.UNNECESSARY)

  /**
   * Indicates whether resource extraction inspection
   *
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isResourceExtractionInspection(): Boolean =
      severity == DiagnosticSeverity.INSPECTION ||
          code == ResourceExtractionDiagnosticData.DIAGNOSTIC_CODE

  /**
   * Formats and displays the string
   *
   * @return the string
   */
  fun toDisplayString(): String {
    return buildString {
      append(severity.getIcon())
      append(" ")
      if (source != null) {
        append("[$source] ")
      }
      append(message)
      if (code != null) {
        append(" (")
        append(code)
        append(")")
      }
    }
  }

  /** Companion object Companion. */
  companion object {

    /**
     * Represents the error
     *
     * @param range the range as a [Range]
     * @param message the message text
     * @param code the code text, or `null` if omitted
     * @param source the source text, or `null` if omitted
     * @return the diagnostic
     */
    fun error(
        range: Range,
        message: String,
        code: String? = null,
        source: String? = null,
    ): Diagnostic {
      return Diagnostic(
          range = range,
          severity = DiagnosticSeverity.ERROR,
          message = message,
          code = code,
          source = source,
      )
    }

    /**
     * Performs the operation
     *
     * @param range the range as a [Range]
     * @param message the message text
     * @param code the code text, or `null` if omitted
     * @param source the source text, or `null` if omitted
     * @return the diagnostic
     */
    fun warning(
        range: Range,
        message: String,
        code: String? = null,
        source: String? = null,
    ): Diagnostic {
      return Diagnostic(
          range = range,
          severity = DiagnosticSeverity.WARNING,
          message = message,
          code = code,
          source = source,
      )
    }

    /**
     * Represents the info
     *
     * @param range the range as a [Range]
     * @param message the message text
     * @param code the code text, or `null` if omitted
     * @param source the source text, or `null` if omitted
     * @return the diagnostic
     */
    fun info(
        range: Range,
        message: String,
        code: String? = null,
        source: String? = null,
    ): Diagnostic {
      return Diagnostic(
          range = range,
          severity = DiagnosticSeverity.INFORMATION,
          message = message,
          code = code,
          source = source,
      )
    }

    /**
     * Performs the operation
     *
     * @param range the range as a [Range]
     * @param message the message text
     * @param code the code text, or `null` if omitted
     * @param source the source text, or `null` if omitted
     * @return the diagnostic
     */
    fun hint(
        range: Range,
        message: String,
        code: String? = null,
        source: String? = null,
    ): Diagnostic {
      return Diagnostic(
          range = range,
          severity = DiagnosticSeverity.HINT,
          message = message,
          code = code,
          source = source,
      )
    }

    /**
     * Represents the inspection
     *
     * @param range the range as a [Range]
     * @param message the message text
     * @param data the [ResourceExtractionDiagnosticData] instance
     * @param code the code text
     * @param source the source text
     * @return the diagnostic
     */
    fun inspection(
        range: Range,
        message: String,
        data: ResourceExtractionDiagnosticData,
        code: String = ResourceExtractionDiagnosticData.DIAGNOSTIC_CODE,
        source: String = ResourceExtractionDiagnosticData.DIAGNOSTIC_SOURCE,
    ): Diagnostic {
      return Diagnostic(
          range = range,
          severity = DiagnosticSeverity.INSPECTION,
          message = message,
          code = code,
          source = source,
          fixAvailable = true,
          data = data,
      )
    }
  }
}

/** Enum class DiagnosticTag. */
enum class DiagnosticTag(val value: Int) {

  /** Class UNNECESSARY. */
  UNNECESSARY(1),

  /** Class DEPRECATED. */
  DEPRECATED(2);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the diagnostic tag
     */
    fun fromValue(value: Int): DiagnosticTag? {
      return values().find { it.value == value }
    }
  }
}

/** Data class DiagnosticRelatedInformation. */
data class DiagnosticRelatedInformation(
    val location: Location,
    val message: String,
)

/** Data class Location. */
data class Location(
    val uri: String,
    val range: Range,
)
