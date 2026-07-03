package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Position in a text document expressed as zero-based line and character offset
 */
data class Position(
    val line: Int,
    @get:JvmName("getColumn") val character: Int
) : Comparable<Position> {
    
    override fun compareTo(other: Position): Int {
        return when {
            line != other.line -> line.compareTo(other.line)
            else -> character.compareTo(other.character)
        }
    }

    fun isBefore(other: Position): Boolean = this < other
    fun isAfter(other: Position): Boolean = this > other
    fun isBeforeOrEqual(other: Position): Boolean = this <= other
    fun isAfterOrEqual(other: Position): Boolean = this >= other

    override fun toString(): String = "($line:$character)"
}

/**
 * A range in a text document expressed as start and end positions
 */
data class Range(
    val start: Position,
    val end: Position
) {
    fun contains(position: Position): Boolean {
        return position.isAfterOrEqual(start) && position.isBeforeOrEqual(end)
    }

    fun intersects(other: Range): Boolean {
        return !(end.isBefore(other.start) || other.end.isBefore(start))
    }

    fun isEmpty(): Boolean = start == end

    fun getLineCount(): Int = end.line - start.line + 1

    override fun toString(): String = "[$start - $end]"

    companion object {
        fun create(startLine: Int, startChar: Int, endLine: Int, endChar: Int): Range {
            return Range(
                Position(startLine, startChar),
                Position(endLine, endChar)
            )
        }

        fun at(line: Int, character: Int): Range {
            val pos = Position(line, character)
            return Range(pos, pos)
        }
    }
}