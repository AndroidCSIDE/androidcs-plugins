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
import com.nullij.androidcodestudio.editor.models.lsp.*
import com.nullij.androidcodestudio.plugins.api.LanguageServerClient
import kotlinx.coroutines.*

/**
 * LSP client for vscode-eslint-language-server.
 *
 * ESLint focuses on diagnostics and code actions (fix rules). Completion, hover, formatting, and
 * signature help are not supported.
 *
 * @author nullij @ https://github.com/nullij
 */
class EslintLanguageClient(private val manager: EslintLanguageServerManager) :
    LanguageServerClient, LanguageClient {

    private val gson = Gson()

    override suspend fun getCompletions(
        uri: String,
        position: Position,
        context: CompletionContext,
    ): List<CompletionItem> = emptyList()

    override suspend fun getHover(uri: String, position: Position): Hover? = null

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

    override suspend fun formatDocument(uri: String, content: String): List<TextEdit> = emptyList()

    override suspend fun formatRange(uri: String, range: Range, content: String): List<TextEdit> =
        emptyList()

    override suspend fun rename(uri: String, position: Position, newName: String): WorkspaceEdit? =
        null

    override suspend fun getDocumentSymbols(uri: String): List<DocumentSymbol> = emptyList()

    override suspend fun getDiagnostics(uri: String, content: String): List<Diagnostic> =
        emptyList()

    override suspend fun getCodeActions(
        uri: String,
        range: Range,
        context: CodeActionContext,
    ): List<CodeAction> = emptyList()

    fun getCachedDiagnostics(uri: String) = manager.getCachedDiagnostics(uri)
}
