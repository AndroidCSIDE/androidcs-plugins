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
 * Manages the Bash LSP server (bash-language-server) for ACS.
 *
 * Now implements [PluginLanguageServerSpec] from the public API instead of the internal
 * LSPAccessor.PluginLanguageServer.
 *
 * Context is no longer passed in the constructor — it is obtained from [PluginApi] when the server
 * starts.
 */
class BashLanguageServerManager : PluginLanguageServerSpec {

    private val gson = Gson()

    private var serverProcess: Process? = null
    private var writer: BufferedWriter? = null
    private var client: BashLanguageClient? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nextId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    @Volatile private var running = false
    @Volatile private var initializing = false

    private val diagnosticsCache = ConcurrentHashMap<String, JsonArray>()

    fun getCachedDiagnostics(uri: String): JsonArray? = diagnosticsCache[uri]

    companion object {
        private const val TAG = "BashLSP"
        private const val BASH_LS_EXECUTABLE = "/bin/bash-language-server"
        private const val BUFFER_SIZE = 32768
        private const val MAX_CONTENT_LENGTH = 10_485_760
    }

    // ─── PluginLanguageServerSpec ─────────────────────────────────────────────

    override val languageId: String = "shellscript"

    override fun start(): Boolean = runBlocking {
        Log.d(TAG, "[DEBUG] start() BEGIN, running=$running")

        if (running) {
            Log.w(TAG, "Bash server already running")
            return@runBlocking true
        }

        initializing = true
        Log.d(TAG, "[DEBUG] initializing=true")

        try {
            Log.d(TAG, "[DEBUG] About to launch process...")
            serverProcess =
                PluginApi.process
                    .builder()
                    .command("/bin/bash-language-server", "start")
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
            Log.d(TAG, "[DEBUG] Process launched, alive=${serverProcess?.isAlive}")

            Log.d(TAG, "[DEBUG] Creating writer...")
            writer =
                BufferedWriter(
                    OutputStreamWriter(serverProcess!!.outputStream, StandardCharsets.UTF_8),
                    BUFFER_SIZE,
                )
            Log.d(TAG, "[DEBUG] Writer created")

            Log.d(TAG, "[DEBUG] Starting reader threads...")
            startReaderThread()
            startErrorReaderThread()
            Log.d(TAG, "[DEBUG] Reader threads started")

            Log.d(TAG, "[DEBUG] Delaying 800ms...")
            delay(800)
            Log.d(TAG, "[DEBUG] Delay complete")

            Log.d(TAG, "[DEBUG] Calling sendInitialize()...")
            val initialized = sendInitialize()
            Log.d(TAG, "[DEBUG] sendInitialize() returned: $initialized")

            return@runBlocking if (initialized) {
                client = BashLanguageClient(this@BashLanguageServerManager)
                running = true
                Log.i(TAG, "✅ bash-language-server started successfully")
                true
            } else {
                Log.e(TAG, "❌ bash-language-server initialize handshake failed")
                stopInternal()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "[DEBUG] Exception in start()", e)
            stopInternal()
            false
        } finally {
            initializing = false
            Log.d(TAG, "[DEBUG] start() END, initializing=false")
        }
    }

    override fun stop() = runBlocking { stopInternal() }

    override fun isRunning(): Boolean = running

    private suspend fun stopInternal(): Unit =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Stopping bash-language-server...")
                running = false

                try {
                    sendRequest("shutdown", JsonObject())
                    sendNotification("exit", JsonObject())
                    delay(400)
                } catch (e: Exception) {
                    Log.w(TAG, "Error during LSP shutdown sequence", e)
                }

                try {
                    writer?.close()
                } catch (_: Exception) {}

                serverProcess?.destroy()
                serverProcess?.waitFor()
                serverProcess = null

                client = null
                pendingRequests.clear()

