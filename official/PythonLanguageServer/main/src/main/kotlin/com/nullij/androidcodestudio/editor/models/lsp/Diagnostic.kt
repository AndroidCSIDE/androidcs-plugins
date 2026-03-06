package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Represents a diagnostic, such as a compiler error or warning
 */
data class Diagnostic(
    /** The range at which the message applies */
    val range: Range,
    
    /** The diagnostic's severity */
    val severity: DiagnosticSeverity,
    
    /** The diagnostic's code, which might appear in the user interface */
    val code: String? = null,
    
    /** A human-readable string describing the source of this diagnostic */
    val source: String? = null,
    
    /** The diagnostic's message */
    val message: String,
    
    /** Additional metadata about the diagnostic */
    val tags: List<DiagnosticTag> = emptyList(),
    
    /** An array of related diagnostic information */
    val relatedInformation: List<DiagnosticRelatedInformation> = emptyList(),
    
    /** Additional data for custom processing */
    val data: Any? = null
) {
    /**
     * Check if this diagnostic is an error
     */
    fun isError(): Boolean = severity == DiagnosticSeverity.ERROR

    /**
     * Check if this diagnostic is a warning
     */
    fun isWarning(): Boolean = severity == DiagnosticSeverity.WARNING

    /**
     * Check if this diagnostic is deprecated
     */
    fun isDeprecated(): Boolean = tags.contains(DiagnosticTag.DEPRECATED)

    /**
     * Check if this diagnostic is unnecessary (unused code)
     */
    fun isUnnecessary(): Boolean = tags.contains(DiagnosticTag.UNNECESSARY)

    /**
     * Get a formatted display string
     */
    fun toDisplayString(): String {
        return buildString {
            append(severity.getIcon())
            append(" ")
            if (source != null) {
                append("[$source] ")
            }
            append(message)
            if (code != null) {
                append(" (")
                append(code)
                append(")")
            }
        }
    }

    companion object {
        /**
         * Create an error diagnostic
         */
        fun error(range: Range, message: String, code: String? = null, source: String? = null): Diagnostic {
            return Diagnostic(
                range = range,
                severity = DiagnosticSeverity.ERROR,
                message = message,
                code = code,
                source = source
            )
        }

        /**
         * Create a warning diagnostic
         */
        fun warning(range: Range, message: String, code: String? = null, source: String? = null): Diagnostic {
            return Diagnostic(
                range = range,
                severity = DiagnosticSeverity.WARNING,
                message = message,
                code = code,
                source = source
            )
        }

        /**
         * Create an info diagnostic
         */
        fun info(range: Range, message: String, code: String? = null, source: String? = null): Diagnostic {
            return Diagnostic(
                range = range,
                severity = DiagnosticSeverity.INFORMATION,
                message = message,
                code = code,
                source = source
            )
        }

        /**
         * Create a hint diagnostic
         */
        fun hint(range: Range, message: String, code: String? = null, source: String? = null): Diagnostic {
            return Diagnostic(
                range = range,
                severity = DiagnosticSeverity.HINT,
                message = message,
                code = code,
                source = source
            )
        }
    }
}

/**
 * Diagnostic tags
 */
enum class DiagnosticTag(val value: Int) {
    /** Unused or unnecessary code */
    UNNECESSARY(1),
    
    /** Deprecated or obsolete code */
    DEPRECATED(2);

    companion object {
        fun fromValue(value: Int): DiagnosticTag? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Related information for a diagnostic
 */
data class DiagnosticRelatedInformation(
    /** The location of this related diagnostic information */
    val location: Location,
    
    /** The message of this related diagnostic information */
    val message: String
)

/**
 * Location in a document
 */
data class Location(
    /** The document URI */
    val uri: String,
    
    /** The range in the document */
    val range: Range
)
