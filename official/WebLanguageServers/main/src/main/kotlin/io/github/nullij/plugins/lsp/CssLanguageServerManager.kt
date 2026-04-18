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
 * Manages the CSS LSP server (vscode-css-language-server) for ACS.
 *
 * Launched via: npx vscode-css-language-server --stdio
 *
 * One manager handles all three CSS dialects. The active [languageId] is set at construction time;
 * register separate instances for "css", "less", and "scss" if you need all three.
 *
 * Capabilities supported by vscode-css-language-server:
 * - textDocument/completion (properties, values, selectors, at-rules)
 * - textDocument/hover (property documentation from MDN)
 * - textDocument/definition (custom property / variable go-to)
 * - textDocument/references
 * - textDocument/documentSymbol (selectors, at-rules)
 * - textDocument/documentHighlight
 * - textDocument/formatting
 * - textDocument/rangeFormatting
 * - textDocument/rename (custom property rename)
 * - textDocument/publishDiagnostics (pushed)
 *
 * Not supported (returns empty / null):
 * - textDocument/signatureHelp
 * - textDocument/inlayHint
 * - textDocument/codeAction
 *
 * @param languageId one of "css", "less", or "scss". Defaults to "css".
 * @author nullij @ https://github.com/nullij
 */
class CssLanguageServerManager(override val languageId: String = "css") : PluginLanguageServerSpec {

    init {
        require(languageId in setOf("css", "less", "scss")) {
            "CssLanguageServerManager: unsupported languageId '$languageId'. Use css, less, or scss."
        }
    }

    private val gson = Gson()

    private var serverProcess: Process? = null
    private var writer: BufferedWriter? = null
    private var client: CssLanguageClient? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nextId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    @Volatile private var running = false
    @Volatile private var initializing = false

    private val diagnosticsCache = ConcurrentHashMap<String, JsonArray>()

    fun getCachedDiagnostics(uri: String): JsonArray? = diagnosticsCache[uri]

    companion object {
        private const val TAG = "CssLSP"
        private const val BUFFER_SIZE = 32768
        private const val MAX_CONTENT_LENGTH = 10_485_760
    }

    // ─── PluginLanguageServerSpec ─────────────────────────────────────────────

    override fun start(): Boolean = runBlocking {
        if (running) {
            return@runBlocking true
        }

        initializing = true

        try {
            serverProcess =
                PluginApi.process
                    .builder()
                    .command("/bin/vscode-css-language-server", "--stdio")
                    .attachDir(
                        hostDir = java.io.File(PluginApi.environment.homeDir, "lab"),
                        mountAt = "/root/lab",
                    )
                    .attachStorage()
                    .withEnv(
                        mapOf(
                            "HOME" to "/root",
                            "USER" to "root",
                            "PATH" to
                                "/bin:/usr/bin:/root/acslab/packages/official/nodejs-v25.9.0/node-v25.9.0-linux-arm64/bin",
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
                client = CssLanguageClient(this@CssLanguageServerManager)
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

                                    // CSS server supports formatting and rename
                                    add("formatting", JsonObject())
                                    add("rangeFormatting", JsonObject())
                                    add(
                                        "rename",
                                        JsonObject().apply { addProperty("prepareSupport", false) },
                                    )

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

                    // vscode-css-language-server reads per-dialect settings here.
                    add(
                        "initializationOptions",
                        JsonObject().apply {
                            // Pass an empty settings object; the server will use its
                            // built-in defaults. Override with e.g.:
                            //   add("css",  JsonObject().apply { addProperty("validate", true) })
                            //   add("less", JsonObject().apply { addProperty("validate", true) })
                            //   add("scss", JsonObject().apply { addProperty("validate", true) })
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
                            addProperty("languageId", languageId) // "css", "less", or "scss"
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
                                    // Full document sync, matches textDocumentSync: Full
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
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val message = "Content-Length: ${bytes.size}\r\n\r\n$content"
        synchronized(writer!!) {
            writer?.write(message)
            writer?.flush()
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
                    } catch (e: Exception) {
                        // if (running || initializing)
                    }
                },
                "csslsp-reader",
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
                "csslsp-error-reader",
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
