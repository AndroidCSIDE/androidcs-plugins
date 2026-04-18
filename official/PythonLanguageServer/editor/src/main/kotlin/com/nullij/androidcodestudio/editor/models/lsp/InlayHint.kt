package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Inlay hint information from LSP server
 * Inlay hints appear inline in the editor to show additional information
 * like parameter names, type annotations, etc.
 */
data class InlayHint(
    /** The position where the hint should be displayed */
    val position: Position,
    
    /** The label of this hint */
    val label: InlayHintLabel,
    
    /** The kind of this hint */
    val kind: InlayHintKind? = null,
    
    /** Optional text edits to apply when accepting this hint */
    val textEdits: List<TextEdit> = emptyList(),
    
    /** Optional tooltip */
    val tooltip: String? = null,
    
    /** Whether this hint should be rendered with padding on the left */
    val paddingLeft: Boolean = false,
    
    /** Whether this hint should be rendered with padding on the right */
    val paddingRight: Boolean = false,
    
    /** Additional data for custom processing */
    val data: Any? = null
) {
    /**
     * Get the plain text representation of the label
     */
    fun getLabelText(): String {
        return when (label) {
            is InlayHintLabel.String -> label.value
            is InlayHintLabel.Parts -> label.parts.joinToString("") { it.value }
        }
    }

    /**
     * Get display text with padding if needed
     */
    fun getDisplayText(): String {
        val text = getLabelText()
        return buildString {
            if (paddingLeft) append(" ")
            append(text)
            if (paddingRight) append(" ")
        }
    }

    companion object {
        /**
         * Create a simple inlay hint with string label
         */
        fun create(
            position: Position,
            label: String,
            kind: InlayHintKind? = null,
            paddingLeft: Boolean = false,
            paddingRight: Boolean = false
        ): InlayHint {
            return InlayHint(
                position = position,
                label = InlayHintLabel.String(label),
                kind = kind,
                paddingLeft = paddingLeft,
                paddingRight = paddingRight
            )
        }

        /**
         * Create a parameter name hint
         */
        fun parameterName(
            position: Position,
            parameterName: String
        ): InlayHint {
            return create(
                position = position,
                label = "$parameterName:",
                kind = InlayHintKind.PARAMETER,
                paddingRight = true
            )
        }

        /**
         * Create a type hint
         */
        fun type(
            position: Position,
            typeName: String
        ): InlayHint {
            return create(
                position = position,
                label = ": $typeName",
                kind = InlayHintKind.TYPE,
                paddingLeft = true
            )
        }
    }
}

/**
 * Label for an inlay hint - can be either a simple string or parts with tooltips
 */
sealed class InlayHintLabel {
    /**
     * Simple string label
     */
    data class String(val value: kotlin.String) : InlayHintLabel()
    
    /**
     * Label consisting of multiple parts, each with optional tooltip and location
     */
    data class Parts(val parts: List<InlayHintLabelPart>) : InlayHintLabel()
}

/**
 * A part of an inlay hint label
 */
data class InlayHintLabelPart(
    /** The value of this label part */
    val value: String,
    
    /** Optional tooltip for this part */
    val tooltip: String? = null,
    
    /** Optional location for this part (for navigation) */
    val location: Location? = null,
    
    /** Optional command to execute when clicking this part */
    val command: Command? = null
)

/**
 * Inlay hint kinds
 */
enum class InlayHintKind(val value: Int) {
    /** Hint shows the type of an expression */
    TYPE(1),
    
    /** Hint shows the name of a parameter */
    PARAMETER(2);

    companion object {
        fun fromValue(value: Int): InlayHintKind? {
            return values().find { it.value == value }
        }
    }

    /**
     * Get icon for this hint kind
     */
    fun getIcon(): String {
        return when (this) {
            TYPE -> ":"
            PARAMETER -> "•"
        }
    }

    /**
     * Get description for this hint kind
     */
    fun getDescription(): String {
        return when (this) {
            TYPE -> "Type"
            PARAMETER -> "Parameter"
        }
    }
}