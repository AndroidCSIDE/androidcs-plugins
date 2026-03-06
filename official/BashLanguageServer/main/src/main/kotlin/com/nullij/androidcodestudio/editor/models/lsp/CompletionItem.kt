package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Completion item represents a single suggestion in autocomplete
 */
data class CompletionItem(
    /** The label of this completion item */
    val label: String,
    
    /** The kind of this completion item */
    val kind: CompletionItemKind,
    
    /** A human-readable string with additional information */
    val detail: String? = null,
    
    /** A human-readable string that represents a doc-comment */
    val documentation: String? = null,
    
    /** A string that should be used when comparing this item with other items */
    val sortText: String? = null,
    
    /** A string that should be used when filtering a set of completion items */
    val filterText: String? = null,
    
    /** A string that should be inserted into a document when selecting this completion */
    val insertText: String? = null,
    
    /** The format of the insert text */
    val insertTextFormat: InsertTextFormat = InsertTextFormat.PLAIN_TEXT,
    
    /** Tags for this completion item */
    val tags: List<CompletionItemTag> = emptyList(),
    
    /** Indicates if this item is deprecated */
    val deprecated: Boolean = false,
    
    /** Select this item when showing */
    val preselect: Boolean = false,
    
    /** Additional text edits (used for auto-imports) */
    val additionalTextEdits: List<TextEdit> = emptyList(),
    
    /** Additional data for custom processing */
    val data: Any? = null
) {
    /**
     * Get the text to insert (fallback to label if insertText is null)
     */
    fun getTextToInsert(): String = insertText ?: label

    /**
     * Get the text to display (label)
     */
    fun getDisplayText(): String = label

    /**
     * Get sort key (fallback to label if sortText is null)
     */
    fun getSortKey(): String = sortText ?: label

    /**
     * Get filter key (fallback to label if filterText is null)
     */
    fun getFilterKey(): String = filterText ?: label

    /**
     * Check if this item requires auto-import
     */
    fun hasAutoImport(): Boolean = additionalTextEdits.isNotEmpty()

    companion object {
        /**
         * Create a simple completion item
         */
        fun simple(label: String, kind: CompletionItemKind): CompletionItem {
            return CompletionItem(label = label, kind = kind)
        }

        /**
         * Create a completion item with auto-import
         */
        fun withImport(
            label: String,
            kind: CompletionItemKind,
            importStatement: String,
            detail: String? = null
        ): CompletionItem {
            // Create text edit to add import at the top of the file
            val importEdit = TextEdit.insert(
                Position(0, 0),
                "$importStatement\n"
            )
            
            return CompletionItem(
                label = label,
                kind = kind,
                detail = detail,
                additionalTextEdits = listOf(importEdit)
            )
        }

        /**
         * Create a keyword completion item
         */
        fun keyword(keyword: String): CompletionItem {
            return CompletionItem(
                label = keyword,
                kind = CompletionItemKind.KEYWORD,
                insertText = keyword
            )
        }

        /**
         * Create a snippet completion item
         */
        fun snippet(label: String, insertText: String, detail: String? = null): CompletionItem {
            return CompletionItem(
                label = label,
                kind = CompletionItemKind.SNIPPET,
                insertText = insertText,
                insertTextFormat = InsertTextFormat.SNIPPET,
                detail = detail
            )
        }

        /**
         * Create a method completion item
         */
        fun method(
            name: String,
            returnType: String? = null,
            parameters: List<String> = emptyList(),
            documentation: String? = null
        ): CompletionItem {
            val paramsStr = parameters.joinToString(", ")
            val detail = buildString {
                append(name)
                append("(")
                append(paramsStr)
                append(")")
                if (returnType != null) {
                    append(": ")
                    append(returnType)
                }
            }

            return CompletionItem(
                label = name,
                kind = CompletionItemKind.METHOD,
                detail = detail,
                documentation = documentation,
                insertText = "$name($paramsStr)"
            )
        }

        /**
         * Create a class completion item
         */
        fun clazz(
            name: String,
            packageName: String? = null,
            documentation: String? = null
        ): CompletionItem {
            return CompletionItem(
                label = name,
                kind = CompletionItemKind.CLASS,
                detail = packageName,
                documentation = documentation
            )
        }

        /**
         * Create a variable completion item
         */
        fun variable(
            name: String,
            type: String? = null,
            value: String? = null
        ): CompletionItem {
            val detail = if (type != null) "$name: $type" else name
            return CompletionItem(
                label = name,
                kind = CompletionItemKind.VARIABLE,
                detail = detail,
                documentation = value
            )
        }
    }
}

/**
 * How a completion item should be inserted
 */
enum class InsertTextFormat(val value: Int) {
    /** Plain text */
    PLAIN_TEXT(1),
    
    /** Snippet format with placeholders like ${1:foo} */
    SNIPPET(2);

    companion object {
        fun fromValue(value: Int): InsertTextFormat? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Completion item tags
 */
enum class CompletionItemTag(val value: Int) {
    /** Render a completion as obsolete, usually using a strike-out */
    DEPRECATED(1);

    companion object {
        fun fromValue(value: Int): CompletionItemTag? {
            return values().find { it.value == value }
        }
    }
}