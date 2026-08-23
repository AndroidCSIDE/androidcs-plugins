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
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nullij.androidcodestudio.plugins.api.LanguageServerClient
import com.nullij.androidcodestudio.plugins.api.PluginApi
import com.nullij.androidcodestudio.plugins.api.PluginLanguageServerSpec
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

/**
 * Manages the HTML LSP server (vscode-html-language-server) for ACS.
 *
 * Launched via: npx vscode-html-language-server --stdio
 *
 * Capabilities supported by vscode-html-language-server:
 * - textDocument/completion (tag names, attributes, attribute values)
 * - textDocument/hover (element/attribute documentation)
 * - textDocument/definition (go-to linked resource , limited)
 * - textDocument/documentSymbol (headings, id/class anchors)
 * - textDocument/documentHighlight
 * - textDocument/formatting
 * - textDocument/rangeFormatting
 * - textDocument/publishDiagnostics (pushed)
 *
 * Not supported (returns empty / null):
 * - textDocument/signatureHelp
 * - textDocument/references
 * - textDocument/rename
 * - textDocument/inlayHint
 * - textDocument/codeAction
 *
 * @author nullij @ https://github.com/nullij
 */
class HtmlLanguageServerManager : PluginLanguageServerSpec {

    private val gson = Gson()

    private var serverProcess: Process? = null
    private var writer: BufferedWriter? = null
    private var client: HtmlLanguageClient? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nextId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    @Volatile private var running = false
    @Volatile private var initializing = false

    private val diagnosticsCache = ConcurrentHashMap<String, JsonArray>()

    fun getCachedDiagnostics(uri: String): JsonArray? = diagnosticsCache[uri]

    companion object {
        private const val TAG = "HtmlLSP"
        private const val BUFFER_SIZE = 32768
        private const val MAX_CONTENT_LENGTH = 10_485_760
    }

    // ─── PluginLanguageServerSpec ─────────────────────────────────────────────

    override val languageId: String = "html"

    override fun start(): Boolean = runBlocking {
        if (running) {

            return@runBlocking true
        }

        initializing = true

        try {
            val binFile = File("$PREFIX/vscode-html-language-server")
            val nodeFile = File("$PREFIX/node")
            val pb = PluginApi.process.builder()

            val builder = if (nodeFile.exists() && binFile.exists()) {
                pb.command(nodeFile.absolutePath, binFile.absolutePath, "--stdio")
            } else if (binFile.exists()) {
                pb.command(binFile.absolutePath, "--stdio")
            } else {
                pb.command("vscode-html-language-server", "--stdio")
            }

            serverProcess = builder
                .withEnv(
                    mapOf(
                        "HOME" to "/data/data/com.acside/files/home",
                        "PATH" to "/data/data/com.acside/files/usr/bin:/data/data/com.acside/files/usr/bin/applets",
                        "LD_LIBRARY_PATH" to "/data/data/com.acside/files/usr/lib",
                        "PREFIX" to "/data/data/com.acside/files/usr",
                    )
                )
                .launch()

            writer =
                BufferedWriter(
                    OutputStreamWriter(serverProcess!!.outputStream, StandardCharsets.UTF_8),
                    BUFFER_SIZE,
                )

            startReaderThread()
            startErrorReaderThread()

            // Give the Node.js process a moment to boot before the handshake.
            delay(1200)

            val initialized = sendInitialize()

            return@runBlocking if (initialized) {
                client = HtmlLanguageClient(this@HtmlLanguageServerManager)
                running = true

                true
            } else {

                stopInternal()
                false
            }
        } catch (e: Exception) {

            stopInternal()
            false
        } finally {
            initializing = false
        }
    }

    override fun stop() = runBlocking { stopInternal() }

    override fun isRunning(): Boolean = running

    private suspend fun stopInternal(): Unit =
        withContext(Dispatchers.IO) {
            try {

                running = false

                try {
                    sendRequest("shutdown", JsonObject())
                    sendNotification("exit", JsonObject())
                    delay(400)
                } catch (e: Exception) {}

                try {
                    writer?.close()
                } catch (_: Exception) {}

                serverProcess?.destroy()
                serverProcess?.waitFor()
                serverProcess = null

                client = null
                pendingRequests.clear()
            } catch (e: Exception) {}
        }

    override fun getClient(): LanguageServerClient =
        client ?: error("getClient() called before server is started")

    fun destroy() {
        scope.cancel()
        stop()
    }

    // ─── LSP initialize handshake ─────────────────────────────────────────────

