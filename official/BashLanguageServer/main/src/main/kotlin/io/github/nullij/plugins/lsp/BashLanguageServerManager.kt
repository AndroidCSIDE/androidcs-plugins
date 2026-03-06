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

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nullij.androidcodestudio.internals.LSPAccessor
import kotlinx.coroutines.*
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages the Bash LSP server (bash-language-server) for ACS.
 *
 * bash-language-server is launched inside the IDE's proot/acsenv environment.
 * It must be installed beforehand:
 * ```
 * npm install -g bash-language-server
 * ```
 * which places the executable at `/usr/local/bin/bash-language-server` inside the rootfs.
 *
 * This class implements [LSPAccessor.PluginLanguageServer] so it can be
 * registered with the IDE via [LSPAccessor.registerServer].
 *
 * Registration (call this once, e.g. from your action or on plugin load):
 * ```kotlin
 * val lsp = LSPAccessor.resolve(context) ?: return
 * lsp.registerServer("shellscript", BashLanguageServerManager(context))
 * lsp.startServer("shellscript")
 * ```
 *
 * @author nullij @ https://github.com/nullij
 */
class BashLanguageServerManager(
    private val context: Context
) : LSPAccessor.PluginLanguageServer {

    private val gson = Gson()

    private var serverProcess: Process?            = null
    private var writer: BufferedWriter?            = null
    private var client: BashLanguageClient?        = null

    private val scope          = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nextId         = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    @Volatile private var running      = false
    @Volatile private var initializing = false

    // Cache of diagnostics pushed by bash-language-server via textDocument/publishDiagnostics.
    // Keyed by document URI. Read by BashLanguageClient.getDiagnostics().
    private val diagnosticsCache = ConcurrentHashMap<String, JsonArray>()

    /** Return the last set of diagnostics pushed for [uri], or null if none yet. */
    fun getCachedDiagnostics(uri: String): JsonArray? = diagnosticsCache[uri]

    companion object {
        private const val TAG = "BashLSP"

        // bash-language-server executable path inside the proot rootfs.
        // `npm install -g bash-language-server` places it here.
        private const val BASH_LS_EXECUTABLE = "/bin/bash-language-server"

        private const val BUFFER_SIZE        = 32768
        private const val MAX_CONTENT_LENGTH = 10_485_760 // 10 MB
    }

    // ─── PluginLanguageServer ─────────────────────────────────────────────────

    override suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (running) {
            Log.w(TAG, "Bash server already running")
            return@withContext true
        }

        initializing = true

        try {
            Log.d(TAG, "Starting bash-language-server...")

            serverProcess = LSPAccessor.LanguageServerProcess.builder(context)
                .command(BASH_LS_EXECUTABLE, "start")
                .attachStorage()
                .withEnv(mapOf(
                    "HOME" to "/root",
                    "USER" to "root",
                    // Point bash-language-server to the system bash so it can
                    // resolve builtins and run shellcheck correctly.
                    "BASH_LSP_SHELL"     to "/bin/bash",
                    // Suppress noisy info/debug logs from the server
                    "BASH_LSP_LOG_LEVEL" to "error"
                ))
                .launch()

            writer = BufferedWriter(
                OutputStreamWriter(serverProcess!!.outputStream, StandardCharsets.UTF_8),
                BUFFER_SIZE
            )

            startReaderThread()
            startErrorReaderThread()

            // Give the process a moment to start before the handshake
            delay(800)

            val initialized = sendInitialize()

            return@withContext if (initialized) {
                client  = BashLanguageClient(this@BashLanguageServerManager)
                running = true
                Log.i(TAG, "✅ bash-language-server started successfully")
                true
            } else {
                Log.e(TAG, "❌ bash-language-server initialize handshake failed")
                stop()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start bash-language-server", e)
            stop()
            false
        } finally {
            initializing = false
        }
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
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

            try { writer?.close() } catch (_: Exception) {}

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

    override fun isRunning(): Boolean = running

    override fun getClient(): Any = client
        ?: error("getClient() called before server is started")

    override suspend fun openDocument(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!running) {
            Log.w(TAG, "openDocument called but server is not running")
            return@withContext false
        }
        try {
            val uri  = file.toUri()
            val text = file.readText()

            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply {
                    addProperty("uri",        uri)
                    addProperty("languageId", "shellscript")
                    addProperty("version",    1)
                    addProperty("text",       text)
                })
            }

            sendNotification("textDocument/didOpen", params)
            delay(300)

            Log.d(TAG, "✅ Opened: ${file.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open document: ${file.name}", e)
            false
        }
    }

    override suspend fun closeDocument(file: File): Unit = withContext(Dispatchers.IO) {
        if (!running) return@withContext
        try {
            val params = JsonObject().apply {
                add("textDocument", JsonObject().apply {
                    addProperty("uri", file.toUri())
                })
            }
            sendNotification("textDocument/didClose", params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close document: ${file.name}", e)
        }
    }

    override suspend fun documentChanged(file: File, content: String, version: Int): Unit =
        withContext(Dispatchers.IO) {
            if (!running) return@withContext
            try {
                val params = JsonObject().apply {
                    add("textDocument", JsonObject().apply {
                        addProperty("uri",     file.toUri())
                        addProperty("version", version)
                    })
                    add("contentChanges", gson.toJsonTree(listOf(mapOf("text" to content))))
                }
                sendNotification("textDocument/didChange", params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send document change for: ${file.name}", e)
            }
        }

    override fun destroy() {
        scope.cancel()
        runBlocking { stop() }
    }

    // ─── LSP initialize handshake ─────────────────────────────────────────────

    private suspend fun sendInitialize(): Boolean {
        return try {
            val params = JsonObject().apply {
                addProperty("processId", android.os.Process.myPid())
                addProperty("rootUri", null as String?)

                add("capabilities", JsonObject().apply {
                    add("textDocument", JsonObject().apply {
                        add("completion", JsonObject().apply {
                            add("completionItem", JsonObject().apply {
                                addProperty("snippetSupport",    true)
                                addProperty("deprecatedSupport", true)
                                addProperty("preselectSupport",  true)
                                add("documentationFormat",
                                    gson.toJsonTree(arrayOf("markdown", "plaintext")))
                                add("resolveSupport", gson.toJsonTree(mapOf(
                                    "properties" to arrayOf(
                                        "documentation", "detail", "additionalTextEdits"
                                    )
                                )))
                            })
                            add("completionItemKind", JsonObject().apply {
                                add("valueSet", gson.toJsonTree((1..25).toList()))
                            })
                        })
                        add("hover", JsonObject().apply {
                            add("contentFormat",
                                gson.toJsonTree(arrayOf("markdown", "plaintext")))
                        })
                        add("signatureHelp", JsonObject().apply {
                            add("signatureInformation", JsonObject().apply {
                                add("documentationFormat",
                                    gson.toJsonTree(arrayOf("markdown", "plaintext")))
                            })
                        })
                        add("synchronization", JsonObject().apply {
                            addProperty("dynamicRegistration",   false)
                            addProperty("willSave",              false)
                            addProperty("willSaveWaitUntil",     false)
                            addProperty("didSave",               true)
                        })
                        add("publishDiagnostics", JsonObject().apply {
                            addProperty("relatedInformation", true)
                        })
                    })
                    add("workspace", JsonObject().apply {
                        addProperty("applyEdit", true)
                        add("workspaceEdit", gson.toJsonTree(mapOf("documentChanges" to true)))
                        addProperty("workspaceFolders", false)
                    })
                })

                add("initializationOptions", JsonObject())
            }

            Log.d(TAG, "Sending initialize...")
            val response = sendRequest("initialize", params) ?: return false
            Log.d(TAG, "Initialize response received")

            sendNotification("initialized", JsonObject())
            Log.d(TAG, "✅ bash-language-server initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Initialize failed", e)
            false
        }
    }

    // ─── JSON-RPC transport ───────────────────────────────────────────────────

    /**
     * Send a request and block (with timeout) until the response arrives.
     */
    internal suspend fun sendRequest(method: String, params: JsonObject): JsonObject? {
        return withContext(Dispatchers.IO) {
            try {
                val id       = nextId.getAndIncrement()
                val deferred = CompletableDeferred<JsonObject>()
                pendingRequests[id] = deferred

                val request = JsonObject().apply {
                    addProperty("jsonrpc", "2.0")
                    addProperty("id",      id)
                    addProperty("method",  method)
                    add("params", params)
                }

                writeMessage(gson.toJson(request))

                withTimeout(30_000) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Request timed out: $method")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error sending request: $method", e)
                null
            }
        }
    }

    /** Send a notification (no response expected). */
    internal fun sendNotification(method: String, params: JsonObject) {
        try {
            val notification = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("method",  method)
                add("params", params)
            }
            writeMessage(gson.toJson(notification))
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification: $method", e)
        }
    }

    private fun writeMessage(content: String) {
        val bytes   = content.toByteArray(StandardCharsets.UTF_8)
        val message = "Content-Length: ${bytes.size}\r\n\r\n$content"
        synchronized(writer!!) {
            writer?.write(message)
            writer?.flush()
        }
    }

    // ─── Reader threads ───────────────────────────────────────────────────────

    private fun startReaderThread() {
        Thread({
            try {
                val inputStream = serverProcess!!.inputStream

                while (running || initializing) {
                    var contentLength = -1

                    // Read headers
                    while (true) {
                        val line = readLineFromStream(inputStream) ?: return@Thread
                        if (line.isEmpty()) break
                        if (line.startsWith("Content-Length:")) {
                            contentLength = line.substring(15).trim().toInt()
                        }
                    }

                    if (contentLength <= 0) continue
                    if (contentLength > MAX_CONTENT_LENGTH) {
                        Log.e(TAG, "Message too large: $contentLength bytes — skipping")
                        continue
                    }

                    // Read exact byte count
                    val buffer   = ByteArray(contentLength)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = inputStream.read(buffer, totalRead, contentLength - totalRead)
                        if (read < 0) break
                        totalRead += read
                    }

                    if (totalRead == contentLength) {
                        handleMessage(String(buffer, StandardCharsets.UTF_8))
                    } else {
                        Log.e(TAG, "Incomplete read: $totalRead / $contentLength bytes")
                    }
                }
            } catch (e: Exception) {
                if (running || initializing) Log.e(TAG, "Reader thread error", e)
            }
        }, "bashlsp-reader").apply { priority = Thread.MAX_PRIORITY }.start()
    }

    private fun startErrorReaderThread() {
        Thread({
            try {
                val errorReader = BufferedReader(
                    InputStreamReader(serverProcess!!.errorStream),
                    BUFFER_SIZE
                )
                var line: String?
                while (errorReader.readLine().also { line = it } != null) {
                    Log.d(TAG, "[bash-ls stderr] $line")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reader thread error", e)
            }
        }, "bashlsp-error-reader").start()
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
        try {
            val obj = gson.fromJson(json, JsonObject::class.java)

            if (obj.has("id")) {
                val id       = obj.get("id").asInt
                val deferred = pendingRequests.remove(id)

                if (obj.has("error")) {
                    val error = obj.getAsJsonObject("error")
                    Log.e(TAG, "Server error: ${error.get("message").asString}")
                    deferred?.completeExceptionally(
                        Exception(error.get("message").asString)
                    )
                } else {
                    val result  = obj.get("result")
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
            Log.e(TAG, "Error handling message", e)
        }
    }

    private fun handleNotification(obj: JsonObject) {
        when (val method = obj.get("method")?.asString ?: return) {
            "textDocument/publishDiagnostics" -> {
                val params = obj.getAsJsonObject("params") ?: return
                val uri    = params.get("uri")?.asString ?: return
                val diags  = params.getAsJsonArray("diagnostics") ?: JsonArray()
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
                // Ignore progress tokens
            }
            else -> Log.v(TAG, "Unhandled notification: $method")
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Convert a [File] to a `file://` URI string. */
    private fun File.toUri(): String = "file://${absolutePath}"
}