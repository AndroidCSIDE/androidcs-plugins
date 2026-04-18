/*
 *  This file is part of WebLanguageServers.
 *
 *  WebLanguageServers is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  WebLanguageServers is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with WebLanguageServers.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.nullij.plugins.lsp

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nullij.androidcodestudio.editor.models.lsp.*
import com.nullij.androidcodestudio.plugins.api.LanguageServerClient

/**
 * LSP client for typescript-language-server.
 *
 * Handles both TypeScript and JavaScript files; the active language is determined by the
 * URI/languageId passed at document open time.
 *
 * Supports:
 * - textDocument/completion
 * - textDocument/hover
 * - textDocument/signatureHelp
 * - textDocument/definition
 * - textDocument/references
 * - textDocument/documentHighlight
 * - textDocument/documentSymbol
 * - textDocument/formatting
 * - textDocument/rangeFormatting
 * - textDocument/rename
 * - textDocument/codeAction
 * - textDocument/inlayHint
 * - workspace/executeCommand (_typescript.organizeImports)
 * - textDocument/publishDiagnostics (pushed, not polled)
 *
 * @author nullij @ https://github.com/nullij
 */
class TypeScriptLanguageClient(private val manager: TypeScriptLanguageServerManager) :
    LanguageServerClient, LanguageClient {

    companion object {
        private const val TAG = "TSLSP-Client"
    }

    private val gson = Gson()

    // ─── Completion ───────────────────────────────────────────────────────────

    override suspend fun getCompletions(
        uri: String,
        position: Position,
        context: CompletionContext,
    ): List<CompletionItem> {
        Log.d(TAG, "getCompletions: $uri at $position")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("position", position.toJson())
                    add(
                        "context",
                        JsonObject().apply {
                            addProperty("triggerKind", context.triggerKind.value)
                            context.triggerCharacter?.let { addProperty("triggerCharacter", it) }
                        },
                    )
                }
            val response = manager.sendRequest("textDocument/completion", params)
            val result = response?.get("result") ?: return emptyList()
            if (result.isJsonNull) return emptyList()

            val itemsArray =
                when {
                    result.isJsonArray -> result.asJsonArray
                    result.isJsonObject ->
                        result.asJsonObject.takeIf { it.has("items") }?.get("items")?.asJsonArray
                            ?: return emptyList()
                    else -> return emptyList()
                }

            Log.d(TAG, "Got ${itemsArray.size()} completion items")
            itemsArray.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    CompletionItem(
                        label = obj.get("label")?.asString ?: return@mapNotNull null,
                        kind =
                            obj.get("kind")?.asInt?.let { CompletionItemKind.fromValue(it) }
                                ?: CompletionItemKind.TEXT,
                        detail = obj.get("detail")?.takeIf { !it.isJsonNull }?.asString,
                        documentation =
                            obj.get("documentation")
                                ?.takeIf { !it.isJsonNull }
                                ?.let { doc ->
                                    when {
                                        doc.isJsonPrimitive -> doc.asString
                                        doc.isJsonObject -> doc.asJsonObject.get("value")?.asString
                                        else -> null
                                    }
                                },
                        sortText = obj.get("sortText")?.takeIf { !it.isJsonNull }?.asString,
                        filterText = obj.get("filterText")?.takeIf { !it.isJsonNull }?.asString,
                        insertText =
                            obj.get("insertText")?.takeIf { !it.isJsonNull }?.asString
                                ?: obj.get("label")?.asString,
                        command = null,
                        textEdit =
                            obj.get("textEdit")
                                ?.takeIf { it.isJsonObject }
                                ?.let { parseTextEdit(it.asJsonObject) },
                        additionalTextEdits =
                            obj.get("additionalTextEdits")
                                ?.takeIf { it.isJsonArray }
                                ?.asJsonArray
                                ?.mapNotNull { parseTextEdit(it.asJsonObject) } ?: emptyList(),
                        data = obj.get("data")?.takeIf { !it.isJsonNull },
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCompletions failed", e)
            emptyList()
        }
    }

    // ─── Hover ────────────────────────────────────────────────────────────────

    override suspend fun getHover(uri: String, position: Position): Hover? {
        Log.d(TAG, "getHover: $uri at $position")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("position", position.toJson())
                }
            val response = manager.sendRequest("textDocument/hover", params)
            val result = response?.get("result")?.takeIf { !it.isJsonNull } ?: return null
            val obj = result.asJsonObject
            val contents = obj.get("contents") ?: return null

            val markedStrings: List<MarkedString> =
                when {
                    contents.isJsonObject -> {
                        val o = contents.asJsonObject
                        val kind = o.get("kind")?.asString
                        val value = o.get("value")?.asString ?: ""
                        listOf(
                            if (kind == "markdown") MarkedString.MarkdownString(value)
                            else MarkedString.PlainText(value)
                        )
                    }
                    contents.isJsonArray ->
                        contents.asJsonArray.map { el ->
                            when {
                                el.isJsonObject -> {
                                    val o = el.asJsonObject
                                    val lang = o.get("language")?.asString
                                    val v = o.get("value")?.asString ?: ""
                                    if (lang != null) MarkedString.CodeBlock(v, lang)
                                    else MarkedString.PlainText(v)
                                }
                                else -> MarkedString.PlainText(el.asString)
                            }
                        }
                    else -> listOf(MarkedString.PlainText(contents.asString))
                }

            val range =
                obj.get("range")?.takeIf { it.isJsonObject }?.let { parseRange(it.asJsonObject) }
            Log.d(TAG, "Hover result: ${markedStrings.size} marked strings")
            Hover(contents = markedStrings, range = range)
        } catch (e: Exception) {
            Log.e(TAG, "getHover failed", e)
            null
        }
    }

    // ─── Signature help ───────────────────────────────────────────────────────

    override suspend fun getSignatureHelp(uri: String, position: Position): SignatureHelp? {
        Log.d(TAG, "getSignatureHelp: $uri at $position")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("position", position.toJson())
                    add(
                        "context",
                        JsonObject().apply {
                            addProperty("triggerKind", 1) // Invoked
                            addProperty("isRetrigger", false)
                        },
                    )
                }
            val response = manager.sendRequest("textDocument/signatureHelp", params)
            val result = response?.get("result")?.takeIf { !it.isJsonNull } ?: return null
            val obj = result.asJsonObject

            val sigsArray = obj.getAsJsonArray("signatures") ?: return null
            val signatures =
                sigsArray.mapNotNull { el ->
                    try {
                        val s = el.asJsonObject
                        val label = s.get("label")?.asString ?: return@mapNotNull null
                        val doc =
                            s.get("documentation")
                                ?.takeIf { !it.isJsonNull }
                                ?.let {
                                    if (it.isJsonPrimitive) it.asString
                                    else it.asJsonObject.get("value")?.asString
                                }
                        val params2 =
                            s.getAsJsonArray("parameters")?.mapNotNull { p ->
                                try {
                                    val po = p.asJsonObject
                                    val pl = po.get("label") ?: return@mapNotNull null
                                    val plStr = if (pl.isJsonPrimitive) pl.asString else null
                                    val pdoc =
                                        po.get("documentation")
                                            ?.takeIf { !it.isJsonNull }
                                            ?.let {
                                                if (it.isJsonPrimitive) it.asString
                                                else it.asJsonObject.get("value")?.asString
                                            }
                                    plStr?.let {
                                        ParameterInformation(label = it, documentation = pdoc)
                                    }
                                } catch (_: Exception) {
                                    null
                                }
                            } ?: emptyList()
                        SignatureInformation(
                            label = label,
                            documentation = doc,
                            parameters = params2,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }

            if (signatures.isEmpty()) return null
            Log.d(TAG, "SignatureHelp: ${signatures.size} signatures")
            SignatureHelp(
                signatures = signatures,
                activeSignature = obj.get("activeSignature")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                activeParameter = obj.get("activeParameter")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "getSignatureHelp failed", e)
            null
        }
    }

    // ─── Document symbols ─────────────────────────────────────────────────────

    override suspend fun getDocumentSymbols(uri: String): List<DocumentSymbol> {
        Log.d(TAG, "getDocumentSymbols: $uri")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                }
            val response = manager.sendRequest("textDocument/documentSymbol", params)
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            Log.d(TAG, "Document symbols: ${result.asJsonArray.size()} items")
            result.asJsonArray.mapNotNull { el ->
                try {
                    parseDocumentSymbol(el.asJsonObject)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse document symbol", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDocumentSymbols failed", e)
            emptyList()
        }
    }

    private fun parseDocumentSymbol(obj: JsonObject): DocumentSymbol {
        val children =
            obj.getAsJsonArray("children")?.mapNotNull {
                runCatching { parseDocumentSymbol(it.asJsonObject) }.getOrNull()
            } ?: emptyList()
        return DocumentSymbol(
            name = obj.get("name")?.asString ?: "",
            detail = obj.get("detail")?.takeIf { !it.isJsonNull }?.asString,
            kind = SymbolKind.fromValue(obj.get("kind")?.asInt ?: 13) ?: SymbolKind.VARIABLE,
            range = parseRange(obj.getAsJsonObject("range")),
            selectionRange = parseRange(obj.getAsJsonObject("selectionRange")),
            children = children,
        )
    }

    // ─── Diagnostics ──────────────────────────────────────────────────────────
    // typescript-language-server pushes diagnostics via publishDiagnostics.
    // Read from the manager's cache rather than polling.

    override suspend fun getDiagnostics(uri: String, content: String): List<Diagnostic> {
        Log.d(TAG, "getDiagnostics: $uri")
        val cached =
            manager.getCachedDiagnostics(uri)
                ?: run {
                    Log.v(TAG, "No cached diagnostics for $uri")
                    return emptyList()
                }
        return cached
            .mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val range = parseRange(obj.getAsJsonObject("range") ?: return@mapNotNull null)
                    val message = obj.get("message")?.asString ?: return@mapNotNull null
                    val severity =
                        DiagnosticSeverity.fromValue(obj.get("severity")?.asInt ?: 1)
                            ?: DiagnosticSeverity.ERROR
                    val code =
                        obj.get("code")
                            ?.takeIf { !it.isJsonNull }
                            ?.let { if (it.isJsonPrimitive) it.asString else null }
                    Diagnostic(
                        range = range,
                        message = message,
                        severity = severity,
                        source = obj.get("source")?.takeIf { !it.isJsonNull }?.asString,
                        code = code,
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse diagnostic", e)
                    null
                }
            }
            .also { Log.d(TAG, "Returning ${it.size} diagnostics for $uri") }
    }

    // ─── Code actions ─────────────────────────────────────────────────────────

    override suspend fun getCodeActions(
        uri: String,
        range: Range,
        context: CodeActionContext,
    ): List<CodeAction> {
        Log.d(TAG, "getCodeActions: $uri, range=$range, ${context.diagnostics.size} diagnostics")
        return try {
            val diagnosticsArray =
                JsonArray().apply {
                    context.diagnostics.forEach { d ->
                        add(
                            JsonObject().apply {
                                add("range", d.range.toJson())
                                addProperty("message", d.message)
                                addProperty("severity", d.severity.value)
                                d.source?.let { addProperty("source", it) }
                                d.code?.let { addProperty("code", it) }
                            }
                        )
                    }
                }
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("range", range.toJson())
                    add(
                        "context",
                        JsonObject().apply {
                            add("diagnostics", diagnosticsArray)
                            add(
                                "only",
                                JsonArray().apply {
                                    add("quickfix")
                                    add("refactor")
                                    add("source")
                                },
                            )
                        },
                    )
                }
            val response = manager.sendRequest("textDocument/codeAction", params)
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val title = obj.get("title")?.asString ?: return@mapNotNull null
                    val kind = obj.get("kind")?.takeIf { !it.isJsonNull }?.asString

                    // Server may return a Command (has "command" + "arguments" at the top level)
                    // or a CodeAction (has "edit" and/or "command" nested).
                    val edit = obj.getAsJsonObject("edit")?.let { parseWorkspaceEdit(it) }
                    val command =
                        obj.getAsJsonObject("command")?.let { cmdObj ->
                            Command(
                                title = cmdObj.get("title")?.asString ?: title,
                                command = cmdObj.get("command")?.asString ?: return@let null,
                                arguments =
                                    cmdObj.getAsJsonArray("arguments")?.toList() ?: emptyList(),
                            )
                        }
                            ?: run {
                                // Top-level command shape (returned by some TS actions)
                                if (obj.has("command") && obj.get("command").isJsonPrimitive) {
                                    Command(
                                        title = title,
                                        command = obj.get("command").asString,
                                        arguments =
                                            obj.getAsJsonArray("arguments")?.toList() ?: emptyList(),
                                    )
                                } else null
                            }

                    CodeAction(
                        title = title,
                        kind = kind?.let { CodeActionKind.fromValue(it) },
                        edit = edit,
                        command = command,
                        isPreferred =
                            obj.get("isPreferred")?.takeIf { !it.isJsonNull }?.asBoolean ?: false,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCodeActions failed", e)
            emptyList()
        }
    }

    // ─── Definition / References ──────────────────────────────────────────────

    override suspend fun getDefinition(uri: String, position: Position): List<Location> {
        Log.d(TAG, "getDefinition: $uri at $position")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("position", position.toJson())
                }
            val locations = parseLocations(manager.sendRequest("textDocument/definition", params))
            Log.d(TAG, "getDefinition returned ${locations.size} locations")
            locations
        } catch (e: Exception) {
            Log.e(TAG, "getDefinition failed", e)
            emptyList()
        }
    }

    override suspend fun getReferences(uri: String, position: Position): List<Location> {
        Log.d(TAG, "getReferences: $uri at $position")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("position", position.toJson())
                    add("context", JsonObject().apply { addProperty("includeDeclaration", true) })
                }
            val locations = parseLocations(manager.sendRequest("textDocument/references", params))
            Log.d(TAG, "getReferences returned ${locations.size} locations")
            locations
        } catch (e: Exception) {
            Log.e(TAG, "getReferences failed", e)
            emptyList()
        }
    }

    private fun parseLocations(response: JsonObject?): List<Location> {
        val result = response?.get("result") ?: return emptyList()
        val array =
            when {
                result.isJsonArray -> result.asJsonArray
                result.isJsonObject -> JsonArray().also { it.add(result) }
                else -> return emptyList()
            }
        return array.mapNotNull { el ->
            try {
                val obj = el.asJsonObject
                Location(
                    uri = obj.get("uri")?.asString ?: return@mapNotNull null,
                    range = parseRange(obj.getAsJsonObject("range")),
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    // ─── Document highlights ──────────────────────────────────────────────────

    override suspend fun getDocumentHighlights(
        uri: String,
        position: Position,
    ): List<DocumentHighlight> {
        Log.d(TAG, "getDocumentHighlights: $uri at $position")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("position", position.toJson())
                }
            val response = manager.sendRequest("textDocument/documentHighlight", params)
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    DocumentHighlight(
                        range = parseRange(obj.getAsJsonObject("range")),
                        kind =
                            DocumentHighlightKind.fromValue(obj.get("kind")?.asInt ?: 1)
                                ?: DocumentHighlightKind.TEXT,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getDocumentHighlights failed", e)
            emptyList()
        }
    }

    // ─── Inlay hints ─────────────────────────────────────────────────────────

    override suspend fun getInlayHints(uri: String, range: Range): List<InlayHint> {
        Log.d(TAG, "getInlayHints: $uri, range=$range")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("range", range.toJson())
                }
            val response = manager.sendRequest("textDocument/inlayHint", params)
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val position =
                        obj.getAsJsonObject("position")?.let {
                            Position(it.get("line").asInt, it.get("character").asInt)
                        } ?: return@mapNotNull null
                    val labelEl = obj.get("label") ?: return@mapNotNull null
                    val label =
                        if (labelEl.isJsonPrimitive) labelEl.asString
                        else
                            labelEl.asJsonArray.joinToString("") { part ->
                                part.asJsonObject.get("value")?.asString ?: ""
                            }
                    InlayHint(
                        position = position,
                        label = InlayHintLabel.String(label),
                        kind =
                            InlayHintKind.fromValue(
                                obj.get("kind")?.takeIf { !it.isJsonNull }?.asInt ?: 0
                            ),
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ─── Formatting ───────────────────────────────────────────────────────────

    override suspend fun formatDocument(uri: String, content: String): List<TextEdit> {
        Log.d(TAG, "formatDocument: $uri, contentLength=${content.length}")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add(
                        "options",
                        JsonObject().apply {
                            addProperty("tabSize", 4)
                            addProperty("insertSpaces", true)
                        },
                    )
                }
            val edits = parseTextEdits(manager.sendRequest("textDocument/formatting", params))
            Log.d(TAG, "formatDocument returned ${edits.size} edits")
            edits
        } catch (e: Exception) {
            Log.e(TAG, "formatDocument failed", e)
            emptyList()
        }
    }

    override suspend fun formatRange(uri: String, range: Range, content: String): List<TextEdit> {
        Log.d(TAG, "formatRange: $uri, range=$range")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("range", range.toJson())
                    add(
                        "options",
                        JsonObject().apply {
                            addProperty("tabSize", 4)
                            addProperty("insertSpaces", true)
                        },
                    )
                }
            val edits = parseTextEdits(manager.sendRequest("textDocument/rangeFormatting", params))
            Log.d(TAG, "formatRange returned ${edits.size} edits")
            edits
        } catch (e: Exception) {
            Log.e(TAG, "formatRange failed", e)
            emptyList()
        }
    }

    private fun parseTextEdits(response: JsonObject?): List<TextEdit> {
        val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()
        return result.asJsonArray.mapNotNull { el ->
            try {
                parseTextEdit(el.asJsonObject)
            } catch (_: Exception) {
                null
            }
        }
    }

    // ─── Organize imports ─────────────────────────────────────────────────────
    // Uses the typescript-language-server's _typescript.organizeImports command.

    override suspend fun organizeImports(uri: String): List<TextEdit> {
        Log.d(TAG, "organizeImports: $uri")
        return try {
            val params =
                JsonObject().apply {
                    addProperty("command", "_typescript.organizeImports")
                    add("arguments", JsonArray().apply { add(uri) })
                }
            val edits = parseTextEdits(manager.sendRequest("workspace/executeCommand", params))
            Log.d(TAG, "organizeImports returned ${edits.size} edits")
            edits
        } catch (e: Exception) {
            Log.e(TAG, "organizeImports failed", e)
            emptyList()
        }
    }

    // ─── Rename ───────────────────────────────────────────────────────────────

    override suspend fun rename(uri: String, position: Position, newName: String): WorkspaceEdit? {
        Log.d(TAG, "rename: $uri at $position to '$newName'")
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("position", position.toJson())
                    addProperty("newName", newName)
                }
            val response = manager.sendRequest("textDocument/rename", params)
            val result = response?.get("result")?.takeIf { it.isJsonObject } ?: return null
            val edit = parseWorkspaceEdit(result.asJsonObject)
            Log.d(TAG, "rename result: ${edit?.changes?.size ?: 0} files affected")
            edit
        } catch (e: Exception) {
            Log.e(TAG, "rename failed", e)
            null
        }
    }

    // ─── Diagnostics cache ────────────────────────────────────────────────────

    fun getCachedDiagnostics(uri: String) = manager.getCachedDiagnostics(uri)

    // ─── Parse helpers ────────────────────────────────────────────────────────

    private fun parseWorkspaceEdit(obj: JsonObject): WorkspaceEdit? {
        val changes = mutableMapOf<String, List<TextEdit>>()

        // Standard "changes": { uri -> TextEdit[] }
        obj.getAsJsonObject("changes")?.entrySet()?.forEach { (fileUri, editsEl) ->
            if (editsEl.isJsonArray)
                changes[fileUri] =
                    editsEl.asJsonArray.mapNotNull { el ->
                        try {
                            parseTextEdit(el.asJsonObject)
                        } catch (_: Exception) {
                            null
                        }
                    }
        }

        // documentChanges: TextDocumentEdit[]
        obj.getAsJsonArray("documentChanges")?.forEach { el ->
            try {
                if (!el.isJsonObject) return@forEach
                val docEdit = el.asJsonObject
                val fileUri =
                    docEdit.getAsJsonObject("textDocument")?.get("uri")?.asString ?: return@forEach
                val edits =
                    docEdit.getAsJsonArray("edits")?.mapNotNull { e ->
                        try {
                            parseTextEdit(e.asJsonObject)
                        } catch (_: Exception) {
                            null
                        }
                    } ?: emptyList()
                changes[fileUri] = (changes[fileUri] ?: emptyList()) + edits
            } catch (_: Exception) {}
        }

        return if (changes.isEmpty()) null else WorkspaceEdit(changes = changes)
    }

    private fun parseTextEdit(obj: JsonObject): TextEdit? {
        val range = obj.getAsJsonObject("range") ?: return null
        val newText = obj.get("newText")?.asString ?: return null
        return TextEdit(range = parseRange(range), newText = newText)
    }

    private fun parseRange(obj: JsonObject): Range {
        val start = obj.getAsJsonObject("start")
        val end = obj.getAsJsonObject("end")
        return Range(
            start = Position(start.get("line").asInt, start.get("character").asInt),
            end = Position(end.get("line").asInt, end.get("character").asInt),
        )
    }

    private fun Position.toJson(): JsonObject =
        JsonObject().apply {
            addProperty("line", line)
            addProperty("character", character)
        }

    private fun Range.toJson(): JsonObject =
        JsonObject().apply {
            add("start", start.toJson())
            add("end", end.toJson())
        }
}