                Log.d(TAG, "✅ bash-language-server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping bash-language-server", e)
            }
        }

    override fun getClient(): LanguageServerClient =
        client ?: error("getClient() called before server is started")

    fun destroy() {
        scope.cancel()
        stop()
    }

    // ─── LSP initialize handshake ─────────────────────────────────────────────
    private suspend fun sendInitialize(): Boolean {
        Log.d(TAG, "[DEBUG] sendInitialize() BEGIN")
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
                                                },
                                            )
                                        },
                                    )
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

                    add("initializationOptions", JsonObject())
                }

            Log.d(TAG, "[DEBUG] About to sendRequest('initialize', ...)")
            val response = sendRequest("initialize", params)
            Log.d(TAG, "[DEBUG] sendRequest returned: ${response != null}")

            if (response == null) {
                Log.d(TAG, "[DEBUG] Response is null, returning false")
                return false
            }

            Log.d(TAG, "[DEBUG] Sending initialized notification...")
            sendNotification("initialized", JsonObject())
            Log.d(TAG, "[DEBUG] ✅ bash-language-server initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[DEBUG] Exception in sendInitialize", e)
            false
        }
    }

    // ─── JSON-RPC transport ───────────────────────────────────────────────────

    internal suspend fun sendRequest(method: String, params: JsonObject): JsonObject? {
        Log.d(TAG, "[DEBUG] sendRequest('$method') BEGIN")
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "[DEBUG] Inside withContext for '$method'")
            try {
                val id = nextId.getAndIncrement()
                val deferred = CompletableDeferred<JsonObject>()
                pendingRequests[id] = deferred
                Log.d(TAG, "[DEBUG] Created deferred with id=$id")

                val request =
                    JsonObject().apply {
                        addProperty("jsonrpc", "2.0")
                        addProperty("id", id)
                        addProperty("method", method)
                        add("params", params)
                    }

                val requestJson = gson.toJson(request)
                Log.d(TAG, "[DEBUG] Request JSON: $requestJson")

                Log.d(TAG, "[DEBUG] About to writeMessage...")
                writeMessage(requestJson)
                Log.d(TAG, "[DEBUG] writeMessage complete")

                Log.d(TAG, "[DEBUG] About to await with timeout (30s)...")
                val result =
                    withTimeout(30_000) {
                        Log.d(TAG, "[DEBUG] Inside withTimeout, awaiting deferred...")
                        deferred.await()
                    }
                Log.d(TAG, "[DEBUG] Deferred completed, result=${result != null}")
                result
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "[DEBUG] Request timed out: $method")
                null
            } catch (e: Exception) {
                Log.e(TAG, "[DEBUG] Error sending request: $method", e)
                null
            }
        }
    }

    internal fun sendNotification(method: String, params: JsonObject) {
        Log.d(TAG, "[DEBUG] sendNotification('$method')")
        try {
            val notification =
                JsonObject().apply {
                    addProperty("jsonrpc", "2.0")
                    addProperty("method", method)
                    add("params", params)
                }
            writeMessage(gson.toJson(notification))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification: $method", e)
        }
    }

    override fun openDocument(file: File): Boolean {
        if (!running) return false
        val content =
            try {
                file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "openDocument: failed to read ${file.name}", e)
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
                            addProperty("languageId", "shellscript")
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
                        com.google.gson.JsonArray().apply {
                            add(
                                JsonObject().apply {
                                    addProperty("text", content) // full-document sync
                                }
                            )
                        },
                    )
                },
            )
        }
    }

    private fun writeMessage(content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val message = "Content-Length: ${bytes.size}\r\n\r\n$content"
        Log.d(TAG, "[DEBUG] writeMessage: Content-Length=${bytes.size}")
        synchronized(writer!!) {
            writer?.write(message)
            writer?.flush()
        }
        Log.d(TAG, "[DEBUG] writeMessage flushed")
    }

    // ─── Reader threads ───────────────────────────────────────────────────────

    private fun startReaderThread() {
        Log.d(TAG, "[DEBUG] startReaderThread() called")
        Thread(
                {
                    Log.d(TAG, "[DEBUG] Reader thread started")
                    try {
                        val inputStream = serverProcess!!.inputStream
                        Log.d(TAG, "[DEBUG] Got input stream")

                        while (running || initializing) {
                            Log.d(
                                TAG,
                                "[DEBUG] Reader loop iteration, running=$running, initializing=$initializing",
                            )
                            var contentLength = -1

                            // Read headers
                            Log.d(TAG, "[DEBUG] Reading headers...")
                            while (true) {
                                val line = readLineFromStream(inputStream)
                                Log.d(
                                    TAG,
                                    "[DEBUG] Read header line: '${line?.take(100)}' (len=${line?.length})",
                                )
                                if (line == null) {
                                    Log.d(TAG, "[DEBUG] Header line is null, exiting reader")
                                    return@Thread
                                }
                                if (line.isEmpty()) {
                                    Log.d(TAG, "[DEBUG] Empty line, end of headers")
                                    break
                                }
                                if (line.startsWith("Content-Length:")) {
                                    contentLength = line.substring(15).trim().toInt()
                                    Log.d(TAG, "[DEBUG] Content-Length=$contentLength")
                                }
                            }

                            if (contentLength <= 0) {
                                Log.d(TAG, "[DEBUG] Invalid content length: $contentLength")
                                continue
                            }
                            if (contentLength > MAX_CONTENT_LENGTH) {
                                Log.e(TAG, "Message too large: $contentLength bytes — skipping")
                                continue
                            }

                            Log.d(TAG, "[DEBUG] Reading $contentLength bytes...")
                            val buffer = ByteArray(contentLength)
                            var totalRead = 0
                            while (totalRead < contentLength) {
                                val read =
                                    inputStream.read(buffer, totalRead, contentLength - totalRead)
                                if (read < 0) {
                                    Log.d(TAG, "[DEBUG] EOF while reading content")
                                    break
                                }
                                totalRead += read
                            }

                            Log.d(TAG, "[DEBUG] Read $totalRead/$contentLength bytes")
                            if (totalRead == contentLength) {
                                val message = String(buffer, StandardCharsets.UTF_8)
                                Log.d(TAG, "[DEBUG] Message content: ${message.take(200)}")
                                handleMessage(message)
                            } else {
                                Log.e(TAG, "Incomplete read: $totalRead / $contentLength bytes")
                            }
                        }
                        Log.d(TAG, "[DEBUG] Reader loop exited")
                    } catch (e: Exception) {
                        if (running || initializing) Log.e(TAG, "[DEBUG] Reader thread error", e)
                    }
                    Log.d(TAG, "[DEBUG] Reader thread ending")
                },
                "bashlsp-reader",
            )
            .apply { priority = Thread.MAX_PRIORITY }
            .start()
    }

    private fun startErrorReaderThread() {
        Log.d(TAG, "[DEBUG] startErrorReaderThread() called")
        Thread(
                {
                    Log.d(TAG, "[DEBUG] Error reader thread started")
                    try {
                        val errorReader =
                            BufferedReader(
                                InputStreamReader(serverProcess!!.errorStream),
                                BUFFER_SIZE,
                            )
                        var line: String?
                        while (errorReader.readLine().also { line = it } != null) {
                            Log.d(TAG, "[bash-ls stderr] $line")
                        }
                        Log.d(TAG, "[DEBUG] Error reader reached EOF")
                    } catch (e: Exception) {
                        Log.e(TAG, "[DEBUG] Error reader thread error", e)
                    }
                    Log.d(TAG, "[DEBUG] Error reader thread ending")
                },
                "bashlsp-error-reader",
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

    private fun handleMessage(json: String) {
        Log.d(TAG, "[DEBUG] handleMessage() called")
        try {
            val obj = gson.fromJson(json, JsonObject::class.java)

            if (obj.has("id")) {
                val id = obj.get("id").asInt
                Log.d(TAG, "[DEBUG] Handling response with id=$id")
                val deferred = pendingRequests.remove(id)

                if (obj.has("error")) {
                    val error = obj.getAsJsonObject("error")
                    Log.e(TAG, "Server error: ${error.get("message").asString}")
                    deferred?.completeExceptionally(Exception(error.get("message").asString))
                } else {
                    val result = obj.get("result")
                    val wrapped = JsonObject()
                    if (result != null && !result.isJsonNull) {
                        wrapped.add("result", result)
                    }
                    Log.d(TAG, "[DEBUG] Completing deferred with result")
                    deferred?.complete(wrapped)
                }
            } else if (obj.has("method")) {
                Log.d(TAG, "[DEBUG] Handling notification")
                handleNotification(obj)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[DEBUG] Error handling message", e)
        }
    }

    private fun handleNotification(obj: JsonObject) {
        when (val method = obj.get("method")?.asString ?: return) {
            "textDocument/publishDiagnostics" -> {
                val params = obj.getAsJsonObject("params") ?: return
                val uri = params.get("uri")?.asString ?: return
                val diags = params.getAsJsonArray("diagnostics") ?: JsonArray()
                diagnosticsCache[uri] = diags
                Log.d(TAG, "Cached ${diags.size()} diagnostics for $uri")
            }
            "window/logMessage" -> {
                val msg = obj.getAsJsonObject("params")?.get("message")?.asString
                Log.d(TAG, "[bash-ls] $msg")
            }
            "window/showMessage" -> {
                val msg = obj.getAsJsonObject("params")?.get("message")?.asString
                Log.i(TAG, "[bash-ls show] $msg")
            }
            "$/progress" -> {
                /* ignore */
            }
            else -> Log.v(TAG, "Unhandled notification: $method")
        }
    }

    private fun File.toUri(): String = "file://${absolutePath}"
}
