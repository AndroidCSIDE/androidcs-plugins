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
 * Enum class DiagnosticSeverity.
 *
 * @author nullij @ https://github.com/nullij
 */
enum class DiagnosticSeverity(val value: Int) {

  /** Class ERROR. */
  ERROR(1),

  /** Class WARNING. */
  WARNING(2),

  /** Class INFORMATION. */
  INFORMATION(3),

  /** Class HINT. */
  HINT(4),

  /** Class INSPECTION. */
  INSPECTION(5);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the diagnostic severity
     */
    fun fromValue(value: Int): DiagnosticSeverity? {
      return values().find { it.value == value }
    }
  }

  /**
   * Retrieves the color
   *
   * @return the int
   */
  fun getColor(): Int {
    return when (this) {
      ERROR -> 0xFFE51C23.toInt()
      WARNING -> 0xFFFFA726.toInt()
      INFORMATION -> 0xFF42A5F5.toInt()
      HINT -> 0xFF66BB6A.toInt()
      INSPECTION -> 0xFF9575CD.toInt()
    }
  }

  /**
   * Retrieves the icon
   *
   * @return the string
   */
  fun getIcon(): String {
    return when (this) {
      ERROR -> "✖"
      WARNING -> "⚠"
      INFORMATION -> "ℹ"
      HINT -> "💡"
      INSPECTION -> "◆"
    }
  }
}
