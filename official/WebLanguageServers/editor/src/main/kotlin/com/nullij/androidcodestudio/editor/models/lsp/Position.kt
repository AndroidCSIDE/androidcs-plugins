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
 * Data class Position.
 *
 * @author nullij @ https://github.com/nullij
 */
data class Position(val line: Int, @get:JvmName("getColumn") val character: Int) :
    Comparable<Position> {

  /**
   * Retrieves the character
   *
   * @return the int
   */
  @JvmName("getCharacter") fun getCharacter(): Int = character

  /**
   * Performs the to operation
   *
   * @param other the other as a [Position]
   * @return the int
   */
  override fun compareTo(other: Position): Int {
    return when {
      line != other.line -> line.compareTo(other.line)
      else -> character.compareTo(other.character)
    }
  }

  /**
   * Indicates whether before
   *
   * @param other the other as a [Position]
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isBefore(other: Position): Boolean = this < other

  /**
   * Indicates whether after
   *
   * @param other the other as a [Position]
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isAfter(other: Position): Boolean = this > other

  /**
   * Indicates whether before or equal
   *
   * @param other the other as a [Position]
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isBeforeOrEqual(other: Position): Boolean = this <= other

  /**
   * Indicates whether after or equal
   *
   * @param other the other as a [Position]
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isAfterOrEqual(other: Position): Boolean = this >= other

  /**
   * Returns a human-readable representation of
   *
   * @return the string
   */
  override fun toString(): String = "($line:$character)"
}

/** Data class Range. */
data class Range(val start: Position, val end: Position) {

  /**
   * Determines if
   *
   * @param position the position as a [Position]
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun contains(position: Position): Boolean {
    return position.isAfterOrEqual(start) && position.isBeforeOrEqual(end)
  }

  /**
   * Performs the operation
   *
   * @param other the other as a [Range]
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun intersects(other: Range): Boolean {
    return !(end.isBefore(other.start) || other.end.isBefore(start))
  }

  /**
   * Indicates whether empty
   *
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  fun isEmpty(): Boolean = start == end

  /**
   * Retrieves the line count
   *
   * @return the int
   */
  fun getLineCount(): Int = end.line - start.line + 1

  /**
   * Returns a human-readable representation of
   *
   * @return the string
   */
  override fun toString(): String = "[$start - $end]"

  /** Companion object Companion. */
  companion object {

    /**
     * Creates a new instance of
     *
     * @param startLine the numeric start line
     * @param startChar the numeric start char
     * @param endLine the numeric end line
     * @param endChar the numeric end char
     * @return the range
     */
    fun create(startLine: Int, startChar: Int, endLine: Int, endChar: Int): Range {
      return Range(Position(startLine, startChar), Position(endLine, endChar))
    }

    /**
     * Represents the at
     *
     * @param line the numeric line
     * @param character the numeric character
     * @return the range
     */
    fun at(line: Int, character: Int): Range {
      val pos = Position(line, character)
      return Range(pos, pos)
    }
  }
}
