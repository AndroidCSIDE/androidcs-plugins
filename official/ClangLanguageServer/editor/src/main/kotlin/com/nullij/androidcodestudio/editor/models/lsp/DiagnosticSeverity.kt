package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Diagnostic severity levels based on LSP specification
 */
enum class DiagnosticSeverity(val value: Int) {
    ERROR(1),
    WARNING(2),
    INFORMATION(3),
    HINT(4);

    companion object {
        fun fromValue(value: Int): DiagnosticSeverity? {
            return values().find { it.value == value }
        }
    }

    /**
     * Get color for this severity level
     */
    fun getColor(): Int {
        return when (this) {
            ERROR -> 0xFFE51C23.toInt() // Red
            WARNING -> 0xFFFFA726.toInt() // Orange
            INFORMATION -> 0xFF42A5F5.toInt() // Blue
            HINT -> 0xFF66BB6A.toInt() // Green
        }
    }

    /**
     * Get icon for this severity
     */
    fun getIcon(): String {
        return when (this) {
            ERROR -> "✖"
            WARNING -> "⚠"
            INFORMATION -> "ℹ"
            HINT -> "💡"
        }
    }
}
