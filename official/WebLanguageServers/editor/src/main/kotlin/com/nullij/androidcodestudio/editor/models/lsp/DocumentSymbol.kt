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
 * Data class DocumentSymbol.
 *
 * @author nullij @ https://github.com/nullij
 */
data class DocumentSymbol(
    val name: String,
    val detail: String? = null,
    val kind: SymbolKind,
    val tags: List<SymbolTag> = emptyList(),
    val deprecated: Boolean = false,
    val range: Range,
    val selectionRange: Range,
    val children: List<DocumentSymbol> = emptyList(),
) {

  /**
   * Indicates whether deprecated
   *
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isDeprecated(): Boolean = deprecated || tags.contains(SymbolTag.DEPRECATED)

  /**
   * Retrieves the all descendantses
   *
   * @return a collection of document symbols
   */
  fun getAllDescendants(): List<DocumentSymbol> {
    val result = mutableListOf<DocumentSymbol>()
    fun collect(symbol: DocumentSymbol) {
      result.addAll(symbol.children)
      symbol.children.forEach { collect(it) }
    }
    collect(this)
    return result
  }

  /**
   * Retrieves the symbol at
   *
   * @param position the position as a [Position]
   * @return the document symbol
   */
  fun findSymbolAt(position: Position): DocumentSymbol? {
    if (!range.contains(position)) return null

    for (child in children) {
      val found = child.findSymbolAt(position)
      if (found != null) return found
    }

    return if (selectionRange.contains(position)) this else null
  }

  /**
   * Retrieves the display text
   *
   * @return the string
   */
  fun getDisplayText(): String {
    return buildString {
      append(kind.getIcon())
      append(" ")
      append(name)
      if (detail != null) {
        append(" ")
        append(detail)
      }
    }
  }

  /** Companion object Companion. */
  companion object {

    /**
     * Performs the operation
     *
     * @param name the name text
     * @param range the range as a [Range]
     * @param selectionRange the selection range as a [Range]
     * @param children the collection of children
     * @return the document symbol
     */
    fun clazz(
        name: String,
        range: Range,
        selectionRange: Range,
        children: List<DocumentSymbol> = emptyList(),
    ): DocumentSymbol {
      return DocumentSymbol(
          name = name,
          kind = SymbolKind.CLASS,
          range = range,
          selectionRange = selectionRange,
          children = children,
      )
    }

    /**
     * Represents the method
     *
     * @param name the name text
     * @param signature the signature text, or `null` if omitted
     * @param range the range as a [Range]
     * @param selectionRange the selection range as a [Range]
     * @return the document symbol
     */
    fun method(
        name: String,
        signature: String? = null,
        range: Range,
        selectionRange: Range,
    ): DocumentSymbol {
      return DocumentSymbol(
          name = name,
          detail = signature,
          kind = SymbolKind.METHOD,
          range = range,
          selectionRange = selectionRange,
      )
    }

    /**
     * Represents the function
     *
     * @param name the name text
     * @param signature the signature text, or `null` if omitted
     * @param range the range as a [Range]
     * @param selectionRange the selection range as a [Range]
     * @return the document symbol
     */
    fun function(
        name: String,
        signature: String? = null,
        range: Range,
        selectionRange: Range,
    ): DocumentSymbol {
      return DocumentSymbol(
          name = name,
          detail = signature,
          kind = SymbolKind.FUNCTION,
          range = range,
          selectionRange = selectionRange,
      )
    }

    /**
     * Performs the operation
     *
     * @param name the name text
     * @param type the type text, or `null` if omitted
     * @param range the range as a [Range]
     * @param selectionRange the selection range as a [Range]
     * @return the document symbol
     */
    fun field(
        name: String,
        type: String? = null,
        range: Range,
        selectionRange: Range,
    ): DocumentSymbol {
      return DocumentSymbol(
          name = name,
          detail = type,
          kind = SymbolKind.FIELD,
          range = range,
          selectionRange = selectionRange,
      )
    }

    /**
     * Performs the operation
     *
     * @param name the name text
     * @param type the type text, or `null` if omitted
     * @param range the range as a [Range]
     * @param selectionRange the selection range as a [Range]
     * @return the document symbol
     */
    fun variable(
        name: String,
        type: String? = null,
        range: Range,
        selectionRange: Range,
    ): DocumentSymbol {
      return DocumentSymbol(
          name = name,
          detail = type,
          kind = SymbolKind.VARIABLE,
          range = range,
          selectionRange = selectionRange,
      )
    }
  }
}

/** Enum class SymbolTag. */
enum class SymbolTag(val value: Int) {

  /** Class DEPRECATED. */
  DEPRECATED(1);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the symbol tag
     */
    fun fromValue(value: Int): SymbolTag? {
      return values().find { it.value == value }
    }
  }
}
