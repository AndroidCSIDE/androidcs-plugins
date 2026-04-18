package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Represents programming constructs like variables, classes, interfaces etc.
 * that appear in a document. Document symbols can be hierarchical.
 */
data class DocumentSymbol(
    /** The name of this symbol */
    val name: String,
    
    /** More detail for this symbol, e.g. signature of a function */
    val detail: String? = null,
    
    /** The kind of this symbol */
    val kind: SymbolKind,
    
    /** Tags for this symbol */
    val tags: List<SymbolTag> = emptyList(),
    
    /** Indicates if this symbol is deprecated */
    val deprecated: Boolean = false,
    
    /** The range enclosing this symbol not including leading/trailing whitespace */
    val range: Range,
    
    /** The range that should be selected and revealed when this symbol is being picked */
    val selectionRange: Range,
    
    /** Children of this symbol, e.g. properties of a class */
    val children: List<DocumentSymbol> = emptyList()
) {
    /**
     * Check if this symbol is deprecated
     */
    fun isDeprecated(): Boolean = deprecated || tags.contains(SymbolTag.DEPRECATED)

    /**
     * Get all descendants (children, grandchildren, etc.)
     */
    fun getAllDescendants(): List<DocumentSymbol> {
        val result = mutableListOf<DocumentSymbol>()
        fun collect(symbol: DocumentSymbol) {
            result.addAll(symbol.children)
            symbol.children.forEach { collect(it) }
        }
        collect(this)
        return result
    }

    /**
     * Find symbol at position
     */
    fun findSymbolAt(position: Position): DocumentSymbol? {
        if (!range.contains(position)) return null
        
        // Check children first (more specific)
        for (child in children) {
            val found = child.findSymbolAt(position)
            if (found != null) return found
        }
        
        // If no child contains the position, return this symbol
        return if (selectionRange.contains(position)) this else null
    }

    /**
     * Get display text for this symbol
     */
    fun getDisplayText(): String {
        return buildString {
            append(kind.getIcon())
            append(" ")
            append(name)
            if (detail != null) {
                append(" ")
                append(detail)
            }
        }
    }

    companion object {
        /**
         * Create a class symbol
         */
        fun clazz(
            name: String,
            range: Range,
            selectionRange: Range,
            children: List<DocumentSymbol> = emptyList()
        ): DocumentSymbol {
            return DocumentSymbol(
                name = name,
                kind = SymbolKind.CLASS,
                range = range,
                selectionRange = selectionRange,
                children = children
            )
        }

        /**
         * Create a method symbol
         */
        fun method(
            name: String,
            signature: String? = null,
            range: Range,
            selectionRange: Range
        ): DocumentSymbol {
            return DocumentSymbol(
                name = name,
                detail = signature,
                kind = SymbolKind.METHOD,
                range = range,
                selectionRange = selectionRange
            )
        }

        /**
         * Create a function symbol
         */
        fun function(
            name: String,
            signature: String? = null,
            range: Range,
            selectionRange: Range
        ): DocumentSymbol {
            return DocumentSymbol(
                name = name,
                detail = signature,
                kind = SymbolKind.FUNCTION,
                range = range,
                selectionRange = selectionRange
            )
        }

        /**
         * Create a field symbol
         */
        fun field(
            name: String,
            type: String? = null,
            range: Range,
            selectionRange: Range
        ): DocumentSymbol {
            return DocumentSymbol(
                name = name,
                detail = type,
                kind = SymbolKind.FIELD,
                range = range,
                selectionRange = selectionRange
            )
        }

        /**
         * Create a variable symbol
         */
        fun variable(
            name: String,
            type: String? = null,
            range: Range,
            selectionRange: Range
        ): DocumentSymbol {
            return DocumentSymbol(
                name = name,
                detail = type,
                kind = SymbolKind.VARIABLE,
                range = range,
                selectionRange = selectionRange
            )
        }
    }
}

/**
 * Symbol tags
 */
enum class SymbolTag(val value: Int) {
    /** Render a symbol as obsolete, usually using a strike-out */
    DEPRECATED(1);

    companion object {
        fun fromValue(value: Int): SymbolTag? {
            return values().find { it.value == value }
        }
    }
}
