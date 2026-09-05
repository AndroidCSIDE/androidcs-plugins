/*
 *  This file is part of ACSIDE.
 *
 *  ACSIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ACSIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ACSIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.nullij.androidcodestudio.editor.models.lsp

/**
 * Class LanguageClient.
 *
 * @author nullij @ https://github.com/nullij
 */
interface LanguageClient {

  /**
   * Retrieves the completionses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param position the position as a [Position]
   * @param context the [CompletionContext] instance
   * @return a collection of completion items
   */
  suspend fun getCompletions(
      uri: String,
      position: Position,
      context: CompletionContext,
  ): List<CompletionItem>

  /**
   * Retrieves the hover
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param position the position as a [Position]
   * @return the hover
   */
  suspend fun getHover(uri: String, position: Position): Hover?

  /**
   * Retrieves the signature help
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param position the position as a [Position]
   * @return the signature help
   */
  suspend fun getSignatureHelp(uri: String, position: Position): SignatureHelp?

  /**
   * Retrieves the document symbolses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @return a collection of document symbols
   */
  suspend fun getDocumentSymbols(uri: String): List<DocumentSymbol>

  /**
   * Retrieves the diagnosticses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param content the content text
   * @return a collection of diagnostics
   */
  suspend fun getDiagnostics(uri: String, content: String): List<Diagnostic>

  /** Registers a listener for pushed diagnostics when the client supports it. */
  fun addDiagnosticsListener(
      uri: String,
      listener: (List<Diagnostic>) -> Unit,
  ): (() -> Unit)? = null

  /**
   * Retrieves the code actionses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param range the range as a [Range]
   * @param context the [CodeActionContext] instance
   * @return a collection of code actions
   */
  suspend fun getCodeActions(
      uri: String,
      range: Range,
      context: CodeActionContext,
  ): List<CodeAction>

  /**
   * Retrieves the definitions
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param position the position as a [Position]
   * @return a collection of locations
   */
  suspend fun getDefinition(uri: String, position: Position): List<Location>

  /**
   * Retrieves the referenceses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param position the position as a [Position]
   * @return a collection of locations
   */
  suspend fun getReferences(uri: String, position: Position): List<Location>

  /**
   * Formats and displays the documents
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param content the content text
   * @return a collection of text edits
   */
  suspend fun formatDocument(uri: String, content: String): List<TextEdit>

  /**
   * Removes the specified unused imports
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param content the content text
   * @return the string
   */
  suspend fun removeUnusedImports(content: String): String? = null

  /**
   * Formats and displays the ranges
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param range the range as a [Range]
   * @param content the content text
   * @return a collection of text edits
   */
  suspend fun formatRange(uri: String, range: Range, content: String): List<TextEdit>

  /**
   * Performs the operation
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param position the position as a [Position]
   * @param newName the new name text
   * @return the workspace edit
   */
  suspend fun rename(uri: String, position: Position, newName: String): WorkspaceEdit?

  /**
   * Retrieves the document highlightses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param position the position as a [Position]
   * @return a collection of document highlights
   */
  suspend fun getDocumentHighlights(uri: String, position: Position): List<DocumentHighlight>

  /**
   * Retrieves the inlay hintses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param range the range as a [Range]
   * @return a collection of inlay hints
   */
  suspend fun getInlayHints(uri: String, range: Range): List<InlayHint>

  /**
   * Retrieves the folding rangeses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param content the content text
   * @return a collection of folding ranges
   */
  suspend fun getFoldingRanges(uri: String, content: String): List<FoldingRange> = emptyList()

  /**
   * Performs the importses operation
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @return a collection of text edits
   */
  suspend fun organizeImports(uri: String): List<TextEdit>

  /**
   * Retrieves the import choiceses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @return a collection of import choice groups
   */
  suspend fun getImportChoices(uri: String): List<ImportChoiceGroup> = emptyList()

  /**
   * Processes the chosen importses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param uri the uri text
   * @param chosen the collection of chosen
   * @return a collection of text edits
   */
  suspend fun applyChosenImports(uri: String, chosen: List<ImportCandidate>): List<TextEdit> =
      emptyList()

  /**
   * Processes the command
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param command the [LspCommand] instance
   * @return `true` if the operation succeeded or condition is met; `false` otherwise
   */
  suspend fun executeCommand(command: LspCommand): Boolean = false

  /**
   * Processes the command for import editses
   *
   * This is a suspend function and can only be called from a coroutine.
   *
   * @param command the [LspCommand] instance
   * @param uri the uri text
   * @return a collection of text edits
   */
  suspend fun executeCommandForImportEdits(command: LspCommand, uri: String): List<TextEdit> =
      emptyList()
}

/** Class WorkspaceEditApplierSink. */
interface WorkspaceEditApplierSink {

  /**
   * Sets the workspace edit applier
   *
   * @param applier the callback function for applier, or `null` if omitted
   */
  fun setWorkspaceEditApplier(applier: ((WorkspaceEdit) -> Unit)?)
}

/** Data class ImportCandidate. */
data class ImportCandidate(val title: String, val packagePath: String, val commandData: String)

/** Data class ImportChoiceGroup. */
data class ImportChoiceGroup(val symbol: String, val candidates: List<ImportCandidate>)

/** Data class CompletionContext. */
data class CompletionContext(
    val triggerKind: CompletionTriggerKind,
    val triggerCharacter: String? = null,
)

/** Enum class CompletionTriggerKind. */
enum class CompletionTriggerKind(val value: Int) {

  /** Class INVOKED. */
  INVOKED(1),

  /** Class TRIGGER_CHARACTER. */
  TRIGGER_CHARACTER(2),

  /** Class TRIGGER_FOR_INCOMPLETE_COMPLETIONS. */
  TRIGGER_FOR_INCOMPLETE_COMPLETIONS(3);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the completion trigger kind
     */
    fun fromValue(value: Int): CompletionTriggerKind? = values().find { it.value == value }
  }
}

/** Data class CodeActionContext. */
data class CodeActionContext(
    val diagnostics: List<Diagnostic>,
    val only: List<CodeActionKind> = emptyList(),
)

/** Data class DocumentHighlight. */
data class DocumentHighlight(
    val range: Range,
    val kind: DocumentHighlightKind = DocumentHighlightKind.TEXT,
)

/** Enum class DocumentHighlightKind. */
enum class DocumentHighlightKind(val value: Int) {

  /** Class TEXT. */
  TEXT(1),

  /** Class READ. */
  READ(2),

  /** Class WRITE. */
  WRITE(3);

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the numeric value
     * @return the document highlight kind
     */
    fun fromValue(value: Int): DocumentHighlightKind? = values().find { it.value == value }
  }
}