    private suspend fun sendInitialize(): Boolean {

        return try {
            val params =
                JsonObject().apply {
                    addProperty("processId", android.os.Process.myPid())
                    addProperty("rootUri", null as String?)

                    add(
                        "capabilities",
                        JsonObject().apply {
                            add(
                                "textDocument",
                                JsonObject().apply {
                                    add(
                                        "synchronization",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                            addProperty("willSave", false)
                                            addProperty("willSaveWaitUntil", false)
                                            addProperty("didSave", true)
                                        },
                                    )

                                    add(
                                        "completion",
                                        JsonObject().apply {
                                            add(
                                                "completionItem",
                                                JsonObject().apply {
                                                    addProperty("snippetSupport", true)
                                                    addProperty("deprecatedSupport", true)
                                                    addProperty("preselectSupport", true)
                                                    add(
                                                        "documentationFormat",
                                                        gson.toJsonTree(
                                                            arrayOf("markdown", "plaintext")
                                                        ),
                                                    )
                                                    add(
                                                        "resolveSupport",
                                                        gson.toJsonTree(
                                                            mapOf(
                                                                "properties" to
                                                                    arrayOf(
                                                                        "documentation",
                                                                        "detail",
                                                                        "additionalTextEdits",
                                                                    )
                                                            )
                                                        ),
                                                    )
                                                },
                                            )
                                            add(
                                                "completionItemKind",
                                                JsonObject().apply {
                                                    add(
                                                        "valueSet",
                                                        gson.toJsonTree((1..25).toList()),
                                                    )
                                                },
                                            )
                                            addProperty("contextSupport", true)
                                        },
                                    )

                                    add(
                                        "hover",
                                        JsonObject().apply {
                                            add(
                                                "contentFormat",
                                                gson.toJsonTree(arrayOf("markdown", "plaintext")),
                                            )
                                        },
                                    )

                                    add("documentHighlight", JsonObject())

                                    add(
                                        "documentSymbol",
                                        JsonObject().apply {
                                            addProperty("hierarchicalDocumentSymbolSupport", true)
                                        },
                                    )

                                    // HTML server supports formatting
                                    add("formatting", JsonObject())
                                    add("rangeFormatting", JsonObject())

                                    add(
                                        "publishDiagnostics",
                                        JsonObject().apply {
                                            addProperty("relatedInformation", true)
                                        },
                                    )
                                },
                            )

                            add(
                                "workspace",
                                JsonObject().apply {
                                    addProperty("applyEdit", true)
                                    add(
                                        "workspaceEdit",
                                        gson.toJsonTree(mapOf("documentChanges" to true)),
                                    )
                                    addProperty("workspaceFolders", false)
                                },
                            )
                        },
                    )

                    // vscode-html-language-server uses initializationOptions to
                    // control embedded language (CSS/JS) feature support.
                    add(
                        "initializationOptions",
                        JsonObject().apply {
                            add(
                                "embeddedLanguages",
                                JsonObject().apply {
                                    addProperty("css", true)
                                    addProperty("javascript", true)
                                },
                            )
                            add(
                                "configurationSection",
                                gson.toJsonTree(arrayOf("html", "css", "javascript")),
                            )
                        },
                    )
                }

            val response = sendRequest("initialize", params)
            if (response == null) {

                return false
            }

            sendNotification("initialized", JsonObject())

            true
        } catch (e: Exception) {

            false
        }
    }

    // ─── Document lifecycle ───────────────────────────────────────────────────

    override fun openDocument(file: File): Boolean {
        if (!running) return false
        val content =
            try {
                file.readText()
            } catch (e: Exception) {

                return false
            }
        scope.launch {
            sendNotification(
                "textDocument/didOpen",
                JsonObject().apply {
                    add(
                        "textDocument",
                        JsonObject().apply {
                            addProperty("uri", file.toUri())
                            addProperty("languageId", "html")
                            addProperty("version", 1)
                            addProperty("text", content)
                        },
                    )
                },
            )
        }
        return true
    }

    override fun closeDocument(file: File) {
        if (!running) return
        scope.launch {
            sendNotification(
                "textDocument/didClose",
                JsonObject().apply {
                    add("textDocument", JsonObject().apply { addProperty("uri", file.toUri()) })
                },
            )
        }
    }

    override fun documentChanged(file: File, content: String, version: Int) {
        if (!running) return
        scope.launch {
            sendNotification(
                "textDocument/didChange",
                JsonObject().apply {
                    add(
                        "textDocument",
                        JsonObject().apply {
                            addProperty("uri", file.toUri())
                            addProperty("version", version)
                        },
                    )
                    add(
                        "contentChanges",
                        JsonArray().apply {
                            add(
                                JsonObject().apply {
                                    // Full document sync — matches textDocumentSync: Full
                                    addProperty("text", content)
                                }
                            )
                        },
                    )
                },
            )
        }
    }

    // ─── JSON-RPC transport ───────────────────────────────────────────────────

    internal suspend fun sendRequest(method: String, params: JsonObject): JsonObject? {
        return withContext(Dispatchers.IO) {
            try {
                val id = nextId.getAndIncrement()
                val deferred = CompletableDeferred<JsonObject>()
                pendingRequests[id] = deferred

                val request =
                    JsonObject().apply {
                        addProperty("jsonrpc", "2.0")
                        addProperty("id", id)
                        addProperty("method", method)
                        add("params", params)
                    }

                writeMessage(gson.toJson(request))

                withTimeout(30_000) { deferred.await() }
            } catch (e: TimeoutCancellationException) {

                null
            } catch (e: Exception) {

                null
            }
        }
    }

    internal fun sendNotification(method: String, params: JsonObject) {
        try {
            val notification =
                JsonObject().apply {
                    addProperty("jsonrpc", "2.0")
                    addProperty("method", method)
                    add("params", params)
                }
            writeMessage(gson.toJson(notification))
        } catch (e: Exception) {}
    }

    private fun writeMessage(content: String) {
        val currentWriter = writer ?: return
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val message = "Content-Length: ${bytes.size}\r\n\r\n$content"
        synchronized(currentWriter) {
            currentWriter.write(message)
            currentWriter.flush()
        }
    }

    // ─── Reader threads ───────────────────────────────────────────────────────

    private fun startReaderThread() {
        Thread(
                {
                    try {
                        val inputStream = serverProcess!!.inputStream
                        while (running || initializing) {
                            var contentLength = -1

                            while (true) {
                                val line = readLineFromStream(inputStream) ?: return@Thread
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:")) {
                                    contentLength = line.substring(15).trim().toInt()
                                }
                            }

                            if (contentLength <= 0 || contentLength > MAX_CONTENT_LENGTH) continue

                            val buffer = ByteArray(contentLength)
                            var totalRead = 0
                            while (totalRead < contentLength) {
                                val read =
                                    inputStream.read(buffer, totalRead, contentLength - totalRead)
                                if (read < 0) break
                                totalRead += read
                            }

                            if (totalRead == contentLength) {
                                handleMessage(String(buffer, StandardCharsets.UTF_8))
                            } else {}
                        }
                    } catch (e: Exception) {}
                },
                "htmllsp-reader",
            )
            .apply { priority = Thread.MAX_PRIORITY }
            .start()
    }

    private fun startErrorReaderThread() {
        Thread(
                {
                    try {
                        val reader =
                            BufferedReader(
                                InputStreamReader(serverProcess!!.errorStream),
                                BUFFER_SIZE,
                            )
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {}
                    } catch (e: Exception) {}
                },
                "htmllsp-error-reader",
            )
            .start()
    }

    private fun readLineFromStream(inputStream: InputStream): String? {
        val sb = StringBuilder()
        var byte: Int
        while (inputStream.read().also { byte = it } != -1) {
            val char = byte.toChar()
            if (char == '\r') {
                val next = inputStream.read()
                if (next == -1) return if (sb.isEmpty()) null else sb.toString()
                if (next != '\n'.code) sb.append(next.toChar())
                break
            } else if (char == '\n') {
                break
            } else {
                sb.append(char)
            }
        }
        return if (byte == -1 && sb.isEmpty()) null else sb.toString()
    }

    // ─── Message handling ─────────────────────────────────────────────────────

    private fun handleMessage(json: String) {
        try {
            val obj = gson.fromJson(json, JsonObject::class.java)

            if (obj.has("id")) {
                val id = obj.get("id").asInt
                val deferred = pendingRequests.remove(id)

                if (obj.has("error")) {
                    val error = obj.getAsJsonObject("error")

                    deferred?.completeExceptionally(Exception(error.get("message").asString))
                } else {
                    val result = obj.get("result")
                    val wrapped = JsonObject()
                    if (result != null && !result.isJsonNull) {
                        wrapped.add("result", result)
                    }
                    deferred?.complete(wrapped)
                }
            } else if (obj.has("method")) {
                handleNotification(obj)
            }
        } catch (e: Exception) {}
    }

    private fun handleNotification(obj: JsonObject) {
        when (val method = obj.get("method")?.asString ?: return) {
            "textDocument/publishDiagnostics" -> {
                val params = obj.getAsJsonObject("params") ?: return
                val uri = params.get("uri")?.asString ?: return
                val diags = params.getAsJsonArray("diagnostics") ?: JsonArray()
                diagnosticsCache[uri] = diags
            }
            "window/logMessage" -> {}
            "window/showMessage" -> {}
            "$/progress" -> {
                /* ignore */
            }
            else -> {}
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun File.toUri(): String = "file://${absolutePath}"
}
