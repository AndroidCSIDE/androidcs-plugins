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
 * Enum class SymbolKind.
 *
 * @author nullij @ https://github.com/nullij
 */
enum class SymbolKind(val value: Int) {

  /** Class FILE. */
  FILE(1),

  /** Class MODULE. */
  MODULE(2),

  /** Class NAMESPACE. */
  NAMESPACE(3),

  /** Class PACKAGE. */
  PACKAGE(4),

  /** Class CLASS. */
  CLASS(5),

  /** Class METHOD. */
  METHOD(6),

  /** Class PROPERTY. */
  PROPERTY(7),

  /** Class FIELD. */
  FIELD(8),

  /** Class CONSTRUCTOR. */
  CONSTRUCTOR(9),

  /** Class ENUM. */
  ENUM(10),

  /** Class INTERFACE. */
  INTERFACE(11),

  /** Class FUNCTION. */
  FUNCTION(12),

  /** Class VARIABLE. */
  VARIABLE(13),

  /** Class CONSTANT. */
  CONSTANT(14),

  /** Class STRING. */
  STRING(15),

  /** Class NUMBER. */
  NUMBER(16),

  /** Class BOOLEAN. */
  BOOLEAN(17),

  /** Class ARRAY. */
  ARRAY(18),

  /** Class OBJECT. */
  OBJECT(19),

  /** Class KEY. */
  KEY(20),

  /** Class NULL. */
  NULL(21),

  /** Class ENUM_MEMBER. */
  ENUM_MEMBER(22),

  /** Class STRUCT. */
  STRUCT(23),

  /** Class EVENT. */
  EVENT(24),

  /** Class OPERATOR. */
  OPERATOR(25),

  /** Class TYPE_PARAMETER. */
  TYPE_PARAMETER(26);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the symbol kind
     */
    fun fromValue(value: Int): SymbolKind? {
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
      FILE -> "📄"
      MODULE -> "M"
      NAMESPACE -> "N"
      PACKAGE -> "📦"
      CLASS -> "C"
      METHOD -> "m"
      PROPERTY -> "P"
      FIELD -> "F"
      CONSTRUCTOR -> "⚙"
      ENUM -> "E"
      INTERFACE -> "I"
      FUNCTION -> "ƒ"
      VARIABLE -> "v"
      CONSTANT -> "c"
      STRING -> "\""
      NUMBER -> "#"
      BOOLEAN -> "b"
      ARRAY -> "[]"
      OBJECT -> "{}"
      KEY -> "K"
      NULL -> "∅"
      ENUM_MEMBER -> "e"
      STRUCT -> "S"
      EVENT -> "⚡"
      OPERATOR -> "+"
      TYPE_PARAMETER -> "<T>"
    }
  }
}
