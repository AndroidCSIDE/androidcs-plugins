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
 * Enum class FoldingRangeKind.
 *
 * @author nullij @ https://github.com/nullij
 */
enum class FoldingRangeKind {

  /** Class COMMENT. */
  COMMENT,

  /** Class IMPORTS. */
  IMPORTS,

  /** Class REGION. */
  REGION,
}

/** Data class FoldingRange. */
data class FoldingRange(val start: Position, val end: Position, val kind: FoldingRangeKind? = null)
