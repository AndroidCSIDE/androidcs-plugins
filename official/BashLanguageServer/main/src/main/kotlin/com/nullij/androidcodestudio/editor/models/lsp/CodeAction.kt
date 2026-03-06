package com.nullij.androidcodestudio.editor.models.lsp

/**
 * A code action represents a change that can be performed in code
 */
data class CodeAction(
    /** A short, human-readable, title for this code action */
    val title: String,
    
    /** The kind of the code action */
    val kind: CodeActionKind? = null,
    
    /** The diagnostics that this code action resolves */
    val diagnostics: List<Diagnostic> = emptyList(),
    
    /** Marks this as a preferred action */
    val isPreferred: Boolean = false,
    
    /** The workspace edit this code action performs */
    val edit: WorkspaceEdit? = null,
    
    /** A command this code action executes */
    val command: Command? = null,
    
    /** Marks that the code action cannot currently be applied */
    val disabled: Reason? = null,
    
    /** Additional data for custom processing */
    val data: Any? = null
) {
    data class Reason(val reason: String)

    companion object {
        /**
         * Create a quick fix action
         */
        fun quickFix(
            title: String,
            diagnostic: Diagnostic,
            edit: WorkspaceEdit,
            isPreferred: Boolean = false
        ): CodeAction {
            return CodeAction(
                title = title,
                kind = CodeActionKind.QUICK_FIX,
                diagnostics = listOf(diagnostic),
                edit = edit,
                isPreferred = isPreferred
            )
        }

        /**
         * Create a refactor action
         */
        fun refactor(
            title: String,
            edit: WorkspaceEdit,
            kind: CodeActionKind = CodeActionKind.REFACTOR
        ): CodeAction {
            return CodeAction(
                title = title,
                kind = kind,
                edit = edit
            )
        }

        /**
         * Create a source action
         */
        fun source(
            title: String,
            command: Command,
            kind: CodeActionKind = CodeActionKind.SOURCE
        ): CodeAction {
            return CodeAction(
                title = title,
                kind = kind,
                command = command
            )
        }
    }
}

/**
 * Code action kinds
 */
enum class CodeActionKind(val value: String) {
    /** Empty kind */
    EMPTY(""),
    
    /** Base kind for quickfix actions */
    QUICK_FIX("quickfix"),
    
    /** Base kind for refactoring actions */
    REFACTOR("refactor"),
    
    /** Base kind for refactoring extraction actions */
    REFACTOR_EXTRACT("refactor.extract"),
    
    /** Base kind for refactoring inline actions */
    REFACTOR_INLINE("refactor.inline"),
    
    /** Base kind for refactoring rewrite actions */
    REFACTOR_REWRITE("refactor.rewrite"),
    
    /** Base kind for source actions */
    SOURCE("source"),
    
    /** Base kind for organize imports source action */
    SOURCE_ORGANIZE_IMPORTS("source.organizeImports"),
    
    /** Base kind for fix all source action */
    SOURCE_FIX_ALL("source.fixAll");

    companion object {
        fun fromValue(value: String): CodeActionKind? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Represents a reference to a command
 */
data class Command(
    /** Title of the command */
    val title: String,
    
    /** The identifier of the actual command handler */
    val command: String,
    
    /** Arguments that the command handler should be invoked with */
    val arguments: List<Any> = emptyList()
)

/**
 * A workspace edit represents changes to many resources managed in the workspace
 */
data class WorkspaceEdit(
    /** Holds changes to existing resources */
    val changes: Map<String, List<TextEdit>> = emptyMap(),
    
    /** Document changes (more advanced than simple changes) */
    val documentChanges: List<TextDocumentEdit> = emptyList()
)

/**
 * Describes textual changes on a single text document
 */
data class TextDocumentEdit(
    /** The text document to change */
    val textDocument: VersionedTextDocumentIdentifier,
    
    /** The edits to be applied */
    val edits: List<TextEdit>
)

/**
 * An identifier to denote a specific version of a text document
 */
data class VersionedTextDocumentIdentifier(
    /** The text document's URI */
    val uri: String,
    
    /** The version number of this document */
    val version: Int?
)

/**
 * A textual edit applicable to a text document
 */
data class TextEdit(
    /** The range of the text document to be manipulated */
    val range: Range,
    
    /** The string to be inserted */
    val newText: String
) {
    companion object {
        /**
         * Create an insert edit
         */
        fun insert(position: Position, text: String): TextEdit {
            return TextEdit(Range(position, position), text)
        }

        /**
         * Create a delete edit
         */
        fun delete(range: Range): TextEdit {
            return TextEdit(range, "")
        }

        /**
         * Create a replace edit
         */
        fun replace(range: Range, text: String): TextEdit {
            return TextEdit(range, text)
        }
    }
}
