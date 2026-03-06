/*
 *  This file is part of BashLanguageServer.
 *
 *  BashLanguageServer is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  BashLanguageServer is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with BashLanguageServer.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.nullij.plugins.lsp

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nullij.androidcodestudio.editor.models.lsp.*
import kotlinx.coroutines.*

/**
 * LSP client for bash-language-server.
 *
 * Sends LSP requests to [BashLanguageServerManager] and parses the JSON
 * responses into the IDE's LSP model types. The implementation follows the
 * same pattern as PythonLanguageClient.
 *
 * bash-language-server supports:
 *   - textDocument/completion
 *   - textDocument/hover
 *   - textDocument/definition
 *   - textDocument/references
 *   - textDocument/documentSymbol
 *   - textDocument/documentHighlight
 *   - textDocument/publishDiagnostics (pushed, not polled)
 *
 * Features not supported by bash-language-server (returns empty):
 *   - textDocument/signatureHelp
 *   - textDocument/formatting / rangeFormatting
 *   - textDocument/rename
 *   - textDocument/inlayHint
 *   - textDocument/codeAction
 *
 * @author nullij @ https://github.com/nullij
 */
class BashLanguageClient(
    private val manager: BashLanguageServerManager
) : LanguageClient {

    private val gson = Gson()
    private val tag  = "BashClient"

    // ─── Completion ───────────────────────────────────────────────────────────

    override suspend fun getCompletions(
        uri: String,
        position: Position,
        context: CompletionContext
    ): List<CompletionItem> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("position", position.toJson())
                add("context", JsonObject().apply {
                    addProperty("triggerKind", context.triggerKind.value)
                    context.triggerCharacter?.let { addProperty("triggerCharacter", it) }
                })
            }

            val response = manager.sendRequest("textDocument/completion", params)
            val result   = response?.get("result") ?: return emptyList()
            if (result.isJsonNull) return emptyList()

            val itemsArray = when {
                result.isJsonArray  -> result.asJsonArray
                result.isJsonObject -> result.asJsonObject
                    .takeIf { it.has("items") }
                    ?.get("items")?.asJsonArray
                    ?: return emptyList()
                else -> return emptyList()
            }

            Log.d(tag, "Parsing ${itemsArray.size()} completion items")

            val items = itemsArray.mapNotNull { element ->
                try {
                    val obj = element.asJsonObject
                    CompletionItem(
                        label      = obj.get("label")?.asString ?: return@mapNotNull null,
                        kind       = obj.get("kind")?.asInt
                            ?.let { CompletionItemKind.fromValue(it) }
                            ?: CompletionItemKind.TEXT,
                        detail     = obj.get("detail")?.takeIf { !it.isJsonNull }?.asString,
                        documentation = obj.get("documentation")
                            ?.takeIf { !it.isJsonNull }?.let { doc ->
                                when {
                                    doc.isJsonPrimitive -> doc.asString
                                    doc.isJsonObject    -> doc.asJsonObject
                                        .get("value")?.asString
                                    else -> null
                                }
                            },
                        sortText   = obj.get("sortText")?.takeIf { !it.isJsonNull }?.asString,
                        filterText = obj.get("filterText")?.takeIf { !it.isJsonNull }?.asString,
                        insertText = obj.get("insertText")?.takeIf { !it.isJsonNull }?.asString
                            ?: obj.get("label")?.asString,
                        additionalTextEdits = obj.get("additionalTextEdits")
                            ?.takeIf { it.isJsonArray }
                            ?.asJsonArray
                            ?.mapNotNull { parseTextEdit(it.asJsonObject) }
                            ?: emptyList(),
                        data = obj.get("data")?.takeIf { !it.isJsonNull }
                    )
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse completion item", e)
                    null
                }
            }

            Log.d(tag, "✅ Returning ${items.size} completion items")
            items
        } catch (e: Exception) {
            Log.e(tag, "getCompletions error", e)
            emptyList()
        }
    }

    // ─── Hover ────────────────────────────────────────────────────────────────

    override suspend fun getHover(uri: String, position: Position): Hover? {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("position", position.toJson())
            }

            val response = manager.sendRequest("textDocument/hover", params)
            val result   = response?.get("result")?.takeIf { !it.isJsonNull } ?: return null
            val obj      = result.asJsonObject
            val contents = obj.get("contents") ?: return null

            val markedStrings: List<MarkedString> = when {
                contents.isJsonArray  -> contents.asJsonArray.map { el ->
                    when {
                        el.isJsonObject -> {
                            val o    = el.asJsonObject
                            val lang  = o.get("language")?.asString
                            val value = o.get("value")?.asString ?: ""
                            if (lang != null) MarkedString.CodeBlock(value, lang)
                            else MarkedString.PlainText(value)
                        }
                        else -> MarkedString.PlainText(el.asString)
                    }
                }
                contents.isJsonObject -> {
                    val o     = contents.asJsonObject
                    val kind  = o.get("kind")?.asString
                    val value = o.get("value")?.asString ?: ""
                    listOf(
                        if (kind == "markdown") MarkedString.MarkdownString(value)
                        else MarkedString.PlainText(value)
                    )
                }
                else -> listOf(MarkedString.PlainText(contents.asString))
            }

            val range = obj.get("range")?.takeIf { it.isJsonObject }
                ?.let { parseRange(it.asJsonObject) }

            Hover(contents = markedStrings, range = range)
        } catch (e: Exception) {
            Log.e(tag, "getHover error", e)
            null
        }
    }

    // ─── Signature help ───────────────────────────────────────────────────────
    // bash-language-server does not implement signatureHelp — return null.

    override suspend fun getSignatureHelp(uri: String, position: Position): SignatureHelp? = null

    // ─── Document symbols ─────────────────────────────────────────────────────

    override suspend fun getDocumentSymbols(uri: String): List<DocumentSymbol> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
            }

            val response = manager.sendRequest("textDocument/documentSymbol", params)
            val result   = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try {
                    parseDocumentSymbol(el.asJsonObject)
                } catch (e: Exception) {
                    Log.e(tag, "Failed to parse symbol", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "getDocumentSymbols error", e)
            emptyList()
        }
    }

    private fun parseDocumentSymbol(obj: JsonObject): DocumentSymbol {
        val children = obj.getAsJsonArray("children")
            ?.mapNotNull { runCatching { parseDocumentSymbol(it.asJsonObject) }.getOrNull() }
            ?: emptyList()

        return DocumentSymbol(
            name           = obj.get("name")?.asString ?: "",
            detail         = obj.get("detail")?.takeIf { !it.isJsonNull }?.asString,
            kind           = SymbolKind.fromValue(obj.get("kind")?.asInt ?: 13)
                ?: SymbolKind.VARIABLE,
            range          = parseRange(obj.getAsJsonObject("range")),
            selectionRange = parseRange(obj.getAsJsonObject("selectionRange")),
            children       = children
        )
    }

    // ─── Diagnostics ──────────────────────────────────────────────────────────
    //
    // bash-language-server pushes diagnostics via textDocument/publishDiagnostics
    // notifications rather than returning them on request. The manager's
    // handleNotification() receives those pushes. We return empty here so the IDE
    // doesn't poll — the cache is read instead.

    override suspend fun getDiagnostics(uri: String, content: String): List<Diagnostic> {
        val cached = manager.getCachedDiagnostics(uri) ?: return emptyList()
        return cached.mapNotNull { el ->
            try {
                val obj      = el.asJsonObject
                val range    = parseRange(obj.getAsJsonObject("range") ?: return@mapNotNull null)
                val message  = obj.get("message")?.asString ?: return@mapNotNull null
                val severity = DiagnosticSeverity.fromValue(obj.get("severity")?.asInt ?: 1)
                    ?: DiagnosticSeverity.ERROR
                val code     = obj.get("code")?.takeIf { !it.isJsonNull }?.let {
                    if (it.isJsonPrimitive) it.asString else null
                }
                Diagnostic(
                    range    = range,
                    message  = message,
                    severity = severity,
                    source   = obj.get("source")?.takeIf { !it.isJsonNull }?.asString,
                    code     = code
                )
            } catch (e: Exception) {
                Log.e(tag, "Failed to parse diagnostic", e)
                null
            }
        }
    }

    // ─── Code actions ─────────────────────────────────────────────────────────
    // bash-language-server does not implement codeAction — return empty.

    override suspend fun getCodeActions(
        uri: String,
        range: Range,
        context: CodeActionContext
    ): List<CodeAction> = emptyList()

    // ─── Definition / References ──────────────────────────────────────────────

    override suspend fun getDefinition(uri: String, position: Position): List<Location> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("position", position.toJson())
            }
            val response = manager.sendRequest("textDocument/definition", params)
            parseLocations(response)
        } catch (e: Exception) {
            Log.e(tag, "getDefinition error", e)
            emptyList()
        }
    }

    override suspend fun getReferences(uri: String, position: Position): List<Location> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("position", position.toJson())
                add("context", JsonObject().apply { addProperty("includeDeclaration", true) })
            }
            val response = manager.sendRequest("textDocument/references", params)
            parseLocations(response)
        } catch (e: Exception) {
            Log.e(tag, "getReferences error", e)
            emptyList()
        }
    }

    private fun parseLocations(response: JsonObject?): List<Location> {
        val result = response?.get("result") ?: return emptyList()
        val array  = when {
            result.isJsonArray  -> result.asJsonArray
            result.isJsonObject -> com.google.gson.JsonArray().also { it.add(result) }
            else -> return emptyList()
        }
        return array.mapNotNull { el ->
            try {
                val obj = el.asJsonObject
                Location(
                    uri   = obj.get("uri")?.asString ?: return@mapNotNull null,
                    range = parseRange(obj.getAsJsonObject("range"))
                )
            } catch (e: Exception) { null }
        }
    }

    // ─── Formatting ───────────────────────────────────────────────────────────
    // bash-language-server does not implement document formatting — return empty.

    override suspend fun formatDocument(uri: String, content: String): List<TextEdit> = emptyList()

    override suspend fun formatRange(uri: String, range: Range, content: String): List<TextEdit> =
        emptyList()

    // ─── Rename ───────────────────────────────────────────────────────────────
    // bash-language-server does not implement rename — return null.

    override suspend fun rename(uri: String, position: Position, newName: String): WorkspaceEdit? =
        null

    // ─── Document highlights ──────────────────────────────────────────────────

    override suspend fun getDocumentHighlights(
        uri: String,
        position: Position
    ): List<DocumentHighlight> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("position", position.toJson())
            }
            val response = manager.sendRequest("textDocument/documentHighlight", params)
            val result   = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    DocumentHighlight(
                        range = parseRange(obj.getAsJsonObject("range")),
                        kind  = DocumentHighlightKind.fromValue(obj.get("kind")?.asInt ?: 1)
                            ?: DocumentHighlightKind.TEXT
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e(tag, "getDocumentHighlights error", e)
            emptyList()
        }
    }

    // ─── Inlay hints ─────────────────────────────────────────────────────────
    // bash-language-server does not implement inlayHint — return empty.

    override suspend fun getInlayHints(uri: String, range: Range): List<InlayHint> = emptyList()

    // ─── Parse helpers ────────────────────────────────────────────────────────

    private fun parseTextEdit(obj: JsonObject): TextEdit? {
        val range   = obj.getAsJsonObject("range") ?: return null
        val newText = obj.get("newText")?.asString ?: return null
        return TextEdit(range = parseRange(range), newText = newText)
    }

    private fun parseRange(obj: JsonObject): Range {
        val start = obj.getAsJsonObject("start")
        val end   = obj.getAsJsonObject("end")
        return Range(
            start = Position(start.get("line").asInt, start.get("character").asInt),
            end   = Position(end.get("line").asInt,   end.get("character").asInt)
        )
    }

    private fun Position.toJson(): JsonObject = JsonObject().apply {
        addProperty("line",      line)
        addProperty("character", character)
    }

    private fun Range.toJson(): JsonObject = JsonObject().apply {
        add("start", start.toJson())
        add("end",   end.toJson())
    }
}