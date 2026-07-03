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
import com.nullij.androidcodestudio.plugins.api.LanguageServerClient
import com.nullij.androidcodestudio.plugins.api.PluginApi
import com.nullij.androidcodestudio.plugins.api.PluginLanguageServerSpec
import java.io.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

/**
 * Manages the clangd language server for ACS.
 *
 * Launched via: <clangdPath> --stdio
 *
 * Capabilities supported by clangd:
 * - textDocument/completion (members, functions, macros, includes)
 * - textDocument/hover (type information, documentation)
 * - textDocument/signatureHelp (function parameter hints)
 * - textDocument/definition (go-to definition)
 * - textDocument/references (find all usages)
 * - textDocument/documentSymbol (classes, functions, variables)
 * - textDocument/documentHighlight (highlight symbol usages)
 * - textDocument/formatting (clang-format integration)
 * - textDocument/rangeFormatting
 * - textDocument/rename (symbol rename)
 * - textDocument/codeAction (quick fixes, include insertion)
 * - textDocument/inlayHint (parameter names, deduced types)
 * - textDocument/publishDiagnostics (errors/warnings, pushed)
 *
 * @author nullij @ https://github.com/nullij
 */
class ClangLanguageServerManager(private val clangdPath: String) : PluginLanguageServerSpec {

    private val gson = Gson()

    private var serverProcess: Process? = null
    private var writer: BufferedWriter? = null
    private var client: ClangLanguageClient? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nextId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    @Volatile private var running = false
    @Volatile private var initializing = false

    private val diagnosticsCache = ConcurrentHashMap<String, JsonArray>()

    fun getCachedDiagnostics(uri: String): JsonArray? = diagnosticsCache[uri]

    companion object {
        private const val TAG = "ClangLSP"
        private const val BUFFER_SIZE = 32768
        private const val MAX_CONTENT_LENGTH = 10_485_760
    }

    override val languageId: String = "c"

