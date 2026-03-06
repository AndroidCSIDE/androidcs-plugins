package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Language client interface for LSP operations
 * Implement this interface to provide language-specific features
 */
interface LanguageClient {
    
    /**
     * Get completion items at a specific position
     */
    suspend fun getCompletions(
        uri: String,
        position: Position,
        context: CompletionContext
    ): List<CompletionItem>

    /**
     * Get hover information at a specific position
     */
    suspend fun getHover(uri: String, position: Position): Hover?

    /**
     * Get signature help at a specific position
     */
    suspend fun getSignatureHelp(uri: String, position: Position): SignatureHelp?

    /**
     * Get document symbols (outline)
     */
    suspend fun getDocumentSymbols(uri: String): List<DocumentSymbol>

    /**
     * Get diagnostics (errors, warnings) for a document
     */
    suspend fun getDiagnostics(uri: String, content: String): List<Diagnostic>

    /**
     * Get code actions available for a range
     */
    suspend fun getCodeActions(
        uri: String,
        range: Range,
        context: CodeActionContext
    ): List<CodeAction>

    /**
     * Find definition of symbol at position
     */
    suspend fun getDefinition(uri: String, position: Position): List<Location>

    /**
     * Find references to symbol at position
     */
    suspend fun getReferences(uri: String, position: Position): List<Location>

    /**
     * Format document
     */
    suspend fun formatDocument(uri: String, content: String): List<TextEdit>

    /**
     * Format range in document
     */
    suspend fun formatRange(uri: String, range: Range, content: String): List<TextEdit>

    /**
     * Rename symbol at position
     */
    suspend fun rename(uri: String, position: Position, newName: String): WorkspaceEdit?

    /**
     * Get document highlights (occurrences) of symbol at position
     */
    suspend fun getDocumentHighlights(uri: String, position: Position): List<DocumentHighlight>

    /**
     * Get inlay hints for a range in the document
     */
    suspend fun getInlayHints(uri: String, range: Range): List<InlayHint>
}

/**
 * Context for completion requests
 */
data class CompletionContext(
    /** How the completion was triggered */
    val triggerKind: CompletionTriggerKind,
    
    /** The trigger character (if triggered by a character) */
    val triggerCharacter: String? = null
)

/**
 * How a completion was triggered
 */
enum class CompletionTriggerKind(val value: Int) {
    /** Completion was triggered by typing an identifier */
    INVOKED(1),
    
    /** Completion was triggered by a trigger character */
    TRIGGER_CHARACTER(2),
    
    /** Completion was re-triggered as the current completion list is incomplete */
    TRIGGER_FOR_INCOMPLETE_COMPLETIONS(3);

    companion object {
        fun fromValue(value: Int): CompletionTriggerKind? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Context for code action requests
 */
data class CodeActionContext(
    /** The diagnostics in the range */
    val diagnostics: List<Diagnostic>,
    
    /** Requested kind of actions to return */
    val only: List<CodeActionKind> = emptyList()
)

/**
 * A document highlight is a range inside a text document which deserves
 * special attention
 */
data class DocumentHighlight(
    /** The range this highlight applies to */
    val range: Range,
    
    /** The highlight kind */
    val kind: DocumentHighlightKind = DocumentHighlightKind.TEXT
)

/**
 * Document highlight kind
 */
enum class DocumentHighlightKind(val value: Int) {
    /** A textual occurrence */
    TEXT(1),
    
    /** Read-access of a symbol */
    READ(2),
    
    /** Write-access of a symbol */
    WRITE(3);

    companion object {
        fun fromValue(value: Int): DocumentHighlightKind? {
            return values().find { it.value == value }
        }
    }
}