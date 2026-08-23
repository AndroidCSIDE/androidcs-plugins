package com.nullij.androidcodestudio.editor.models.lsp

import com.google.gson.JsonElement

/**
 * Represents a command attached to a completion item (LSP `Command` type).
 *
 * The JetBrains Kotlin LSP uses `jetbrains.kotlin.completion.apply` here instead
 * of putting the insert text inside `textEdit.newText`.  When this field is set,
 * the client must execute the command via `workspace/executeCommand` after the user
 * selects the item; the server then sends back a `workspace/applyEdit` request
 * containing the actual text insertion + any required imports.
 */
data class LspCommand(
    /** Human-readable title shown in the UI (e.g. "Apply Completion") */
    val title: String,
    /** The command identifier (e.g. "jetbrains.kotlin.completion.apply") */
    val command: String,
    /** Arguments forwarded verbatim to workspace/executeCommand */
    val arguments: List<JsonElement> = emptyList()
)