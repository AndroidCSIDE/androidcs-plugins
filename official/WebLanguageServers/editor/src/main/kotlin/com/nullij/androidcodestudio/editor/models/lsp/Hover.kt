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
 * Data class Hover.
 *
 * @author nullij @ https://github.com/nullij
 */
data class Hover(
    val contents: List<MarkedString>,
    val range: Range? = null,
) {

  /**
   * Retrieves the plain text
   *
   * @return the string
   */
  fun getPlainText(): String {
    return contents.joinToString("\n") { it.getValue() }
  }

  /**
   * Indicates whether markdown is present
   *
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun hasMarkdown(): Boolean {
    return contents.any { it is MarkedString.MarkdownString }
  }

  /** Companion object Companion. */
  companion object {

    /**
     * Performs the operation
     *
     * @param text the text text
     * @param range the range as a [Range], or `null` if omitted
     * @return the hover
     */
    fun text(text: String, range: Range? = null): Hover {
      return Hover(contents = listOf(MarkedString.PlainText(text)), range = range)
    }

    /**
     * Performs the operation
     *
     * @param markdown the markdown text
     * @param range the range as a [Range], or `null` if omitted
     * @return the hover
     */
    fun markdown(markdown: String, range: Range? = null): Hover {
      return Hover(contents = listOf(MarkedString.MarkdownString(markdown)), range = range)
    }

    /**
     * Represents the code
     *
     * @param code the code text
     * @param language the language text
     * @param range the range as a [Range], or `null` if omitted
     * @return the hover
     */
    fun code(code: String, language: String, range: Range? = null): Hover {
      return Hover(contents = listOf(MarkedString.CodeBlock(code, language)), range = range)
    }
  }
}

/** Sealed class MarkedString. */
sealed class MarkedString {

  /**
   * Retrieves the value
   *
   * @return the string
   */
  abstract fun getValue(): String

  /** Data class PlainText. */
  data class PlainText(val text: String) : MarkedString() {

    /**
     * Retrieves the value
     *
     * @return the string
     */
    override fun getValue(): String = text
  }

  /** Data class MarkdownString. */
  data class MarkdownString(val markdown: String) : MarkedString() {

    /**
     * Retrieves the value
     *
     * @return the string
     */
    override fun getValue(): String = markdown
  }

  /** Data class CodeBlock. */
  data class CodeBlock(val code: String, val language: String) : MarkedString() {

    /**
     * Retrieves the value
     *
     * @return the string
     */
    override fun getValue(): String = "```$language\n$code\n```"
  }
}
