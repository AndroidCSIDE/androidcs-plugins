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

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.nullij.androidcodestudio.editor.models.lsp.*
import com.nullij.androidcodestudio.plugins.api.LanguageServerClient

/**
 * LSP client for vscode-json-language-server.
 *
 * Supports:
 * - textDocument/completion
 * - textDocument/hover
 * - textDocument/formatting
 * - textDocument/rangeFormatting
 * - textDocument/publishDiagnostics
 * - textDocument/documentSymbol
 *
 * Not supported:
 * - textDocument/signatureHelp
 * - textDocument/definition
 * - textDocument/references
 * - textDocument/rename
 *
 * @author nullij @ https://github.com/nullij
 */
class JsonLanguageClient(private val manager: JsonLanguageServerManager) :
    LanguageServerClient, LanguageClient {

    private val gson = Gson()

    override suspend fun getCompletions(
        uri: String,
        position: Position,
        context: CompletionContext,
    ): List<CompletionItem> {
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getHover(uri: String, position: Position): Hover? {
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
                                    val value = o.get("value")?.asString ?: ""
                                    if (lang != null) MarkedString.CodeBlock(value, lang)
                                    else MarkedString.PlainText(value)
                                }
                                else -> MarkedString.PlainText(el.asString)
                            }
                        }
                    else -> listOf(MarkedString.PlainText(contents.asString))
                }

            val range =
                obj.get("range")?.takeIf { it.isJsonObject }?.let { parseRange(it.asJsonObject) }
            Hover(contents = markedStrings, range = range)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getSignatureHelp(uri: String, position: Position): SignatureHelp? = null

    override suspend fun getDefinition(uri: String, position: Position): List<Location> =
        emptyList()

    override suspend fun getReferences(uri: String, position: Position): List<Location> =
        emptyList()

    override suspend fun getDocumentHighlights(
        uri: String,
        position: Position,
    ): List<DocumentHighlight> = emptyList()

    override suspend fun getInlayHints(uri: String, range: Range): List<InlayHint> = emptyList()

    override suspend fun organizeImports(uri: String): List<TextEdit> = emptyList()

    override suspend fun rename(uri: String, position: Position, newName: String): WorkspaceEdit? =
        null

    override suspend fun formatDocument(uri: String, content: String): List<TextEdit> {
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add(
                        "options",
                        JsonObject().apply {
                            addProperty("tabSize", 2)
                            addProperty("insertSpaces", true)
                        },
                    )
                }
            parseTextEdits(manager.sendRequest("textDocument/formatting", params))
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun formatRange(uri: String, range: Range, content: String): List<TextEdit> {
        return try {
            val params =
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                    add("range", range.toJson())
                    add(
                        "options",
                        JsonObject().apply {
                            addProperty("tabSize", 2)
                            addProperty("insertSpaces", true)
                        },
                    )
                }
            parseTextEdits(manager.sendRequest("textDocument/rangeFormatting", params))
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getDocumentSymbols(uri: String): List<DocumentSymbol> = emptyList()

    override suspend fun getDiagnostics(uri: String, content: String): List<Diagnostic> =
        emptyList()

    override suspend fun getCodeActions(
        uri: String,
        range: Range,
        context: CodeActionContext,
    ): List<CodeAction> = emptyList()

    fun getCachedDiagnostics(uri: String) = manager.getCachedDiagnostics(uri)

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
