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
 * Data class CodeAction.
 *
 * @author nullij @ https://github.com/nullij
 */
data class CodeAction(
    val title: String,
    val kind: CodeActionKind? = null,
    val diagnostics: List<Diagnostic> = emptyList(),
    val isPreferred: Boolean = false,
    val edit: WorkspaceEdit? = null,
    val command: Command? = null,
    val disabled: Reason? = null,
    val data: Any? = null,
) {

  /** Data class Reason. */
  data class Reason(val reason: String)

  /** Companion object Companion. */
  companion object {

    /**
     * Performs the operation
     *
     * @param title the title text
     * @param diagnostic the diagnostic as a [Diagnostic]
     * @param edit the [WorkspaceEdit] instance
     * @param isPreferred whether is preferred is enabled
     * @return the code action
     */
    fun quickFix(
        title: String,
        diagnostic: Diagnostic,
        edit: WorkspaceEdit,
        isPreferred: Boolean = false,
    ): CodeAction {
      return CodeAction(
          title = title,
          kind = CodeActionKind.QUICK_FIX,
          diagnostics = listOf(diagnostic),
          edit = edit,
          isPreferred = isPreferred,
      )
    }

    /**
     * Performs the operation
     *
     * @param title the title text
     * @param edit the [WorkspaceEdit] instance
     * @param kind the [CodeActionKind] instance
     * @return the code action
     */
    fun refactor(
        title: String,
        edit: WorkspaceEdit,
        kind: CodeActionKind = CodeActionKind.REFACTOR,
    ): CodeAction {
      return CodeAction(title = title, kind = kind, edit = edit)
    }

    /**
     * Represents the source
     *
     * @param title the title text
     * @param command the command as a [Command]
     * @param kind the [CodeActionKind] instance
     * @return the code action
     */
    fun source(
        title: String,
        command: Command,
        kind: CodeActionKind = CodeActionKind.SOURCE,
    ): CodeAction {
      return CodeAction(title = title, kind = kind, command = command)
    }
  }
}

/** Enum class CodeActionKind. */
enum class CodeActionKind(val value: String) {

  /** Class EMPTY. */
  EMPTY(""),

  /** Class QUICK_FIX. */
  QUICK_FIX("quickfix"),

  /** Class REFACTOR. */
  REFACTOR("refactor"),

  /** Class REFACTOR_EXTRACT. */
  REFACTOR_EXTRACT("refactor.extract"),

  /** Class REFACTOR_INLINE. */
  REFACTOR_INLINE("refactor.inline"),

  /** Class REFACTOR_REWRITE. */
  REFACTOR_REWRITE("refactor.rewrite"),

  /** Class SOURCE. */
  SOURCE("source"),

  /** Class SOURCE_ORGANIZE_IMPORTS. */
  SOURCE_ORGANIZE_IMPORTS("source.organizeImports"),

  /** Class SOURCE_FIX_ALL. */
  SOURCE_FIX_ALL("source.fixAll"),

  /** Class SOURCE_REMOVE_UNUSED_IMPORTS. */
  SOURCE_REMOVE_UNUSED_IMPORTS("source.removeUnusedImports"),

  /** Class SOURCE_ADD_MISSING_IMPORTS. */
  SOURCE_ADD_MISSING_IMPORTS("source.addMissingImports");

  /** Companion object Companion. */
  companion object {

    /**
     * Retrieves the value
     *
     * @param value the value text
     * @return the code action kind
     */
    fun fromValue(value: String): CodeActionKind? {
      return values().find { it.value == value }
    }
  }
}

/** Data class Command. */
data class Command(
    val title: String,
    val command: String,
    val arguments: List<Any> = emptyList(),
)

/** Data class WorkspaceEdit. */
data class WorkspaceEdit(
    val changes: Map<String, List<TextEdit>> = emptyMap(),
    val documentChanges: List<TextDocumentEdit> = emptyList(),
)

/** Data class TextDocumentEdit. */
data class TextDocumentEdit(
    val textDocument: VersionedTextDocumentIdentifier,
    val edits: List<TextEdit>,
)

/** Data class VersionedTextDocumentIdentifier. */
data class VersionedTextDocumentIdentifier(
    val uri: String,
    val version: Int?,
)

/** Data class TextEdit. */
data class TextEdit(
    val range: Range,
    val newText: String,
) {

  /** Companion object Companion. */
  companion object {

    /**
     * Adds
     *
     * @param position the position as a [Position]
     * @param text the text text
     * @return the text edit
     */
    fun insert(position: Position, text: String): TextEdit {
      return TextEdit(Range(position, position), text)
    }

    /**
     * Removes the specified
     *
     * @param range the range as a [Range]
     * @return the text edit
     */
    fun delete(range: Range): TextEdit {
      return TextEdit(range, "")
    }

    /**
     * Sets
     *
     * @param range the range as a [Range]
     * @param text the text text
     * @return the text edit
     */
    fun replace(range: Range, text: String): TextEdit {
      return TextEdit(range, text)
    }
  }
}
