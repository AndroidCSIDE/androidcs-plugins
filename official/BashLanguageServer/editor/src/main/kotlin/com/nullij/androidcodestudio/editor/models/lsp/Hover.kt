package com.nullij.androidcodestudio.editor.models.lsp

/**
 * The result of a hover request
 */
data class Hover(
    /** The hover's content */
    val contents: List<MarkedString>,
    
    /** An optional range is a range inside a text document that is used to
     * visualize a hover, e.g. by changing the background color */
    val range: Range? = null
) {
    /**
     * Get plain text content
     */
    fun getPlainText(): String {
        return contents.joinToString("\n") { it.getValue() }
    }

    /**
     * Check if hover has markdown content
     */
    fun hasMarkdown(): Boolean {
        return contents.any { it is MarkedString.MarkdownString }
    }

    companion object {
        /**
         * Create a simple hover with plain text
         */
        fun text(text: String, range: Range? = null): Hover {
            return Hover(
                contents = listOf(MarkedString.PlainText(text)),
                range = range
            )
        }

        /**
         * Create a hover with markdown
         */
        fun markdown(markdown: String, range: Range? = null): Hover {
            return Hover(
                contents = listOf(MarkedString.MarkdownString(markdown)),
                range = range
            )
        }

        /**
         * Create a hover with code
         */
        fun code(code: String, language: String, range: Range? = null): Hover {
            return Hover(
                contents = listOf(MarkedString.CodeBlock(code, language)),
                range = range
            )
        }
    }
}

/**
 * Marked string can be used to render human readable text
 */
sealed class MarkedString {
    abstract fun getValue(): String

    /**
     * Plain text
     */
    data class PlainText(val text: String) : MarkedString() {
        override fun getValue(): String = text
    }

    /**
     * Markdown text
     */
    data class MarkdownString(val markdown: String) : MarkedString() {
        override fun getValue(): String = markdown
    }

    /**
     * Code block with language
     */
    data class CodeBlock(val code: String, val language: String) : MarkedString() {
        override fun getValue(): String = "```$language\n$code\n```"
    }
}
