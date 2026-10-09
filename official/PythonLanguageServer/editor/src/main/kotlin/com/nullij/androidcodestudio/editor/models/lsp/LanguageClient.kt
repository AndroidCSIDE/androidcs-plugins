package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Language client interface for LSP operations
 * Implement this interface to provide language-specific features
 */
interface LanguageClient {
    
    suspend fun getCompletions(
        uri: String,
        position: Position,
        context: CompletionContext
    ): List<CompletionItem>

    suspend fun getHover(uri: String, position: Position): Hover?

    suspend fun getSignatureHelp(uri: String, position: Position): SignatureHelp?

    suspend fun getDocumentSymbols(uri: String): List<DocumentSymbol>

    suspend fun getDiagnostics(uri: String, content: String): List<Diagnostic>

    suspend fun getCodeActions(
        uri: String,
        range: Range,
        context: CodeActionContext
    ): List<CodeAction>

    suspend fun getDefinition(uri: String, position: Position): List<Location>

    suspend fun getReferences(uri: String, position: Position): List<Location>

    suspend fun formatDocument(uri: String, content: String): List<TextEdit>
    
    suspend fun removeUnusedImports(content: String): String? = null

    suspend fun formatRange(uri: String, range: Range, content: String): List<TextEdit>

    suspend fun rename(uri: String, position: Position, newName: String): WorkspaceEdit?

    suspend fun getDocumentHighlights(uri: String, position: Position): List<DocumentHighlight>

    suspend fun getInlayHints(uri: String, range: Range): List<InlayHint>

    /**
     * Organize imports for the document (sort + remove unused).
     * Uses KotlinOptimizeImportsFacility via source.organizeImports.
     * Does NOT add missing imports — use [getImportChoices] + [applyChosenImports] for that.
     */
    suspend fun organizeImports(uri: String): List<TextEdit>

    /**
     * Returns all available import candidates for every unresolved reference in the
     * document, grouped by symbol name — WITHOUT executing anything.
     *
     * Each [ImportChoiceGroup] holds one or more [ImportCandidate]s for one symbol.
     * The caller handles:
     *  - Groups with exactly one candidate  → auto-apply (no dialog needed)
     *  - Groups with multiple candidates    → show a disambiguation dialog, user picks one
     *
     * After collecting the user's selections, pass them to [applyChosenImports].
     *
     * Works regardless of whether live diagnostics (squiggles) are enabled — does a
     * silent one-shot textDocument/diagnostic pull internally.
     *
     * Default no-op — only Kotlin LSP overrides this.
     */
    suspend fun getImportChoices(uri: String): List<ImportChoiceGroup> = emptyList()

    /**
     * Executes the import commands for [chosen] candidates and returns the resulting
     * text edits (one `import X\n` line per chosen candidate).
     *
     * Pass exactly one [ImportCandidate] per resolved symbol — either the only available
     * one (auto-selected) or the one the user picked from the dialog.
     * Do NOT modify [ImportCandidate.commandData]; it is opaque serialized server data.
     *
     * Default no-op — only Kotlin LSP overrides this.
     */
    suspend fun applyChosenImports(uri: String, chosen: List<ImportCandidate>): List<TextEdit> = emptyList()

    /**
     * Execute a server-side command (e.g. `jetbrains.kotlin.completion.apply`).
     * Default no-op.
     */
    suspend fun executeCommand(command: LspCommand): Boolean = false

    /**
     * Execute a command and capture the resulting workspace/applyEdit edits.
     * Default no-op.
     */
    suspend fun executeCommandForImportEdits(command: LspCommand, uri: String): List<TextEdit> = emptyList()
}

/**
 * Implemented by LSP clients whose server may send a client request
 * `workspace/applyEdit` (e.g. clangd after applying a code action). The UI wires
 * the active tab via [setWorkspaceEditApplier] so edits reach the editor buffer.
 */
interface WorkspaceEditApplierSink {
    fun setWorkspaceEditApplier(applier: ((WorkspaceEdit) -> Unit)?)
}

// ─────────────────────────────────────────────────────────────────────────────
// Import choice types  (used by getImportChoices / applyChosenImports)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single import candidate for one unresolved symbol.
 *
 * @param title       Full action title from the server, e.g. "Import android.widget.TextView"
 * @param packagePath Fully-qualified class path shown in the dialog, e.g. "android.widget.TextView"
 * @param commandData Opaque serialized JSON produced by [LanguageClient.getImportChoices] and
 *                    consumed verbatim by [LanguageClient.applyChosenImports]. Do not modify.
 */
data class ImportCandidate(
    val title: String,
    val packagePath: String,
    val commandData: String
)

/**
 * All import candidates for a single unresolved symbol.
 *
 * @param symbol     The unresolved identifier, e.g. "TextView"
 * @param candidates One or more packages that resolve the reference.
 *                   size == 1  → auto-apply, no dialog needed.
 *                   size  > 1  → show a dialog, let the user choose.
 */
data class ImportChoiceGroup(
    val symbol: String,
    val candidates: List<ImportCandidate>
)

// ─────────────────────────────────────────────────────────────────────────────
// Supporting types
// ─────────────────────────────────────────────────────────────────────────────

data class CompletionContext(
    val triggerKind: CompletionTriggerKind,
    val triggerCharacter: String? = null
)

enum class CompletionTriggerKind(val value: Int) {
    INVOKED(1),
    TRIGGER_CHARACTER(2),
    TRIGGER_FOR_INCOMPLETE_COMPLETIONS(3);

    companion object {
        fun fromValue(value: Int): CompletionTriggerKind? = values().find { it.value == value }
    }
}

data class CodeActionContext(
    val diagnostics: List<Diagnostic>,
    val only: List<CodeActionKind> = emptyList()
)

data class DocumentHighlight(
    val range: Range,
    val kind: DocumentHighlightKind = DocumentHighlightKind.TEXT
)

enum class DocumentHighlightKind(val value: Int) {
    TEXT(1),
    READ(2),
    WRITE(3);

    companion object {
        fun fromValue(value: Int): DocumentHighlightKind? = values().find { it.value == value }
    }
}