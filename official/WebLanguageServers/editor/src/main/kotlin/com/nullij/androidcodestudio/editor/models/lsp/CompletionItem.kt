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
 * Data class CompletionItem.
 *
 * @author nullij @ https://github.com/nullij
 */
data class CompletionItem(
    val label: String,
    val kind: CompletionItemKind,
    val detail: String? = null,
    val documentation: String? = null,
    val sortText: String? = null,
    val filterText: String? = null,
    val insertText: String? = null,
    val insertTextFormat: InsertTextFormat = InsertTextFormat.PLAIN_TEXT,
    val tags: List<CompletionItemTag> = emptyList(),
    val deprecated: Boolean = false,
    val preselect: Boolean = false,
    val additionalTextEdits: List<TextEdit> = emptyList(),
    val data: Any? = null,
    val textEdit: TextEdit? = null,
    val command: LspCommand? = null,
) {

  /**
   * Retrieves the text to insert
   *
   * @return the string
   */
  fun getTextToInsert(): String = insertText ?: label

  /**
   * Retrieves the display text
   *
   * @return the string
   */
  fun getDisplayText(): String = label

  /**
   * Retrieves the sort key
   *
   * @return the string
   */
  fun getSortKey(): String = sortText ?: label

  /**
   * Retrieves the filter key
   *
   * @return the string
   */
  fun getFilterKey(): String = filterText ?: label

  /**
   * Indicates whether auto import is present
   *
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun hasAutoImport(): Boolean = additionalTextEdits.isNotEmpty()

  /** Companion object Companion. */
  companion object {

    /**
     * Represents the simple
     *
     * @param label the label text
     * @param kind the [CompletionItemKind] instance
     * @return the completion item
     */
    fun simple(label: String, kind: CompletionItemKind): CompletionItem {
      return CompletionItem(label = label, kind = kind)
    }

    /**
     * Performs the operation
     *
     * @param label the label text
     * @param kind the [CompletionItemKind] instance
     * @param importStatement the import statement text
     * @param detail the detail text, or `null` if omitted
     * @return the completion item
     */
    fun withImport(
        label: String,
        kind: CompletionItemKind,
        importStatement: String,
        detail: String? = null,
    ): CompletionItem {
      val importEdit = TextEdit.insert(Position(0, 0), "$importStatement\n")

      return CompletionItem(
          label = label,
          kind = kind,
          detail = detail,
          additionalTextEdits = listOf(importEdit),
      )
    }

    /**
     * Performs the operation
     *
     * @param keyword the keyword text
     * @return the completion item
     */
    fun keyword(keyword: String): CompletionItem {
      return CompletionItem(
          label = keyword,
          kind = CompletionItemKind.KEYWORD,
          insertText = keyword,
      )
    }

    /**
     * Performs the operation
     *
     * @param label the label text
     * @param insertText the insert text text
     * @param detail the detail text, or `null` if omitted
     * @return the completion item
     */
    fun snippet(label: String, insertText: String, detail: String? = null): CompletionItem {
      return CompletionItem(
          label = label,
          kind = CompletionItemKind.SNIPPET,
          insertText = insertText,
          insertTextFormat = InsertTextFormat.SNIPPET,
          detail = detail,
      )
    }

    /**
     * Represents the method
     *
     * @param name the name text
     * @param returnType the return type text, or `null` if omitted
     * @param parameters the collection of parameters
     * @param documentation the documentation text, or `null` if omitted
     * @return the completion item
     */
    fun method(
        name: String,
        returnType: String? = null,
        parameters: List<String> = emptyList(),
        documentation: String? = null,
    ): CompletionItem {
      val paramsStr = parameters.joinToString(", ")
      val detail = buildString {
        append(name)
        append("(")
        append(paramsStr)
        append(")")
        if (returnType != null) {
          append(": ")
          append(returnType)
        }
      }

      return CompletionItem(
          label = name,
          kind = CompletionItemKind.METHOD,
          detail = detail,
          documentation = documentation,
          insertText = "$name($paramsStr)",
      )
    }

    /**
     * Performs the operation
     *
     * @param name the name text
     * @param packageName the package name text, or `null` if omitted
     * @param documentation the documentation text, or `null` if omitted
     * @return the completion item
     */
    fun clazz(
        name: String,
        packageName: String? = null,
        documentation: String? = null,
    ): CompletionItem {
      return CompletionItem(
          label = name,
          kind = CompletionItemKind.CLASS,
          detail = packageName,
          documentation = documentation,
      )
    }

    /**
     * Performs the operation
     *
     * @param name the name text
     * @param type the type text, or `null` if omitted
     * @param value the value text, or `null` if omitted
     * @return the completion item
     */
    fun variable(name: String, type: String? = null, value: String? = null): CompletionItem {
      val detail = if (type != null) "$name: $type" else name
      return CompletionItem(
          label = name,
          kind = CompletionItemKind.VARIABLE,
          detail = detail,
          documentation = value,
      )
    }
  }
}

/** Enum class InsertTextFormat. */
enum class InsertTextFormat(val value: Int) {

  /** Class PLAIN_TEXT. */
  PLAIN_TEXT(1),

  /** Class SNIPPET. */
  SNIPPET(2);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the insert text format
     */
    fun fromValue(value: Int): InsertTextFormat? {
      return values().find { it.value == value }
    }
  }
}

/** Enum class CompletionItemTag. */
enum class CompletionItemTag(val value: Int) {

  /** Class DEPRECATED. */
  DEPRECATED(1);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the completion item tag
     */
    fun fromValue(value: Int): CompletionItemTag? {
      return values().find { it.value == value }
    }
  }
}