    override fun start(): Boolean = runBlocking {
        if (running) {
            android.util.Log.i(TAG, "start() called but server is already running — skipping")
            return@runBlocking true
        }

        initializing = true
        android.util.Log.i(TAG, "start() - launching clangd at $clangdPath")

        try {
            val projectDir = PluginApi.environment.openProjectDir
            
            val extraArgs = ClangConfig.extraArgsList()
            val homeDir = PluginApi.environment.homeDir
            
            var builder = PluginApi.process
                .builder()
                .command(
                    clangdPath,
                    "--log=error",
                    "--clang-tidy",
                    "--pch-storage=memory",
                    *extraArgs.toTypedArray()
                )
                .attachStorage()
                .attachAndroidSdk()
            
            if (projectDir != null) builder = builder.attachDir(projectDir)
            if (homeDir != null && java.io.File(clangdPath).canonicalPath
                    .startsWith(homeDir.canonicalPath)) {
                builder = builder.attachDir(homeDir, "/root")
            }
            
            serverProcess = builder
                .withEnv(mapOf(
                    "HOME" to "/root",
                    "USER" to "root",
                    "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                ))
                .launch()

            android.util.Log.i(TAG, "process launched — setting up I/O streams")

            writer = BufferedWriter(
                OutputStreamWriter(serverProcess!!.outputStream, StandardCharsets.UTF_8),
                BUFFER_SIZE,
            )

            startReaderThread()
            startErrorReaderThread()

            android.util.Log.i(TAG, "waiting 800 ms for clangd to boot…")
            delay(800)
            android.util.Log.i(TAG, "boot wait done — sending initialize")

            val initialized = sendInitialize()

            return@runBlocking if (initialized) {
                client = ClangLanguageClient(this@ClangLanguageServerManager)
                running = true
                android.util.Log.i(TAG, "clangd started successfully ✓")
                true
            } else {
                android.util.Log.e(TAG, "sendInitialize() returned false — aborting start")
                stopInternal()
                false
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "exception during start(): ${e::class.simpleName}: ${e.message}", e)
            stopInternal()
            false
        } finally {
            initializing = false
        }
    }

    override fun stop() = runBlocking { stopInternal() }

    override fun isRunning(): Boolean = running

    private fun writeClangdConfig(projectDir: java.io.File) {
        val config = java.io.File(projectDir, ".clangd")
        if (config.exists()) return
        config.writeText("""
            CompileFlags:
              Add:
                - "-I${projectDir.absolutePath}"
              CompilationDatabase: "${projectDir.absolutePath}"
        """.trimIndent())
    }

    private suspend fun stopInternal(): Unit = withContext(Dispatchers.IO) {
        try {
            running = false
            try {
                sendRequest("shutdown", JsonObject())
                sendNotification("exit", JsonObject())
                delay(400)
            } catch (e: Exception) {}

            try { writer?.close() } catch (_: Exception) {}

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

    private suspend fun sendInitialize(): Boolean {
        return try {
            val params = JsonObject().apply {
                addProperty("processId", android.os.Process.myPid())
                addProperty("rootUri", "file://${PluginApi.environment.openProjectDir?.absolutePath ?: ""}")

                add("capabilities", JsonObject().apply {
                    add("textDocument", JsonObject().apply {
                        add("synchronization", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                            addProperty("willSave", false)
                            addProperty("willSaveWaitUntil", false)
                            addProperty("didSave", true)
                        })
                        add("completion", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                            add("completionItem", JsonObject().apply {
                                addProperty("snippetSupport", true)
                                addProperty("documentationFormat", "markdown")
                            })
                        })
                        add("hover", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                            add("contentFormat", JsonArray().apply {
                                add("markdown")
                                add("plaintext")
                            })
                        })
                        add("signatureHelp", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                        })
                        add("definition", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                        })
                        add("references", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                        })
                        add("documentSymbol", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                        })
                        add("documentHighlight", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                        })
                        add("formatting", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                        })
                        add("rangeFormatting", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                        })
                        add("rename", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                        })
                        add("codeAction", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                            addProperty("isPreferredSupport", true)
                            addProperty("disabledSupport", false)
                            add("codeActionLiteralSupport", JsonObject().apply {
                                add("codeActionKind", JsonObject().apply {
                                    add("valueSet", JsonArray().apply {
                                        add("")
                                        add("quickfix")
                                        add("refactor")
                                        add("refactor.extract")
                                        add("refactor.inline")
                                        add("refactor.rewrite")
                                        add("source")
                                        add("source.organizeImports")
                                    })
                                })
                            })
                            add("resolveSupport", JsonObject().apply {
                                add("properties", JsonArray().apply {
                                    add("edit")
                                })
                            })
                        })
                        add("inlayHint", JsonObject().apply {
                            addProperty("dynamicRegistration", false)
                        })
                        add("publishDiagnostics", JsonObject().apply {
                            addProperty("relatedInformation", true)
                        })
                    })
                    add("workspace", JsonObject().apply {
                        addProperty("applyEdit", true)
                    })
                })
            }

            val response = sendRequest("initialize", params) ?: return false
            sendNotification("initialized", JsonObject())
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "sendInitialize exception: ${e.message}", e)
            false
        }
    }

    override fun openDocument(file: java.io.File): Boolean {
        if (!running) return false
        return try {
            val content = file.readText(StandardCharsets.UTF_8)
            sendNotification("textDocument/didOpen", JsonObject().apply {
                add("textDocument", JsonObject().apply {
                    addProperty("uri", file.toUri())
                    addProperty("languageId", languageId)
                    addProperty("version", 1)
                    addProperty("text", content)
                })
            })
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun closeDocument(file: java.io.File) {
        if (!running) return
        sendNotification("textDocument/didClose", JsonObject().apply {
            add("textDocument", JsonObject().apply {
                addProperty("uri", file.toUri())
            })
        })
    }

    override fun documentChanged(file: java.io.File, content: String, version: Int) {
        if (!running) return
        sendNotification("textDocument/didChange", JsonObject().apply {
            add("textDocument", JsonObject().apply {
                addProperty("uri", file.toUri())
                addProperty("version", version)
            })
            add("contentChanges", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("text", content)
                })
            })
        })
    }

    internal suspend fun sendRequest(method: String, params: JsonObject): JsonObject? {
        return withContext(Dispatchers.IO) {
            try {
                val id = nextId.getAndIncrement()
                val deferred = CompletableDeferred<JsonObject>()
                pendingRequests[id] = deferred

                val request = JsonObject().apply {
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
            val notification = JsonObject().apply {
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

    private fun startReaderThread() {
        Thread({
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
                        val read = inputStream.read(buffer, totalRead, contentLength - totalRead)
                        if (read < 0) break
                        totalRead += read
                    }

                    if (totalRead == contentLength) {
                        handleMessage(String(buffer, StandardCharsets.UTF_8))
                    }
                }
            } catch (e: Exception) {}
        }, "clangd-reader").apply { priority = Thread.MAX_PRIORITY }.start()
    }

    private fun startErrorReaderThread() {
        Thread({
            try {
                val reader = BufferedReader(
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
        }, "clangd-error-reader").start()
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
                if (obj.has("method")) {
                    handleServerRequest(obj)
                    return
                }
                val id = obj.get("id").asInt
                val deferred = pendingRequests.remove(id)

                if (obj.has("error")) {
                    val error = obj.getAsJsonObject("error")
                    android.util.Log.e(TAG, "LSP error for id=$id: code=${error.get("code")}, message=${error.get("message")}")
                    deferred?.completeExceptionally(Exception(error.get("message").asString))
                } else {
                    val result = obj.get("result")
                    val wrapped = JsonObject()
                    if (result != null && !result.isJsonNull) wrapped.add("result", result)
                    deferred?.complete(wrapped)
                }
            } else if (obj.has("method")) {
                handleNotification(obj)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "failed to parse server message: ${e.message} — raw: ${json.take(200)}")
        }
    }

    private fun handleServerRequest(obj: JsonObject) {
        val method = obj.get("method")?.asString ?: return
        val id = obj.get("id")?.takeIf { !it.isJsonNull }?.asInt ?: return
        when (method) {
            "workspace/applyEdit" -> {
                try {
                    val params = obj.get("params")?.asJsonObject
                    if (params == null) {
                        sendApplyEditResponse(id, applied = false)
                        return
                    }
                    val editObj = params.get("edit")?.asJsonObject
                    if (editObj == null) {
                        sendApplyEditResponse(id, applied = false)
                        return
                    }
                    val changes = mutableMapOf<String, List<com.nullij.androidcodestudio.editor.models.lsp.TextEdit>>()

                    if (editObj.has("documentChanges") && !editObj.get("documentChanges").isJsonNull) {
                        editObj.getAsJsonArray("documentChanges").forEach { changeEl ->
                            try {
                                val changeObj = changeEl.asJsonObject
                                if (!changeObj.has("textDocument") || !changeObj.has("edits")) return@forEach
                                val fileUri = changeObj.getAsJsonObject("textDocument").get("uri")?.asString
                                    ?: return@forEach
                                val list = changeObj.getAsJsonArray("edits").mapNotNull { editEl ->
                                    parseApplyEditTextEdit(editEl.asJsonObject)
                                }
                                changes[fileUri] = (changes[fileUri] ?: emptyList()) + list
                            } catch (_: Exception) { }
                        }
                    }
                    if (changes.isEmpty() && editObj.has("changes") && !editObj.get("changes").isJsonNull) {
                        editObj.getAsJsonObject("changes").entrySet().forEach { (fileUri, editsEl) ->
                            try {
                                if (!editsEl.isJsonArray) return@forEach
                                changes[fileUri] = editsEl.asJsonArray.mapNotNull { el ->
                                    parseApplyEditTextEdit(el.asJsonObject)
                                }
                            } catch (_: Exception) { }
                        }
                    }

                    val workspaceEdit = com.nullij.androidcodestudio.editor.models.lsp.WorkspaceEdit(changes = changes)
                    client?.dispatchApplyEditFromServer(workspaceEdit)
                    sendApplyEditResponse(id, applied = true)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "workspace/applyEdit failed", e)
                    sendApplyEditResponse(id, applied = false)
                }
            }
            else -> {
                android.util.Log.w(TAG, "Unhandled server request: $method")
                sendErrorResponse(id, -32601, "Method not found: $method")
            }
        }
    }

    private fun parseApplyEditTextEdit(editObj: com.google.gson.JsonObject): com.nullij.androidcodestudio.editor.models.lsp.TextEdit? {
        return try {
            val rangeObj = editObj.getAsJsonObject("range") ?: return null
            val start = rangeObj.getAsJsonObject("start")
            val end = rangeObj.getAsJsonObject("end")
            com.nullij.androidcodestudio.editor.models.lsp.TextEdit(
                range = com.nullij.androidcodestudio.editor.models.lsp.Range(
                    start = com.nullij.androidcodestudio.editor.models.lsp.Position(
                        line = start.get("line").asInt,
                        character = start.get("character").asInt,
                    ),
                    end = com.nullij.androidcodestudio.editor.models.lsp.Position(
                        line = end.get("line").asInt,
                        character = end.get("character").asInt,
                    ),
                ),
                newText = editObj.get("newText")?.asString ?: "",
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun sendApplyEditResponse(id: Int, applied: Boolean) {
        try {
            val response = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", id)
                add("result", JsonObject().apply {
                    addProperty("applied", applied)
                })
            }
            val json = gson.toJson(response)
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            val header = "Content-Length: ${bytes.size}\r\n\r\n"
            synchronized(writer!!) {
                writer?.write(header)
                writer?.write(json)
                writer?.flush()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to send applyEdit response", e)
        }
    }

    private fun sendErrorResponse(id: Int, code: Int, message: String) {
        try {
            val response = JsonObject().apply {
                addProperty("jsonrpc", "2.0")
                addProperty("id", id)
                add("error", JsonObject().apply {
                    addProperty("code", code)
                    addProperty("message", message)
                })
            }
            val json = gson.toJson(response)
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            val header = "Content-Length: ${bytes.size}\r\n\r\n"
            synchronized(writer!!) {
                writer?.write(header)
                writer?.write(json)
                writer?.flush()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to send error response", e)
        }
    }

    private fun handleNotification(obj: JsonObject) {
        when (obj.get("method")?.asString ?: return) {
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
            "$/progress" -> {}
            else -> {}
        }
    }

    private fun java.io.File.toUri(): String = "file://${absolutePath}"
}