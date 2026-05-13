/*
 *  This file is part of ClangLanguageServer.
 *
 *  ClangLanguageServer is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ClangLanguageServer is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ClangLanguageServer.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.nullij.plugins.lsp

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nullij.androidcodestudio.editor.models.lsp.*
import com.nullij.androidcodestudio.plugins.api.LanguageServerClient
import kotlinx.coroutines.*

/**
 * LSP client for clangd.
 *
 * Sends LSP requests to [ClangLanguageServerManager] and parses the JSON responses into the IDE's
 * LSP model types.
 *
 * clangd supports:
 * - textDocument/completion (members, functions, macros, includes, snippets)
 * - textDocument/hover (type info, documentation)
 * - textDocument/signatureHelp (parameter hints)
 * - textDocument/definition (go-to definition)
 * - textDocument/references (find all usages)
 * - textDocument/documentSymbol (classes, functions, variables)
 * - textDocument/documentHighlight (highlight symbol usages)
 * - textDocument/formatting (clang-format)
 * - textDocument/rangeFormatting
 * - textDocument/rename (symbol rename)
 * - textDocument/codeAction (quick fixes, include insertion)
 * - textDocument/inlayHint (parameter names, deduced types)
 * - textDocument/publishDiagnostics (errors/warnings, pushed)
 *
 * @author nullij @ https://github.com/nullij
 */
