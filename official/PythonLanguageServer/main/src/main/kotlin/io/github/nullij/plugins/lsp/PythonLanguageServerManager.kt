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
 * Manages the Python LSP server (pyright-langserver) for ACS.
 *
 * Launched via: /bin/pyright-langserver --stdio
 *
 * Capabilities supported by pyright-langserver:
 * - textDocument/completion (symbols, imports, keywords, snippets)
 * - textDocument/hover (type information, docstrings)
 * - textDocument/signatureHelp (function/method parameter hints)
 * - textDocument/definition (go-to symbol definition)
 * - textDocument/references (find all usages)
 * - textDocument/documentSymbol (classes, functions, variables)
 * - textDocument/documentHighlight (highlight symbol usages)
 * - textDocument/formatting (via autopep8/black integration)
 * - textDocument/rangeFormatting
 * - textDocument/rename (symbol rename across workspace)
 * - textDocument/codeAction (quick fixes, organize imports)
 * - textDocument/inlayHint (parameter names, return types)
 * - textDocument/publishDiagnostics (type errors, pushed)
 *
 * @author nullij @ https://github.com/nullij
 */
class PythonLanguageServerManager : PluginLanguageServerSpec {

    private val gson = Gson()

    private var serverProcess: Process? = null
    private var writer: BufferedWriter? = null
    private var client: PythonLanguageClient? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nextId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    @Volatile private var running = false
    @Volatile private var initializing = false

    private val diagnosticsCache = ConcurrentHashMap<String, JsonArray>()

    fun getCachedDiagnostics(uri: String): JsonArray? = diagnosticsCache[uri]

    companion object {
        private const val TAG = "PyLSP"
        private const val PYRIGHT_EXECUTABLE = "/data/data/com.acside/files/usr/bin/pyright-langserver"
        private const val NODE_EXECUTABLE = "/data/data/com.acside/files/usr/bin/node"
        private const val BUFFER_SIZE = 32768
        private const val MAX_CONTENT_LENGTH = 10_485_760
    }

    // ─── PluginLanguageServerSpec ─────────────────────────────────────────────

    override val languageId: String = "python"

