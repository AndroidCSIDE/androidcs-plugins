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
 * Data class InlayHint.
 *
 * @author nullij @ https://github.com/nullij
 */
data class InlayHint(
    val position: Position,
    val label: InlayHintLabel,
    val kind: InlayHintKind? = null,
    val textEdits: List<TextEdit> = emptyList(),
    val tooltip: String? = null,
    val paddingLeft: Boolean = false,
    val paddingRight: Boolean = false,
    val data: Any? = null,
) {

  /**
   * Retrieves the label text
   *
   * @return the string
   */
  fun getLabelText(): String {
    return when (label) {
      is InlayHintLabel.String -> label.value
      is InlayHintLabel.Parts -> label.parts.joinToString("") { it.value }
    }
  }

  /**
   * Retrieves the display text
   *
   * @return the string
   */
  fun getDisplayText(): String {
    val text = getLabelText()
    return buildString {
      if (paddingLeft) append(" ")
      append(text)
      if (paddingRight) append(" ")
    }
  }

  /** Companion object Companion. */
  companion object {

    /**
     * Creates a new instance of
     *
     * @param position the position as a [Position]
     * @param label the label text
     * @param kind the [InlayHintKind] instance, or `null` if omitted
     * @param paddingLeft whether padding left is enabled
     * @param paddingRight whether padding right is enabled
     * @return the inlay hint
     */
    fun create(
        position: Position,
        label: String,
        kind: InlayHintKind? = null,
        paddingLeft: Boolean = false,
        paddingRight: Boolean = false,
    ): InlayHint {
      return InlayHint(
          position = position,
          label = InlayHintLabel.String(label),
          kind = kind,
          paddingLeft = paddingLeft,
          paddingRight = paddingRight,
      )
    }

    /**
     * Performs the operation
     *
     * @param position the position as a [Position]
     * @param parameterName the parameter name text
     * @return the inlay hint
     */
    fun parameterName(position: Position, parameterName: String): InlayHint {
      return create(
          position = position,
          label = "$parameterName:",
          kind = InlayHintKind.PARAMETER,
          paddingRight = true,
      )
    }

    /**
     * Represents the type
     *
     * @param position the position as a [Position]
     * @param typeName the type name text
     * @return the inlay hint
     */
    fun type(position: Position, typeName: String): InlayHint {
      return create(
          position = position,
          label = ": $typeName",
          kind = InlayHintKind.TYPE,
          paddingLeft = true,
      )
    }
  }
}

/** Sealed class InlayHintLabel. */
sealed class InlayHintLabel {

  /** Data class String. */
  data class String(val value: kotlin.String) : InlayHintLabel()

  /** Data class Parts. */
  data class Parts(val parts: List<InlayHintLabelPart>) : InlayHintLabel()
}

/** Data class InlayHintLabelPart. */
data class InlayHintLabelPart(
    val value: String,
    val tooltip: String? = null,
    val location: Location? = null,
    val command: Command? = null,
)

/** Enum class InlayHintKind. */
enum class InlayHintKind(val value: Int) {

  /** Class TYPE. */
  TYPE(1),

  /** Class PARAMETER. */
  PARAMETER(2);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the inlay hint kind
     */
    fun fromValue(value: Int): InlayHintKind? {
      return values().find { it.value == value }
    }
  }

  /**
   * Retrieves the icon
   *
   * @return the string
   */
  fun getIcon(): String {
    return when (this) {
      TYPE -> ":"
      PARAMETER -> "•"
    }
  }

  /**
   * Retrieves the description
   *
   * @return the string
   */
  fun getDescription(): String {
    return when (this) {
      TYPE -> "Type"
      PARAMETER -> "Parameter"
    }
  }
}