class ClangLanguageClient(private val manager: ClangLanguageServerManager) :
    LanguageServerClient, LanguageClient {

    private val gson = Gson()
    private val tag = "ClangLSP"

    override suspend fun getCompletions(
        uri: String,
        position: Position,
        context: CompletionContext,
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
            val result = response?.get("result") ?: return emptyList()
            if (result.isJsonNull) return emptyList()

            val itemsArray = when {
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
                        kind = obj.get("kind")?.asInt?.let { CompletionItemKind.fromValue(it) }
                            ?: CompletionItemKind.TEXT,
                        detail = obj.get("detail")?.takeIf { !it.isJsonNull }?.asString,
                        documentation = obj.get("documentation")
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
                        insertText = obj.get("insertText")?.takeIf { !it.isJsonNull }?.asString
                            ?: obj.get("label")?.asString,
                        command = null,
                        textEdit = obj.get("textEdit")
                            ?.takeIf { it.isJsonObject }
                            ?.let { parseTextEdit(it.asJsonObject) },
                        additionalTextEdits = obj.get("additionalTextEdits")
                            ?.takeIf { it.isJsonArray }
                            ?.asJsonArray
                            ?.mapNotNull { parseTextEdit(it.asJsonObject) } ?: emptyList(),
                        data = obj.get("data")?.takeIf { !it.isJsonNull },
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getHover(uri: String, position: Position): Hover? {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("position", position.toJson())
            }

            val response = manager.sendRequest("textDocument/hover", params)
            val result = response?.get("result")?.takeIf { !it.isJsonNull } ?: return null
            val obj = result.asJsonObject
            val contents = obj.get("contents") ?: return null

            val markedStrings: List<MarkedString> = when {
                contents.isJsonArray -> contents.asJsonArray.map { el ->
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
                contents.isJsonObject -> {
                    val o = contents.asJsonObject
                    val kind = o.get("kind")?.asString
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
            null
        }
    }

    override suspend fun getSignatureHelp(uri: String, position: Position): SignatureHelp? {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("position", position.toJson())
            }

            val response = manager.sendRequest("textDocument/signatureHelp", params)
            val result = response?.get("result")?.takeIf { !it.isJsonNull } ?: return null
            val obj = result.asJsonObject

            val signatures = obj.getAsJsonArray("signatures")?.mapNotNull { el ->
                try {
                    val sig = el.asJsonObject
                    SignatureInformation(
                        label = sig.get("label")?.asString ?: return@mapNotNull null,
                        documentation = sig.get("documentation")
                            ?.takeIf { !it.isJsonNull }
                            ?.let { doc ->
                                when {
                                    doc.isJsonPrimitive -> doc.asString
                                    doc.isJsonObject -> doc.asJsonObject.get("value")?.asString
                                    else -> null
                                }
                            },
                        parameters = sig.getAsJsonArray("parameters")?.mapNotNull { pel ->
                            try {
                                val p = pel.asJsonObject
                                val labelEl = p.get("label")
                                val labelStr = when {
                                    labelEl.isJsonPrimitive -> labelEl.asString
                                    labelEl.isJsonArray -> {
                                        val arr = labelEl.asJsonArray
                                        "${arr[0].asInt}-${arr[1].asInt}"
                                    }
                                    else -> ""
                                }
                                ParameterInformation(label = labelStr)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: emptyList(),
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

            val activeSignature = obj.get("activeSignature")?.takeIf { !it.isJsonNull }?.asInt ?: 0
            val activeParameter = obj.get("activeParameter")?.takeIf { !it.isJsonNull }?.asInt ?: 0

            SignatureHelp(
                signatures = signatures,
                activeSignature = activeSignature,
                activeParameter = activeParameter,
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getDefinition(uri: String, position: Position): List<Location> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("position", position.toJson())
            }

            val response = manager.sendRequest("textDocument/definition", params)
            val result = response?.get("result")?.takeIf { !it.isJsonNull } ?: return emptyList()

            val array = when {
                result.isJsonArray -> result.asJsonArray
                result.isJsonObject -> JsonArray().apply { add(result) }
                else -> return emptyList()
            }

            array.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    Location(
                        uri = obj.get("uri")?.asString ?: return@mapNotNull null,
                        range = parseRange(obj.getAsJsonObject("range")),
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
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
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    Location(
                        uri = obj.get("uri")?.asString ?: return@mapNotNull null,
                        range = parseRange(obj.getAsJsonObject("range")),
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getDocumentSymbols(uri: String): List<DocumentSymbol> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
            }

            val response = manager.sendRequest("textDocument/documentSymbol", params)
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    DocumentSymbol(
                        name = obj.get("name")?.asString ?: return@mapNotNull null,
                        kind = SymbolKind.fromValue(obj.get("kind")?.asInt ?: 1)
                            ?: SymbolKind.FILE,
                        range = parseRange(obj.getAsJsonObject("range")),
                        selectionRange = obj.getAsJsonObject("selectionRange")
                            ?.let { parseRange(it) }
                            ?: parseRange(obj.getAsJsonObject("range")),
                        detail = obj.get("detail")?.takeIf { !it.isJsonNull }?.asString,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun formatDocument(uri: String, content: String): List<TextEdit> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("options", JsonObject().apply {
                    addProperty("tabSize", 4)
                    addProperty("insertSpaces", true)
                })
            }

            val response = manager.sendRequest("textDocument/formatting", params)
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try { parseTextEdit(el.asJsonObject) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun formatRange(uri: String, range: Range, content: String): List<TextEdit> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("range", range.toJson())
                add("options", JsonObject().apply {
                    addProperty("tabSize", 4)
                    addProperty("insertSpaces", true)
                })
            }

            val response = manager.sendRequest("textDocument/rangeFormatting", params)
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try { parseTextEdit(el.asJsonObject) } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getCodeActions(
        uri: String,
        range: Range,
        context: CodeActionContext,
    ): List<CodeAction> {
        return try {
            val diagnosticsArray = JsonArray().apply {
                context.diagnostics.forEach { diag ->
                    add(JsonObject().apply {
                        add("range", diag.range.toJson())
                        addProperty("message", diag.message)
                        diag.severity?.let { addProperty("severity", it.value) }
                        diag.source?.let { addProperty("source", it) }
                        diag.code?.let { addProperty("code", it) }
                    })
                }
            }

            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("range", range.toJson())
                add("context", JsonObject().apply { add("diagnostics", diagnosticsArray) })
            }

            val response = manager.sendRequest("textDocument/codeAction", params)
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val title = obj.get("title")?.asString ?: return@mapNotNull null
                    val kind = obj.get("kind")
                        ?.takeIf { !it.isJsonNull }
                        ?.asString
                        ?.let { CodeActionKind.fromValue(it) }
                    CodeAction(
                        title = title,
                        kind = kind,
                        diagnostics = emptyList(),
                        edit = null,
                        command = null,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun rename(uri: String, position: Position, newName: String): WorkspaceEdit? {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("position", position.toJson())
                addProperty("newName", newName)
            }

            val response = manager.sendRequest("textDocument/rename", params)
            val result = response?.get("result")?.takeIf { !it.isJsonNull } ?: return null
            val obj = result.asJsonObject

            val changes = mutableMapOf<String, List<TextEdit>>()
            obj.getAsJsonObject("changes")?.entrySet()?.forEach { (fileUri, editsEl) ->
                val edits = editsEl.asJsonArray.mapNotNull { parseTextEdit(it.asJsonObject) }
                changes[fileUri] = edits
            }

            WorkspaceEdit(changes = changes)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getDocumentHighlights(
        uri: String,
        position: Position,
    ): List<DocumentHighlight> {
        return try {
            val params = JsonObject().apply {
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
                        kind = DocumentHighlightKind.fromValue(obj.get("kind")?.asInt ?: 1)
                            ?: DocumentHighlightKind.TEXT,
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getInlayHints(uri: String, range: Range): List<InlayHint> {
        return try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply { addProperty("uri", uri) })
                add("range", range.toJson())
            }

            val response = manager.sendRequest("textDocument/inlayHint", params)
            val result = response?.get("result")?.takeIf { it.isJsonArray } ?: return emptyList()

            result.asJsonArray.mapNotNull { el ->
                try {
                    val obj = el.asJsonObject
                    val position = obj.getAsJsonObject("position")?.let {
                        Position(it.get("line").asInt, it.get("character").asInt)
                    } ?: return@mapNotNull null
                    val label = obj.get("label")?.let {
                        when {
                            it.isJsonPrimitive -> it.asString
                            it.isJsonArray -> it.asJsonArray
                                .mapNotNull { part -> part.asJsonObject.get("value")?.asString }
                                .joinToString("")
                            else -> null
                        }
                    } ?: return@mapNotNull null
                    val kind = obj.get("kind")
                        ?.takeIf { !it.isJsonNull }
                        ?.asInt
                        ?.let { InlayHintKind.fromValue(it) }
                    null // TODO: construct InlayHintLabel correctly once its API is known
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun organizeImports(uri: String): List<TextEdit> = emptyList()

    override suspend fun getDiagnostics(uri: String, content: String): List<Diagnostic> {
        val cached = manager.getCachedDiagnostics(uri) ?: return emptyList()
        return cached.mapNotNull { el ->
            try {
                val obj = el.asJsonObject
                val range = parseRange(obj.getAsJsonObject("range") ?: return@mapNotNull null)
                val message = obj.get("message")?.asString ?: return@mapNotNull null
                val severity = DiagnosticSeverity.fromValue(obj.get("severity")?.asInt ?: 1)
                    ?: DiagnosticSeverity.ERROR
                val code = obj.get("code")
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

    private fun Position.toJson(): JsonObject = JsonObject().apply {
        addProperty("line", line)
        addProperty("character", character)
    }

    private fun Range.toJson(): JsonObject = JsonObject().apply {
        add("start", start.toJson())
        add("end", end.toJson())
    }
}