    override fun start(): Boolean = runBlocking {
        if (running) {

            android.util.Log.i(TAG, "start() called but server is already running — skipping")
            return@runBlocking true
        }

        initializing = true
        android.util.Log.i(TAG, "start() - launching pyright-langserver process")

        try {
            val scriptFile = File(PYRIGHT_EXECUTABLE)
            val nodeFile = File(NODE_EXECUTABLE)
            val processBuilder = PluginApi.process.builder()

            val builderWithCmd = if (nodeFile.exists() && scriptFile.exists()) {
                processBuilder.command(nodeFile.absolutePath, scriptFile.absolutePath, "--stdio")
            } else if (scriptFile.exists()) {
                processBuilder.command(scriptFile.absolutePath, "--stdio")
            } else {
                processBuilder.command("pyright-langserver", "--stdio")
            }

            serverProcess = builderWithCmd
                .attachStorage()
                .withEnv(
                    mapOf(
                        "HOME" to "/data/data/com.acside/files/home",
                        "PATH" to "/data/data/com.acside/files/usr/bin:/data/data/com.acside/files/usr/bin/applets",
                        "LD_LIBRARY_PATH" to "/data/data/com.acside/files/usr/lib",
                        "PREFIX" to "/data/data/com.acside/files/usr",
                    )
                )
                .launch()

            android.util.Log.i(TAG, "process launched — setting up I/O streams")

            writer =
                BufferedWriter(
                    OutputStreamWriter(serverProcess!!.outputStream, StandardCharsets.UTF_8),
                    BUFFER_SIZE,
                )

            startReaderThread()
            startErrorReaderThread()

            // Give the Node.js process a moment to boot before the handshake.
            android.util.Log.i(TAG, "waiting 1200 ms for process to boot…")
            delay(1200)
            android.util.Log.i(TAG, "boot wait done — sending initialize")

            val initialized = sendInitialize()

            return@runBlocking if (initialized) {
                client = PythonLanguageClient(this@PythonLanguageServerManager)
                running = true
                android.util.Log.i(TAG, "server started successfully ✓")
                true
            } else {
                android.util.Log.e(TAG, "sendInitialize() returned false — aborting start")
                stopInternal()
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(
                TAG,
                "exception during start(): ${e::class.simpleName}: ${e.message}",
                e,
            )
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

                                    // Pyright supports signatureHelp with parameter information.
                                    add(
                                        "signatureHelp",
                                        JsonObject().apply {
                                            add(
                                                "signatureInformation",
                                                JsonObject().apply {
                                                    add(
                                                        "documentationFormat",
                                                        gson.toJsonTree(
                                                            arrayOf("markdown", "plaintext")
                                                        ),
                                                    )
                                                    add(
                                                        "parameterInformation",
                                                        JsonObject().apply {
                                                            addProperty("labelOffsetSupport", true)
                                                        },
                                                    )
                                                },
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

                                    // Pyright supports formatting via bundled formatter.
                                    add("formatting", JsonObject())
                                    add("rangeFormatting", JsonObject())

                                    // Pyright supports rename across the workspace.
                                    add(
                                        "rename",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                        },
                                    )

                                    // Pyright supports code actions: quick fixes, organize imports.
                                    add(
                                        "codeAction",
                                        JsonObject().apply {
                                            add(
                                                "codeActionLiteralSupport",
                                                JsonObject().apply {
                                                    add(
                                                        "codeActionKind",
                                                        JsonObject().apply {
                                                            add(
                                                                "valueSet",
                                                                gson.toJsonTree(
                                                                    arrayOf(
                                                                        "quickfix",
                                                                        "source.organizeImports",
                                                                    )
                                                                ),
                                                            )
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )

                                    // Pyright supports inlay hints for parameter names and return
                                    // types.
                                    add(
                                        "inlayHint",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                        },
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

                    // Pyright initializationOptions: tune analysis behaviour.
                    add(
                        "initializationOptions",
                        JsonObject().apply {
                            add(
                                "python",
                                JsonObject().apply {
                                    add(
                                        "analysis",
                                        JsonObject().apply {
                                            addProperty("autoImportCompletions", true)
                                            addProperty("useLibraryCodeForTypes", true)
                                        },
                                    )
                                },
                            )
                        },
                    )
                }

            android.util.Log.d(TAG, "sending initialize request…")
            val response = sendRequest("initialize", params)
            if (response == null) {
                android.util.Log.e(
                    TAG,
                    "initialize request timed out or failed — response was null",
                )
                return false
            }
            android.util.Log.i(TAG, "initialize response received: ${response}")
            sendNotification("initialized", JsonObject())
            android.util.Log.i(TAG, "sent initialized notification")
            true
        } catch (e: Exception) {
            android.util.Log.e(
                TAG,
                "exception in sendInitialize(): ${e::class.simpleName}: ${e.message}",
                e,
            )
            false
        }
    }

    // ─── Document lifecycle ───────────────────────────────────────────────────

    override fun openDocument(file: File): Boolean {
        if (!running) {
            android.util.Log.w(
                TAG,
                "openDocument() called but server is not running — file: ${file.name}",
            )
            return false
        }
        val content =
            try {
                file.readText()
            } catch (e: Exception) {
                android.util.Log.e(
                    TAG,
                    "failed to read file for openDocument: ${file.absolutePath}",
                    e,
                )
                return false
            }
        android.util.Log.d(TAG, "openDocument: ${file.name} (${content.length} chars)")
        scope.launch {
            sendNotification(
                "textDocument/didOpen",
                JsonObject().apply {
                    add(
                        "textDocument",
                        JsonObject().apply {
                            addProperty("uri", file.toUri())
                            addProperty("languageId", "python")
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
                android.util.Log.e(TAG, "request timed out: method=$method")
                null
            } catch (e: Exception) {
                android.util.Log.e(TAG, "sendRequest exception: method=$method, ${e.message}")
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
                "pylsp-reader",
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
                        while (reader.readLine().also { line = it } != null) {
                            android.util.Log.w(TAG, "stderr: $line")
                        }
                        android.util.Log.i(TAG, "stderr stream closed")
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "error-reader thread crashed: ${e.message}", e)
                    }
                },
                "pylsp-error-reader",
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
                    android.util.Log.e(
                        TAG,
                        "LSP error response for id=$id: code=${error.get("code")}, message=${error.get("message")}",
                    )
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
        } catch (e: Exception) {
            android.util.Log.e(
                TAG,
                "failed to parse server message: ${e.message} — raw: ${json.take(200)}",
            )
        }
    }

    private fun handleNotification(obj: JsonObject) {
        when (val method = obj.get("method")?.asString ?: return) {
            "textDocument/publishDiagnostics" -> {
                val params = obj.getAsJsonObject("params") ?: return
                val uri = params.get("uri")?.asString ?: return
                val diags = params.getAsJsonArray("diagnostics") ?: JsonArray()
                diagnosticsCache[uri] = diags
            }
            "window/logMessage" -> {
                val params = obj.getAsJsonObject("params")
                android.util.Log.i(TAG, "window/logMessage: ${params?.get("message")?.asString}")
            }
            "window/showMessage" -> {
                val params = obj.getAsJsonObject("params")
                android.util.Log.i(TAG, "window/showMessage: ${params?.get("message")?.asString}")
            }
            "$/progress" -> {
                /* ignore */
            }
            else -> {}
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun File.toUri(): String = "file://${absolutePath}"
}
