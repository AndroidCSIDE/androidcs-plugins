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
import com.nullij.androidcodestudio.plugins.api.LanguageServerClient
import com.nullij.androidcodestudio.plugins.api.PluginApi
import com.nullij.androidcodestudio.plugins.api.PluginLanguageServerSpec
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

/**
 * Manages the TypeScript/JavaScript LSP server (typescript-language-server) for ACS.
 *
 * Launched via: typescript-language-server --stdio
 *
 * Handles both TypeScript and JavaScript. Register separate instances for each language ID
 * ("typescript", "javascript", "typescriptreact", "javascriptreact") as needed.
 *
 * Capabilities:
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
 * - textDocument/publishDiagnostics (pushed)
 *
 * @author nullij @ https://github.com/nullij
 */

class TypeScriptLanguageServerManager(override val languageId: String = "typescript") :
    PluginLanguageServerSpec {

    companion object {
        private const val TAG = "TSLSP-Manager"
        private const val BUFFER_SIZE = 32768
        private const val MAX_CONTENT_LENGTH = 10_485_760
    }

    private val gson = Gson()

    private var serverProcess: Process? = null
    private var writer: BufferedWriter? = null
    private var client: TypeScriptLanguageClient? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nextId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    @Volatile private var running = false
    @Volatile private var initializing = false

    private val diagnosticsCache = ConcurrentHashMap<String, JsonArray>()

    fun getCachedDiagnostics(uri: String): JsonArray? = diagnosticsCache[uri]

    // ─── PluginLanguageServerSpec ─────────────────────────────────────────────

    override fun start(): Boolean = runBlocking {
        Log.d(TAG, "start() called, running=$running")
        if (running) {
            Log.d(TAG, "Server already running, returning true")
            return@runBlocking true
        }
        initializing = true
        val tempDir = File(PluginApi.environment.homeDir, ".unneeded/tmp")
        tempDir.mkdirs()
        try {
            Log.d(TAG, "Starting TypeScript language server...")
            serverProcess =
                PluginApi.process
                    .builder()
                    .command("/bin/typescript-language-server", "--stdio")
                    .attachDir(
                        hostDir = java.io.File(PluginApi.environment.homeDir, "lab"),
                        mountAt = "/root/lab",
                    )
                    .attachDir(hostDir = PluginApi.environment.tmpDir, mountAt = "${tempDir}")
                    .attachStorage()
                    .withEnv(
                        mapOf(
                            "HOME" to "/root",
                            "USER" to "root",
                            "TMPDIR" to "${tempDir}",
                            "PATH" to
                                "/bin:/usr/bin:/root/acslab/packages/official/nodejs-v25.9.0/node-v25.9.0-linux-arm64/bin",
                        )
                    )
                    .launch()

            Log.d(TAG, "Process launched successfully, alive=${serverProcess?.isAlive}")
            writer =
                BufferedWriter(
                    OutputStreamWriter(serverProcess!!.outputStream, StandardCharsets.UTF_8),
                    BUFFER_SIZE,
                )
            Log.d(TAG, "Writer created, starting reader threads...")
            startReaderThread()
            startErrorReaderThread()
            delay(1200)

            Log.d(TAG, "Sending initialize request...")
            val initialized = sendInitialize()
            Log.d(TAG, "Initialize result: $initialized")
            return@runBlocking if (initialized) {
                Log.d(TAG, "Server initialized successfully, creating client")
                client = TypeScriptLanguageClient(this@TypeScriptLanguageServerManager)
                running = true
                Log.d(TAG, "Server is now running")
                true
            } else {
                Log.e(TAG, "Failed to initialize server")
                stopInternal()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during server start", e)
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
            Log.d(TAG, "stopInternal() called")
            try {
                running = false
                try {
                    Log.d(TAG, "Sending shutdown request...")
                    sendRequest("shutdown", JsonObject())
                    sendNotification("exit", JsonObject())
                    delay(400)
                } catch (e: Exception) {
                    Log.w(TAG, "Exception during shutdown", e)
                }
                try {
                    writer?.close()
                } catch (_: Exception) {}
                serverProcess?.destroy()
                serverProcess?.waitFor()
                serverProcess = null
                client = null
                pendingRequests.clear()
            } catch (_: Exception) {}
        }

    override fun getClient(): LanguageServerClient =
        client ?: error("getClient() called before server is started")

    fun destroy() {
        scope.cancel()
        stop()
    }

    // ─── LSP initialize handshake ─────────────────────────────────────────────

    private suspend fun sendInitialize(): Boolean {
        Log.d(TAG, "sendInitialize() called")
        return try {
            val params =
                JsonObject().apply {
                    addProperty("processId", android.os.Process.myPid())
                    addProperty("rootUri", "file:///root/lab")
                    add(
                        "workspaceFolders",
                        JsonArray().apply {
                            add(
                                JsonObject().apply {
                                    addProperty("uri", "file:///root/lab")
                                    addProperty("name", "lab")
                                }
                            )
                        },
                    )
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
                                                    addProperty("insertReplaceSupport", true)
                                                    add(
                                                        "documentationFormat",
                                                        JsonArray().apply {
                                                            add("markdown")
                                                            add("plaintext")
                                                        },
                                                    )
                                                    add(
                                                        "resolveSupport",
                                                        JsonObject().apply {
                                                            add(
                                                                "properties",
                                                                JsonArray().apply {
                                                                    add("documentation")
                                                                    add("detail")
                                                                    add("additionalTextEdits")
                                                                },
                                                            )
                                                        },
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
                                                JsonArray().apply {
                                                    add("markdown")
                                                    add("plaintext")
                                                },
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
                                                        JsonArray().apply {
                                                            add("markdown")
                                                            add("plaintext")
                                                        },
                                                    )
                                                    add(
                                                        "parameterInformation",
                                                        JsonObject().apply {
                                                            addProperty("labelOffsetSupport", true)
                                                        },
                                                    )
                                                    addProperty("activeParameterSupport", true)
                                                },
                                            )
                                            addProperty("contextSupport", true)
                                        },
                                    )
                                    add(
                                        "definition",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                        },
                                    )
                                    add(
                                        "references",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                        },
                                    )
                                    add(
                                        "documentHighlight",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                        },
                                    )
                                    add(
                                        "documentSymbol",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                            addProperty("hierarchicalDocumentSymbolSupport", true)
                                            add(
                                                "symbolKind",
                                                JsonObject().apply {
                                                    add(
                                                        "valueSet",
                                                        JsonArray().apply {
                                                            for (i in 1..26) add(i)
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                    add(
                                        "formatting",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                        },
                                    )
                                    add(
                                        "rangeFormatting",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                        },
                                    )
                                    add(
                                        "rename",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                            addProperty("prepareSupport", true)
                                        },
                                    )
                                    add(
                                        "codeAction",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                            add(
                                                "resolveSupport",
                                                JsonObject().apply {
                                                    add(
                                                        "properties",
                                                        JsonArray().apply { add("edit") },
                                                    )
                                                },
                                            )
                                            add(
                                                "codeActionLiteralSupport",
                                                JsonObject().apply {
                                                    add(
                                                        "codeActionKind",
                                                        JsonObject().apply {
                                                            add(
                                                                "valueSet",
                                                                JsonArray().apply {
                                                                    add("")
                                                                    add("quickfix")
                                                                    add("refactor")
                                                                    add("refactor.extract")
                                                                    add("refactor.inline")
                                                                    add("refactor.rewrite")
                                                                    add("source")
                                                                    add("source.organizeImports")
                                                                    add("source.fixAll")
                                                                },
                                                            )
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
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
                                            addProperty("versionSupport", true)
                                            add(
                                                "tagSupport",
                                                JsonObject().apply {
                                                    add(
                                                        "valueSet",
                                                        JsonArray().apply {
                                                            add(1)
                                                            add(2)
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                            add(
                                "workspace",
                                JsonObject().apply {
                                    addProperty("applyEdit", true)
                                    addProperty("workspaceFolders", true)
                                    addProperty("configuration", false)
                                    add(
                                        "executeCommand",
                                        JsonObject().apply {
                                            addProperty("dynamicRegistration", false)
                                        },
                                    )
                                    add(
                                        "workspaceEdit",
                                        JsonObject().apply {
                                            addProperty("documentChanges", true)
                                            addProperty("failureHandling", "textOnlyTransactional")
                                        },
                                    )
                                },
                            )
                        },
                    )
                    add(
                        "initializationOptions",
                        JsonObject().apply {
                            addProperty("hostInfo", "ACS")
                            add(
                                "preferences",
                                JsonObject().apply {
                                    addProperty("includeInlayParameterNameHints", "all")
                                    addProperty(
                                        "includeInlayParameterNameHintsWhenArgumentMatchesName",
                                        false,
                                    )
                                    addProperty("includeInlayFunctionParameterTypeHints", true)
                                    addProperty("includeInlayVariableTypeHints", false)
                                    addProperty(
                                        "includeInlayVariableTypeHintsWhenTypeMatchesName",
                                        false,
                                    )
                                    addProperty("includeInlayPropertyDeclarationTypeHints", false)
                                    addProperty("includeInlayFunctionLikeReturnTypeHints", true)
                                    addProperty("includeInlayEnumMemberValueHints", true)
                                    addProperty("includeCompletionsForModuleExports", true)
                                    addProperty("includeCompletionsWithInsertText", true)
                                    addProperty("includeAutomaticOptionalChainCompletions", true)
                                    addProperty("allowIncompleteCompletions", true)
                                    addProperty("importModuleSpecifierPreference", "shortest")
                                },
                            )
                        },
                    )
                }
            Log.d(TAG, "Sending initialize request with params...")
            val response = sendRequest("initialize", params)
            if (response == null) {
                Log.e(TAG, "Initialize request returned null")
                return false
            }
            Log.d(TAG, "Initialize response received: $response")
            sendNotification("initialized", JsonObject())
            Log.d(TAG, "Initialized notification sent")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception during initialize", e)
            false
        }
    }

    // ─── Document lifecycle ───────────────────────────────────────────────────

    override fun openDocument(file: File): Boolean {
        Log.d(TAG, "openDocument: ${file.absolutePath}")
        if (!running) {
            Log.w(TAG, "Cannot open document - server not running")
            return false
        }
        val content =
            try {
                file.readText()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read file: ${file.absolutePath}", e)
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
                            addProperty("languageId", languageId)
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
        Log.d(TAG, "closeDocument: ${file.absolutePath}")
        if (!running) {
            Log.w(TAG, "Cannot close document - server not running")
            return
        }
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
        Log.d(
            TAG,
            "documentChanged: ${file.absolutePath}, version=$version, contentLength=${content.length}",
        )
        if (!running) {
            Log.w(TAG, "Cannot update document - server not running")
            return
        }
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
                            add(JsonObject().apply { addProperty("text", content) })
                        },
                    )
                },
            )
        }
    }

    // ─── JSON-RPC transport ───────────────────────────────────────────────────

    internal suspend fun sendRequest(method: String, params: JsonObject): JsonObject? {
        Log.v(TAG, "sendRequest: $method, params=$params")
        return withContext(Dispatchers.IO) {
            try {
                val id = nextId.getAndIncrement()
                Log.v(TAG, "Request ID: $id for method: $method")
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
                Log.v(TAG, "Request $id sent: $method")
                val result = withTimeout(30_000) { deferred.await() }
                Log.v(TAG, "Request $id response: $result")
                result
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Request $method timed out")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Request $method failed", e)
                null
            }
        }
    }

    internal fun sendNotification(method: String, params: JsonObject) {
        Log.v(TAG, "sendNotification: $method")
        try {
            val notification =
                JsonObject().apply {
                    addProperty("jsonrpc", "2.0")
                    addProperty("method", method)
                    add("params", params)
                }
            writeMessage(gson.toJson(notification))
            Log.v(TAG, "Notification sent: $method")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification: $method", e)
        }
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
        Log.d(TAG, "Starting reader thread...")
        Thread(
                {
                    try {
                        val inputStream = serverProcess!!.inputStream
                        Log.d(TAG, "Reader thread started")
                        while (running || initializing) {
                            var contentLength = -1
                            while (true) {
                                val line = readLineFromStream(inputStream) ?: return@Thread
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:"))
                                    contentLength = line.substring(15).trim().toInt()
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
                                val message = String(buffer, StandardCharsets.UTF_8)
                                Log.v(TAG, "Received message: ${message.take(200)}...")
                                handleMessage(message)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Reader thread exception", e)
                    }
                    Log.d(TAG, "Reader thread exiting")
                },
                "tslsp-reader",
            )
            .apply { priority = Thread.MAX_PRIORITY }
            .start()
    }

    private fun startErrorReaderThread() {
        Log.d(TAG, "Starting error reader thread...")
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
                            Log.w(TAG, "Server stderr: $line")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reader thread exception", e)
                    }
                    Log.d(TAG, "Error reader thread exiting")
                },
                "tslsp-error-reader",
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
        Log.v(TAG, "handleMessage: ${json.take(300)}")
        try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            when {
                // Response to a request we sent
                obj.has("id") && (obj.has("result") || obj.has("error")) -> {
                    val id = obj.get("id").asInt
                    Log.v(TAG, "Received response for request $id")
                    val deferred = pendingRequests.remove(id)
                    if (obj.has("error")) {
                        val errorMsg = obj.getAsJsonObject("error").get("message").asString
                        Log.e(TAG, "Request $id returned error: $errorMsg")
                        deferred?.completeExceptionally(Exception(errorMsg))
                    } else {
                        val result = obj.get("result")
                        val wrapped = JsonObject()
                        if (result != null && !result.isJsonNull) wrapped.add("result", result)
                        deferred?.complete(wrapped)
                    }
                }
                // Server-initiated request (e.g. workspace/applyEdit) reply with success
                obj.has("id") && obj.has("method") -> {
                    val reqId = obj.get("id")
                    val reply =
                        JsonObject().apply {
                            addProperty("jsonrpc", "2.0")
                            add("id", reqId)
                            add("result", JsonObject().apply { addProperty("applied", true) })
                        }
                    writeMessage(gson.toJson(reply))
                }
                // Notification
                obj.has("method") -> {
                    val method = obj.get("method")?.asString ?: "unknown"
                    Log.v(TAG, "Received notification: $method")
                    handleNotification(obj)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message", e)
        }
    }

    private fun handleNotification(obj: JsonObject) {
        val method = obj.get("method")?.asString ?: return
        Log.d(TAG, "handleNotification: $method")
        when (method) {
            "textDocument/publishDiagnostics" -> {
                val params = obj.getAsJsonObject("params") ?: return
                val uri = params.get("uri")?.asString ?: return
                val diags = params.getAsJsonArray("diagnostics") ?: JsonArray()
                Log.d(TAG, "Received ${diags.size()} diagnostics for $uri")
                diagnosticsCache[uri] = diags
            }
            "$/progress" -> Log.v(TAG, "Progress notification")
            "window/logMessage" ->
                Log.d(TAG, "Server log: ${obj.getAsJsonObject("params")?.get("message")?.asString}")
            "window/showMessage" ->
                Log.d(
                    TAG,
                    "Server message: ${obj.getAsJsonObject("params")?.get("message")?.asString}",
                )
            else -> Log.w(TAG, "Unhandled notification: $method")
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun File.toUri(): String {
        val hostLab = java.io.File(PluginApi.environment.homeDir, "lab").canonicalPath
        val canonical = this.canonicalPath
        val rootfsPath =
            if (canonical.startsWith(hostLab)) {
                "/root/lab" + canonical.removePrefix(hostLab)
            } else {
                canonical // fallback! it shouldn't happen for project files
            }
        return "file://$rootfsPath"
    }
}
