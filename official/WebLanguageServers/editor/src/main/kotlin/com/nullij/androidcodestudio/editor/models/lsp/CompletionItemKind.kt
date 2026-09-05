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
 * Enum class CompletionItemKind.
 *
 * @author nullij @ https://github.com/nullij
 */
enum class CompletionItemKind(val value: Int) {

  /** Class TEXT. */
  TEXT(1),

  /** Class METHOD. */
  METHOD(2),

  /** Class FUNCTION. */
  FUNCTION(3),

  /** Class CONSTRUCTOR. */
  CONSTRUCTOR(4),

  /** Class FIELD. */
  FIELD(5),

  /** Class VARIABLE. */
  VARIABLE(6),

  /** Class CLASS. */
  CLASS(7),

  /** Class INTERFACE. */
  INTERFACE(8),

  /** Class MODULE. */
  MODULE(9),

  /** Class PROPERTY. */
  PROPERTY(10),

  /** Class UNIT. */
  UNIT(11),

  /** Class VALUE. */
  VALUE(12),

  /** Class ENUM. */
  ENUM(13),

  /** Class KEYWORD. */
  KEYWORD(14),

  /** Class SNIPPET. */
  SNIPPET(15),

  /** Class COLOR. */
  COLOR(16),

  /** Class FILE. */
  FILE(17),

  /** Class REFERENCE. */
  REFERENCE(18),

  /** Class FOLDER. */
  FOLDER(19),

  /** Class ENUM_MEMBER. */
  ENUM_MEMBER(20),

  /** Class CONSTANT. */
  CONSTANT(21),

  /** Class STRUCT. */
  STRUCT(22),

  /** Class EVENT. */
  EVENT(23),

  /** Class OPERATOR. */
  OPERATOR(24),

  /** Class TYPE_PARAMETER. */
  TYPE_PARAMETER(25);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the completion item kind
     */
    fun fromValue(value: Int): CompletionItemKind? {
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
      TEXT -> "T"
      METHOD -> "m"
      FUNCTION -> "ƒ"
      CONSTRUCTOR -> "C"
      FIELD -> "F"
      VARIABLE -> "v"
      CLASS -> "C"
      INTERFACE -> "I"
      MODULE -> "M"
      PROPERTY -> "P"
      UNIT -> "U"
      VALUE -> "V"
      ENUM -> "E"
      KEYWORD -> "K"
      SNIPPET -> "S"
      COLOR -> "◼"
      FILE -> "📄"
      REFERENCE -> "R"
      FOLDER -> "📁"
      ENUM_MEMBER -> "e"
      CONSTANT -> "c"
      STRUCT -> "S"
      EVENT -> "⚡"
      OPERATOR -> "+"
      TYPE_PARAMETER -> "<T>"
    }
  }

  /**
   * Retrieves the description
   *
   * @return the string
   */
  fun getDescription(): String {
    return when (this) {
      TEXT -> "Text"
      METHOD -> "Method"
      FUNCTION -> "Function"
      CONSTRUCTOR -> "Constructor"
      FIELD -> "Field"
      VARIABLE -> "Variable"
      CLASS -> "Class"
      INTERFACE -> "Interface"
      MODULE -> "Module"
      PROPERTY -> "Property"
      UNIT -> "Unit"
      VALUE -> "Value"
      ENUM -> "Enum"
      KEYWORD -> "Keyword"
      SNIPPET -> "Snippet"
      COLOR -> "Color"
      FILE -> "File"
      REFERENCE -> "Reference"
      FOLDER -> "Folder"
      ENUM_MEMBER -> "Enum Member"
      CONSTANT -> "Constant"
      STRUCT -> "Struct"
      EVENT -> "Event"
      OPERATOR -> "Operator"
      TYPE_PARAMETER -> "Type Parameter"
    }
  }
}
