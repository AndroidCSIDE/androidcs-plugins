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
/*
 * @author nullij @ https://github.com/nullij
 */
class JsonLanguageServerManager : PluginLanguageServerSpec {

    override val languageId: String = "json"

    private val gson = Gson()

    private var serverProcess: Process? = null
    private var writer: BufferedWriter? = null
    private var client: JsonLanguageClient? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val nextId = AtomicInteger(1)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    @Volatile private var running = false
    @Volatile private var initializing = false

    private val diagnosticsCache = ConcurrentHashMap<String, JsonArray>()

    fun getCachedDiagnostics(uri: String): JsonArray? = diagnosticsCache[uri]

    companion object {
        private const val BUFFER_SIZE = 32768
        private const val MAX_CONTENT_LENGTH = 10_485_760
    }

    override fun start(): Boolean = runBlocking {
        if (running) return@runBlocking true
        initializing = true
        try {
            val binFile = File("$PREFIX/vscode-json-language-server")
            val nodeFile = File("$PREFIX/node")
            val pb = PluginApi.process.builder()

            val builder = if (nodeFile.exists() && binFile.exists()) {
                pb.command(nodeFile.absolutePath, binFile.absolutePath, "--stdio")
            } else if (binFile.exists()) {
                pb.command(binFile.absolutePath, "--stdio")
            } else {
                pb.command("vscode-json-language-server", "--stdio")
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
            delay(1200)

            val initialized = sendInitialize()
            return@runBlocking if (initialized) {
                client = JsonLanguageClient(this@JsonLanguageServerManager)
                running = true
                true
            } else {
                stopInternal()
                false
            }
        } catch (_: Exception) {
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
                } catch (_: Exception) {}
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
                                                        JsonArray().apply {
                                                            add("markdown")
                                                            add("plaintext")
                                                        },
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
                                                JsonArray().apply {
                                                    add("markdown")
                                                    add("plaintext")
                                                },
                                            )
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
                            add("workspace", JsonObject().apply { addProperty("applyEdit", true) })
                        },
                    )
                    add(
                        "initializationOptions",
                        JsonObject().apply {
                            add("schemas", JsonArray())
                            addProperty("resultLimit", 5000)
                        },
                    )
                }
            sendRequest("initialize", params) ?: return false
            sendNotification("initialized", JsonObject())
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun openDocument(file: File): Boolean {
        if (!running) return false
        val content =
            try {
                file.readText()
            } catch (_: Exception) {
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
                            addProperty("languageId", "json")
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
                            add(JsonObject().apply { addProperty("text", content) })
                        },
                    )
                },
            )
        }
    }

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
            } catch (_: TimeoutCancellationException) {
                null
            } catch (_: Exception) {
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
        } catch (_: Exception) {}
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
                            if (totalRead == contentLength)
                                handleMessage(String(buffer, StandardCharsets.UTF_8))
                        }
                    } catch (_: Exception) {}
                },
                "jsonlsp-reader",
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
                        while (reader.readLine() != null) {}
                    } catch (_: Exception) {}
                },
                "jsonlsp-error-reader",
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
        try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            if (obj.has("id")) {
                val id = obj.get("id").asInt
                val deferred = pendingRequests.remove(id)
                if (obj.has("error")) {
                    deferred?.completeExceptionally(
                        Exception(obj.getAsJsonObject("error").get("message").asString)
                    )
                } else {
                    val result = obj.get("result")
                    val wrapped = JsonObject()
                    if (result != null && !result.isJsonNull) wrapped.add("result", result)
                    deferred?.complete(wrapped)
                }
            } else if (obj.has("method")) {
                handleNotification(obj)
            }
        } catch (_: Exception) {}
    }

    private fun handleNotification(obj: JsonObject) {
        when (obj.get("method")?.asString ?: return) {
            "textDocument/publishDiagnostics" -> {
                val params = obj.getAsJsonObject("params") ?: return
                val uri = params.get("uri")?.asString ?: return
                val diags = params.getAsJsonArray("diagnostics") ?: JsonArray()
                diagnosticsCache[uri] = diags
            }
            "$/progress" -> {}
        }
    }

    private fun File.toUri(): String = "file://${absolutePath}"
}
