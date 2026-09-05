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
 * Data class SignatureHelp.
 *
 * @author nullij @ https://github.com/nullij
 */
data class SignatureHelp(
    val signatures: List<SignatureInformation>,
    val activeSignature: Int = 0,
    val activeParameter: Int = 0,
) {

  /**
   * Retrieves the active signature info
   *
   * @return the signature information
   */
  fun getActiveSignatureInfo(): SignatureInformation? {
    return signatures.getOrNull(activeSignature)
  }

  /**
   * Retrieves the active parameter info
   *
   * @return the parameter information
   */
  fun getActiveParameterInfo(): ParameterInformation? {
    return getActiveSignatureInfo()?.parameters?.getOrNull(activeParameter)
  }

  /** Companion object Companion. */
  companion object {

    /**
     * Performs the operation
     *
     * @param label the label text
     * @param parameters the collection of parameters
     * @param documentation the documentation text, or `null` if omitted
     * @param activeParameter the numeric active parameter
     * @return the signature help
     */
    fun single(
        label: String,
        parameters: List<ParameterInformation> = emptyList(),
        documentation: String? = null,
        activeParameter: Int = 0,
    ): SignatureHelp {
      return SignatureHelp(
          signatures =
              listOf(
                  SignatureInformation(
                      label = label,
                      documentation = documentation,
                      parameters = parameters,
                  )
              ),
          activeParameter = activeParameter,
      )
    }
  }
}

/** Data class SignatureInformation. */
data class SignatureInformation(
    val label: String,
    val documentation: String? = null,
    val parameters: List<ParameterInformation> = emptyList(),
    val activeParameter: Int? = null,
) {

  /**
   * Retrieves the display text
   *
   * @return the string
   */
  fun getDisplayText(): String {
    return buildString {
      append(label)
      if (documentation != null) {
        append("\n")
        append(documentation)
      }
    }
  }
}

/** Data class ParameterInformation. */
data class ParameterInformation(
    val label: String,
    val documentation: String? = null,
) {

  /**
   * Retrieves the display text
   *
   * @return the string
   */
  fun getDisplayText(): String {
    return if (documentation != null) {
      "$label: $documentation"
    } else {
      label
    }
  }

  /** Companion object Companion. */
  companion object {

    /**
     * Creates a new instance of
     *
     * @param name the name text
     * @param type the type text
     * @param documentation the documentation text, or `null` if omitted
     * @return the parameter information
     */
    fun create(
        name: String,
        type: String,
        documentation: String? = null,
    ): ParameterInformation {
      return ParameterInformation(label = "$name: $type", documentation = documentation)
    }
  }
}
