/*
 *  This file is part of ACSAccessors.
 *
 *  ACSAccessors is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  ACSAccessors is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with ACSAccessors.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.nullij.androidcodestudio.internals

import android.content.Context
import java.io.File
import kotlinx.coroutines.*
import kotlin.coroutines.suspendCoroutine

/**
 * Provides reflection-based access to the IDE's LanguageServerRegistry for plugin use.
 * No compile-time dependency on any IDE LSP class is needed.
 *
 * Suspend functions on the registry (startServer, openDocument, etc.) are handled
 * internally — the accessor launches them on Dispatchers.IO and, where a return
 * value is needed, blocks until the result is available.
 *
 * Entry point: LSPAccessor.resolve(context)
 *
 * @author nullij @ https://github.com/nullij
 */
class LSPAccessor private constructor(private val registry: Any) {

    private val registryClass = registry.javaClass

    // ─── Discovery ───────────────────────────────────────────────────────────
    // LanguageServerRegistry is a private lateinit var on EditorActivity.
    // EditorActivity is always the running activity when a plugin fires, so we
    // grab it from the context and reflect the field off it.

    companion object {
        private const val ACTIVITY_CLASS_NAME =
            "com.nullij.androidcodestudio.activities.editor.EditorActivity"
        private const val REGISTRY_FIELD_NAME = "lspRegistry"

        /**
         * Resolve the live LanguageServerRegistry from the running EditorActivity
         * and return a ready-to-use LSPAccessor, or null if unavailable.
         *
         * Two-pass lookup:
         *  1. By source field name "lspRegistry" — fast, works on debug/non-obfuscated builds.
         *  2. By type fingerprint — walks all fields and matches the one whose declared type
         *     has both "hasServer" and "registerServer" methods. Survives R8 field renaming.
         */
        fun resolve(context: Context): LSPAccessor? {
            val activity = context as? android.app.Activity
                ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                ?: return null

            if (activity.javaClass.name != ACTIVITY_CLASS_NAME &&
                !activity.javaClass.superclasses().any { it.name == ACTIVITY_CLASS_NAME }
            ) {
                android.util.Log.e("LSPAccessor", "Running activity is not EditorActivity")
                return null
            }

            val registryInstance = findRegistryField(activity) ?: return null
            return LSPAccessor(registryInstance)
        }

        private fun findRegistryField(activity: android.app.Activity): Any? {
            // Pass 1: known source name
            val byName = runCatching {
                allDeclaredFields(activity.javaClass)
                    .first { it.name == REGISTRY_FIELD_NAME }
                    .apply { isAccessible = true }
                    .get(activity)
            }.getOrNull()

            if (byName != null) {
                android.util.Log.d("LSPAccessor", "Registry found by field name")
                return byName
            }

            // Pass 2: type fingerprint — R8 renamed the field but not the methods
            android.util.Log.d("LSPAccessor",
                "Field '$REGISTRY_FIELD_NAME' not found by name — scanning by type fingerprint")

            for (field in allDeclaredFields(activity.javaClass)) {
                if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
                val t = field.type
                if (t.isPrimitive) continue
                if (t.name.startsWith("android.") || t.name.startsWith("java.") ||
                    t.name.startsWith("kotlin.")  || t.name.startsWith("androidx.")) continue

                val hasRegister = t.declaredMethods.any { it.name == "registerServer" }
                val hasHas      = t.declaredMethods.any { it.name == "hasServer" }
                if (!hasRegister || !hasHas) continue

                return runCatching {
                    field.isAccessible = true
                    field.get(activity).also {
                        android.util.Log.d("LSPAccessor",
                            "Registry found via fingerprint: field '${field.name}' type '${t.name}'")
                    }
                }.getOrNull() ?: continue
            }

            android.util.Log.e("LSPAccessor",
                "Could not locate LanguageServerRegistry on EditorActivity")
            return null
        }

        private fun allDeclaredFields(clazz: Class<*>): List<java.lang.reflect.Field> {
            val fields = mutableListOf<java.lang.reflect.Field>()
            var c: Class<*>? = clazz
            while (c != null && c != Any::class.java) { fields += c.declaredFields; c = c.superclass }
            return fields
        }

        /** Walk the full superclass chain */
        private fun Class<*>.superclasses(): List<Class<*>> {
            val list = mutableListOf<Class<*>>()
            var current = this.superclass
            while (current != null) {
                list.add(current)
                current = current.superclass
            }
            return list
        }
    }

    // ─── Suspend-function helpers ────────────────────────────────────────────
    // Kotlin suspend functions compile with an extra Continuation parameter.
    // We can't invoke them with plain reflection. Instead we call them through
    // kotlinx.coroutines: runBlocking for calls that return a value we need
    // immediately, and fire-and-forget launch for void calls.

    /**
     * Invoke a suspend method that returns a value.  The method signature on
     * the compiled class is: fun methodName(...args, Continuation): Object
     * We pass a real Continuation obtained from runBlocking's coroutine.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> invokeSuspend(methodName: String, vararg args: Any?): T {
        return runBlocking(Dispatchers.IO) {
            suspendCoroutine<T> { cont ->
                val paramTypes = args.map { 
                    it?.javaClass ?: Any::class.java 
                }.toMutableList()
                // Add Continuation as the last parameter type
                paramTypes.add(kotlin.coroutines.Continuation::class.java as Class<Any>)

                val method = registryClass.getDeclaredMethod(methodName, *paramTypes.toTypedArray())
                method.isAccessible = true

                val allArgs = args.toMutableList()
                allArgs.add(cont)

                method.invoke(registry, *allArgs.toTypedArray())
            }
        }
    }

    /**
     * Invoke a suspend method that returns Unit (fire-and-forget).
     * Launches on IO and does not block the caller.
     */
    @Suppress("UNCHECKED_CAST")
    private fun launchSuspend(methodName: String, vararg args: Any?) {
        GlobalScope.launch(Dispatchers.IO) {
            suspendCoroutine<Unit> { cont ->
                val paramTypes = args.map { 
                    it?.javaClass ?: Any::class.java 
                }.toMutableList()
                // Add Continuation as the last parameter type
                paramTypes.add(kotlin.coroutines.Continuation::class.java as Class<Any>)

                val method = registryClass.getDeclaredMethod(methodName, *paramTypes.toTypedArray())
                method.isAccessible = true

                val allArgs = args.toMutableList()
                allArgs.add(cont)

                method.invoke(registry, *allArgs.toTypedArray())
            }
        }
    }

    // ─── Query (non-suspend, direct invoke) ──────────────────────────────────

    /** Whether a server is registered for this language (running or not) */
    fun hasServer(languageId: String): Boolean =
        registryClass.getDeclaredMethod("hasServer", String::class.java)
            .invoke(registry, languageId) as Boolean

    /** Whether the server for this language is currently running */
    fun isServerRunning(languageId: String): Boolean =
        registryClass.getDeclaredMethod("isServerRunning", String::class.java)
            .invoke(registry, languageId) as Boolean

    /**
     * Detect language ID from a File (uses extension).
     * Returns e.g. "kotlin", "java", "dart", or null.
     */
    fun detectLanguage(file: File): String? =
        registryClass.getDeclaredMethod("detectLanguage", File::class.java)
            .invoke(registry, file) as? String

    /**
     * Detect language ID from a file name string.
     * Returns e.g. "kotlin", "java", "dart", or null.
     */
    fun detectLanguage(fileName: String): String? =
        registryClass.getDeclaredMethod("detectLanguage", String::class.java)
            .invoke(registry, fileName) as? String

    /**
     * The set of language IDs that have a registered server
     * (mirrors the availableServers StateFlow's current value).
     */
    @Suppress("UNCHECKED_CAST")
    fun getAvailableServers(): Set<String> {
        // availableServers is a val StateFlow — compiled as a getter returning StateFlow.
        // StateFlow has a getValue() method that returns the current value.
        val stateFlow = registryClass.getDeclaredMethod("getAvailableServers")
            .invoke(registry)
        return stateFlow.javaClass.getDeclaredMethod("getValue")
            .invoke(stateFlow) as Set<String>
    }

    /**
     * The set of language IDs whose servers are currently running
     * (mirrors the runningServers StateFlow's current value).
     */
    @Suppress("UNCHECKED_CAST")
    fun getRunningServers(): Set<String> {
        val stateFlow = registryClass.getDeclaredMethod("getRunningServers")
            .invoke(registry)
        return stateFlow.javaClass.getDeclaredMethod("getValue")
            .invoke(stateFlow) as Set<String>
    }

    // ─── Lifecycle (suspend, blocking for result) ───────────────────────────

    /**
     * Start the language server for the given language ID.
     * Blocks until the server reports started (or fails).
     * Returns true on success.
     */
    fun startServer(languageId: String): Boolean =
        invokeSuspend("startServer", languageId)

    /**
     * Stop the language server for the given language ID.
     * Fire-and-forget — returns immediately, stop happens on IO.
     */
    fun stopServer(languageId: String) {
        launchSuspend("stopServer", languageId)
    }

    /** Stop every running language server. Fire-and-forget. */
    fun stopAllServers() {
        launchSuspend("stopAllServers")
    }

    // ─── Document lifecycle (suspend) ────────────────────────────────────────

    /**
     * Open a document in its appropriate language server.
     * If the server isn't running yet, this will start it first.
     * Blocks until the document is opened (or fails).
     * Returns true on success.
     */
    fun openDocument(file: File): Boolean =
        invokeSuspend("openDocument", file)

    /**
     * Notify the language server that a document has been closed.
     * Fire-and-forget.
     */
    fun closeDocument(file: File) {
        launchSuspend("closeDocument", file)
    }

    /**
     * Notify the language server that a document's content has changed.
     * Fire-and-forget.
     *
     * @param file       the file that changed
     * @param content    the new full content of the file
     * @param version    monotonically increasing version number
     */
    fun documentChanged(file: File, content: String, version: Int) {
        launchSuspend("documentChanged", file, content, version)
    }

    // ─── Server registration ─────────────────────────────────────────────────
    // Plugins register a new language server in two steps:
    //   1. registerServer() — tells LanguageServerRegistry about the manager so
    //      it can be started, stopped, and queried like any built-in server.
    //   2. The manager itself calls LSPManager.registerClient() when it starts,
    //      so the editor can route LSP feature requests to it.
    //
    // PluginLanguageServer is the only thing a plugin author needs to implement.
    // PluginServerManager wraps it and satisfies the LanguageServerManager
    // interface entirely through reflection — no IDE compile-time dependency.

    /**
     * Register a new language server contributed by a plugin.
     *
     * After registration the server behaves identically to a built-in one:
     * it appears in [getAvailableServers], can be started with [startServer],
     * and the editor will route completions / hover / diagnostics to it
     * automatically once it is running.
     *
     * @param languageId  the language this server handles, e.g. "rust", "go"
     * @param server      your [PluginLanguageServer] implementation
     */
    fun registerServer(languageId: String, server: PluginLanguageServer) {
        val manager = PluginServerManager(languageId, server)

        // registerServer(languageId, manager) on LanguageServerRegistry.
        // The manager is passed as the LanguageServerManager interface type —
        // we need the interface class, not the concrete proxy class, for the
        // method lookup.
        val managerInterfaceClass = try {
            Class.forName("com.nullij.androidcodestudio.lsp.LanguageServerManager")
        } catch (e: ClassNotFoundException) {
            android.util.Log.e("LSPAccessor", "LanguageServerManager interface not found", e)
            return
        }

        val method = registryClass.getDeclaredMethod(
            "registerServer",
            String::class.java,
            managerInterfaceClass
        ).apply { isAccessible = true }

        method.invoke(registry, languageId, manager.proxy)
    }

    /**
     * Unregister a previously-registered plugin language server.
     * If the server is running it will be stopped first.
     */
    fun unregisterServer(languageId: String) {
        if (isServerRunning(languageId)) stopServer(languageId)

        val method = registryClass.getDeclaredMethod(
            "unregisterServer",
            String::class.java
        ).apply { isAccessible = true }

        method.invoke(registry, languageId)

        // Also remove the client from LSPManager
        val lspManager = getLSPManagerInstance() ?: return
        lspManager.javaClass.getDeclaredMethod("unregisterClient", String::class.java)
            .apply { isAccessible = true }
            .invoke(lspManager, languageId)
    }

    // ─── Extension registration ───────────────────────────────────────────────
    // Teach the IDE registry which file extensions belong to a language server.
    // Without this, detectLanguage() returns null for any extension not already
    // in the registry's built-in table, and openDocument / documentChanged never
    // route to the plugin server.
    //
    // Both methods use a two-pass lookup: name first (fast, works on non-obfuscated
    // builds), then parameter-signature fingerprint (survives R8 method renaming).

    /**
     * Map a file extension to a language ID.
     *
     * The extension should be given without a leading dot and is normalised to
     * lower case internally (e.g. `"sh"`, `"bash"`, `"zsh"`, `"env"`).
     *
     * Custom mappings override built-in ones, so this can also redirect an
     * existing extension to a different server if needed.
     *
     * Call this **before** [registerServer] so that any already-open files of
     * that type are immediately routable.
     *
     * ```kotlin
     * val lsp = LSPAccessor.resolve(context) ?: return
     * lsp.registerExtension("sh",   "shellscript")
     * lsp.registerExtension("bash", "shellscript")
     * lsp.registerExtension("zsh",  "shellscript")
     * lsp.registerExtension("env",  "shellscript")
     * lsp.registerServer("shellscript", BashLanguageServerManager(context))
     * ```
     *
     * @param extension  file extension without leading dot, e.g. `"sh"`
     * @param languageId the language server to route this extension to
     */
    fun registerExtension(extension: String, languageId: String) {
        val method = findRegistryMethod(
            name           = "registerExtension",
            paramTypes     = arrayOf(String::class.java, String::class.java),
            // Fingerprint: the method that takes two Strings and returns Unit,
            // with a name containing "registerExtension" or "Extension".
            fingerprint    = { m ->
                m.parameterCount == 2 &&
                m.parameterTypes.all { it == String::class.java } &&
                m.returnType == Void.TYPE &&
                (m.name.contains("register", ignoreCase = true) ||
                 m.name.contains("Extension", ignoreCase = true))
            }
        )

        if (method != null) {
            method.invoke(registry, extension, languageId)
        } else {
            android.util.Log.e(
                "LSPAccessor",
                "registerExtension not found on LanguageServerRegistry — " +
                "is the IDE built with the updated registry?"
            )
        }
    }

    /**
     * Remove a previously-registered custom extension mapping.
     * Has no effect on built-in extension mappings.
     *
     * @param extension  the extension to remove (leading dot optional, case-insensitive)
     */
    fun unregisterExtension(extension: String) {
        val method = findRegistryMethod(
            name       = "unregisterExtension",
            paramTypes = arrayOf(String::class.java),
            fingerprint = { m ->
                m.parameterCount == 1 &&
                m.parameterTypes[0] == String::class.java &&
                m.returnType == Void.TYPE &&
                (m.name.contains("unregister", ignoreCase = true) ||
                 m.name.contains("Extension", ignoreCase = true))
            }
        )

        if (method != null) {
            method.invoke(registry, extension)
        } else {
            android.util.Log.e(
                "LSPAccessor",
                "unregisterExtension not found on LanguageServerRegistry"
            )
        }
    }

    /**
     * Return a snapshot of all registered extension → languageId mappings,
     * combining both built-in and custom entries.
     *
     * Returns an empty map if the method is not available on this build.
     */
    @Suppress("UNCHECKED_CAST")
    fun getRegisteredExtensions(): Map<String, String> {
        val method = findRegistryMethod(
            name        = "getRegisteredExtensions",
            paramTypes  = emptyArray(),
            fingerprint = { m ->
                m.parameterCount == 0 &&
                Map::class.java.isAssignableFrom(m.returnType) &&
                m.name.contains("Extension", ignoreCase = true)
            }
        ) ?: return emptyMap()

        return method.invoke(registry) as? Map<String, String> ?: emptyMap()
    }

    /**
     * Two-pass method lookup on [registryClass].
     *
     *  Pass 1 — by [name] + [paramTypes]: fast, works on debug/non-obfuscated builds.
     *  Pass 2 — by [fingerprint] lambda: walks all declared methods and returns the
     *            first one that matches. Survives R8 method renaming.
     *
     * The returned method has [isAccessible] set to `true`.
     * Returns `null` if neither pass finds a match.
     */
    private fun findRegistryMethod(
        name: String,
        paramTypes: Array<Class<*>>,
        fingerprint: (java.lang.reflect.Method) -> Boolean
    ): java.lang.reflect.Method? {
        // Pass 1: exact name + signature
        val byName = runCatching {
            registryClass.getDeclaredMethod(name, *paramTypes)
                .apply { isAccessible = true }
        }.getOrNull()

        if (byName != null) {
            android.util.Log.d("LSPAccessor", "Method '$name' found by name")
            return byName
        }

        // Pass 2: signature fingerprint
        android.util.Log.d(
            "LSPAccessor",
            "Method '$name' not found by name — scanning by fingerprint"
        )

        for (m in registryClass.declaredMethods) {
            if (!fingerprint(m)) continue
            m.isAccessible = true
            android.util.Log.d(
                "LSPAccessor",
                "Method '$name' found via fingerprint: actual name '${m.name}'"
            )
            return m
        }

        android.util.Log.e("LSPAccessor", "Method '$name' not found on LanguageServerRegistry")
        return null
    }

    /** Grab the LSPManager singleton via reflection. */
    private fun getLSPManagerInstance(): Any? {
        return try {
            val clazz = Class.forName("com.nullij.androidcodestudio.editor.LSPManager")
            val companion = clazz.getDeclaredField("Companion")
                .apply { isAccessible = true }
                .get(null)
            companion.javaClass.getDeclaredMethod("getInstance")
                .invoke(companion)
        } catch (e: Exception) {
            android.util.Log.e("LSPAccessor", "Failed to get LSPManager instance", e)
            null
        }
    }

    // ─── PluginLanguageServer ─────────────────────────────────────────────────

    /**
     * Implement this interface to contribute a language server from a plugin.
     *
     * The IDE calls these methods on a background thread. You do not need to
     * manage threading yourself — all calls arrive on [kotlinx.coroutines.Dispatchers.IO].
     *
     * Typical implementation flow inside [start]:
     * 1. Build your server process using [LanguageServerProcess].
     * 2. Wire stdin/stdout to a JSON-RPC loop (or use your own transport).
     * 3. Send the LSP `initialize` / `initialized` handshake.
     * 4. Return `true` when the server is ready to accept requests.
     *
     * ```kotlin
     * class RustLanguageServer(private val context: Context) : PluginLanguageServer {
     *
     *     private var process: Process? = null
     *
     *     override suspend fun start(): Boolean {
     *         process = LanguageServerProcess.builder(context)
     *             .command("rust-analyzer")
     *             .attachDir(File(context.filesDir, "home/flutter"))
     *             .withEnv("RUST_LOG", "error")
     *             .launch()
     *         // … send initialize, wire reader thread, etc.
     *         return true
     *     }
     *
     *     override suspend fun stop() { process?.destroy() }
     *     override fun isRunning(): Boolean = process?.isAlive == true
     *     override fun getClient(): Any = RustLanguageClient(this)
     *
     *     override suspend fun openDocument(file: File): Boolean { … }
     *     override suspend fun closeDocument(file: File) { … }
     *     override suspend fun documentChanged(file: File, content: String, version: Int) { … }
     *     override fun destroy() { runBlocking { stop() } }
     * }
     * ```
     */
    interface PluginLanguageServer {
        /**
         * Start the language server process and complete the LSP handshake.
         * Called on [kotlinx.coroutines.Dispatchers.IO].
         * Returns `true` if the server is ready to accept requests.
         */
        suspend fun start(): Boolean

        /**
         * Gracefully shut down the server.
         * Send LSP `shutdown` + `exit` before destroying the process.
         */
        suspend fun stop()

        /**
         * Whether the server process is currently alive and ready.
         */
        fun isRunning(): Boolean

        /**
         * Return your [LanguageClient] implementation.
         * The IDE uses this object to call completions, hover, diagnostics, etc.
         * Must be non-null after [start] returns `true`.
         *
         * The return type is [Any] to avoid a compile-time dependency on the
         * IDE's [LanguageClient] interface — implement it against the copy
         * from the accessors library.
         */
        fun getClient(): Any

        /** Notify the server that a document was opened. */
        suspend fun openDocument(file: File): Boolean

        /** Notify the server that a document was closed. */
        suspend fun closeDocument(file: File)

        /**
         * Notify the server that a document's content changed.
         * @param version monotonically increasing version counter for this file.
         */
        suspend fun documentChanged(file: File, content: String, version: Int)

        /**
         * Called when the IDE is shutting down or the plugin is unloaded.
         * Release all resources (threads, sockets, processes).
         */
        fun destroy()
    }

    // ─── PluginServerManager (internal bridge) ───────────────────────────────
    // Wraps PluginLanguageServer and satisfies the LanguageServerManager
    // interface so it can be passed to LanguageServerRegistry.registerServer().
    // Uses a dynamic proxy so we never reference LanguageServerManager at
    // compile time.

    private inner class PluginServerManager(
        private val languageId: String,
        private val server: PluginLanguageServer
    ) : java.lang.reflect.InvocationHandler {

        /** The actual proxy instance implementing LanguageServerManager. */
        val proxy: Any by lazy {
            val managerInterface = Class.forName(
                "com.nullij.androidcodestudio.lsp.LanguageServerManager"
            )
            java.lang.reflect.Proxy.newProxyInstance(
                managerInterface.classLoader,
                arrayOf(managerInterface),
                this
            )
        }

        @Suppress("UNCHECKED_CAST")
        override fun invoke(proxy: Any?, method: java.lang.reflect.Method, args: Array<out Any>?): Any? {
            return when (method.name) {

                "start" -> {
                    // suspend fun start(): Boolean
                    val cont = args?.lastOrNull() as? kotlin.coroutines.Continuation<Boolean>
                        ?: return false
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val result = runCatching { server.start() }.getOrDefault(false)
                        if (result) {
                            // Register the client with LSPManager so the editor
                            // can route feature requests to it.
                            val client = server.getClient()
                            val lspManager = getLSPManagerInstance()
                            if (lspManager != null) {
                                // Find registerClient by name because the second param is typed
                                // LanguageClient, not Any — getDeclaredMethod with Any::class.java
                                // would throw NoSuchMethodException and silently skip registration.
                                val registerMethod = lspManager.javaClass.declaredMethods
                                    .firstOrNull { it.name == "registerClient" && it.parameterCount == 2 }
                                    ?.apply { isAccessible = true }
                                if (registerMethod != null) {
                                    registerMethod.invoke(lspManager, languageId, client)
                                } else {
                                    android.util.Log.e("LSPAccessor", "registerClient method not found on LSPManager")
                                }
                            }
                        }
                        (cont as kotlin.coroutines.Continuation<Any?>).resumeWith(Result.success(result))
                    }
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }

                "stop" -> {
                    val cont = args?.lastOrNull() as? kotlin.coroutines.Continuation<Unit>
                        ?: return null
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { server.stop() }
                        (cont as kotlin.coroutines.Continuation<Any?>).resumeWith(Result.success(Unit))
                    }
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }

                "isRunning" -> server.isRunning()

                "getClient" -> server.getClient()

                "openDocument" -> {
                    // suspend fun openDocument(file: File): Boolean
                    val file = args?.getOrNull(0) as? File ?: return false
                    val cont = args.lastOrNull() as? kotlin.coroutines.Continuation<Boolean>
                        ?: return false
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val result = runCatching { server.openDocument(file) }.getOrDefault(false)
                        (cont as kotlin.coroutines.Continuation<Any?>).resumeWith(Result.success(result))
                    }
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }

                "closeDocument" -> {
                    val file = args?.getOrNull(0) as? File ?: return null
                    val cont = args.lastOrNull() as? kotlin.coroutines.Continuation<Unit>
                        ?: return null
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { server.closeDocument(file) }
                        (cont as kotlin.coroutines.Continuation<Any?>).resumeWith(Result.success(Unit))
                    }
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }

                "documentChanged" -> {
                    val file = args?.getOrNull(0) as? File ?: return null
                    val content = args.getOrNull(1) as? String ?: return null
                    val version = args.getOrNull(2) as? Int ?: 1
                    val cont = args.lastOrNull() as? kotlin.coroutines.Continuation<Unit>
                        ?: return null
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { server.documentChanged(file, content, version) }
                        (cont as kotlin.coroutines.Continuation<Any?>).resumeWith(Result.success(Unit))
                    }
                    kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
                }

                "destroy" -> {
                    server.destroy()
                    null
                }

                // Proxy bookkeeping
                "equals"   -> proxy === args?.getOrNull(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PluginServerManager[$languageId]"

                else -> null
            }
        }
    }

    // ─── LanguageServerProcess ───────────────────────────────────────────────
    // Utility for launching a language server process inside the IDE's proot
    // environment. Mirrors ProotProcessWrapper.Builder but with naming that
    // makes sense to plugin authors.

    /**
     * Builds and launches a language server process inside the IDE's
     * proot/acsenv environment, with the same environment variables the IDE
     * uses for its own build processes.
     *
     * ```kotlin
     * val process = LanguageServerProcess.builder(context)
     *     .command("rust-analyzer")
     *     .attachDir(rustSdkDir, "/root/rust")
     *     .withEnv("RUST_LOG", "error")
     *     .launch()
     * ```
     */
    class LanguageServerProcess private constructor(
        private val context: Context,
        private val command: Array<out String>,
        private val workingDir: File?,
        private val attachedDirs: List<Pair<File, String?>>,
        private val envVars: Map<String, String>,
        private val attachStorage: Boolean,
        private val attachAndroidSdk: Boolean
    ) {

        companion object {
            fun builder(context: Context): Builder = Builder(context)
        }

        class Builder(private val context: Context) {
            private var command: Array<out String> = emptyArray()
            private var workingDir: File? = null
            private val attachedDirs = mutableListOf<Pair<File, String?>>()
            private val envVars = mutableMapOf<String, String>()
            private var attachStorage = false
            private var attachAndroidSdk = false

            /**
             * The executable and its arguments to run inside proot.
             * Paths must be rootfs-relative (e.g. `"/root/flutter/bin/dart"`).
             *
             * ```kotlin
             * .command("dart", "language-server", "--protocol=lsp")
             * ```
             */
            fun command(vararg cmd: String) = apply { command = cmd }

            /**
             * Set the working directory for the process (host filesystem path).
             * Defaults to the project root if not set.
             */
            fun workingDir(dir: File) = apply { workingDir = dir }

            /**
             * Attach a host filesystem directory into the proot environment.
             * If [mountAt] is omitted the directory is mounted at the same path.
             *
             * ```kotlin
             * .attachDir(flutterSdkDir, "/root/flutter")
             * .attachDir(projectDir)               // same path inside proot
             * ```
             */
            fun attachDir(hostDir: File, mountAt: String? = null) = apply {
                attachedDirs.add(hostDir to mountAt)
            }

            /**
             * Attach the device's external storage (`/storage/emulated/0`)
             * into the proot environment.
             */
            fun attachStorage() = apply { attachStorage = true }

            /**
             * Attach the Android SDK directory into the proot environment,
             * mounted at its standard rootfs path (`/home/Android/Sdk`).
             */
            fun attachAndroidSdk() = apply { attachAndroidSdk = true }

            /**
             * Set a single environment variable for the server process.
             */
            fun withEnv(key: String, value: String) = apply { envVars[key] = value }

            /**
             * Set multiple environment variables at once.
             */
            fun withEnv(vars: Map<String, String>) = apply { envVars.putAll(vars) }

            /**
             * Build and launch the process.
             * Call this from a background thread or a coroutine on [kotlinx.coroutines.Dispatchers.IO].
             *
             * @throws IllegalStateException if proot or the acsenv is not set up
             * @throws IllegalArgumentException if [command] was not set
             */
            fun launch(): Process {
                require(command.isNotEmpty()) { "command() must be set before calling launch()" }

                // Resolve proot and acsenv through reflection on IDEEnvironment
                // so we have no compile-time dependency on the IDE.
                val ideEnvClass = Class.forName("com.nullij.androidcodestudio.utils.IDEEnvironment")
                val ideEnv = ideEnvClass.getDeclaredField("INSTANCE")
                    .apply { isAccessible = true }
                    .get(null)

                fun getFile(name: String): File =
                    ideEnvClass.getMethod("get${name.replaceFirstChar { it.uppercase() }}")
                        .invoke(ideEnv) as File

                fun getString(name: String): String =
                    ideEnvClass.getMethod("get${name.replaceFirstChar { it.uppercase() }}")
                        .invoke(ideEnv) as String

                val binDir    = getFile("binDir")
                val acsenvDir = getFile("acsenvDir")

                val proot = File(binDir, "proot")
                check(proot.exists()) { "proot not found at ${proot.absolutePath}" }
                check(acsenvDir.exists()) { "acsenv not found at ${acsenvDir.absolutePath}" }

                // Build proot command
                val localWorkingDir = workingDir

                val fullCmd = mutableListOf<String>()
                fullCmd += proot.absolutePath
                fullCmd += "--rootfs=${acsenvDir.absolutePath}"

                if (localWorkingDir != null) {
                    fullCmd += "--cwd=${localWorkingDir.absolutePath}"
                }

                // Standard bind mounts
                listOf("/dev", "/proc", "/sys").forEach { fullCmd += "--bind=$it" }

                // Attach storage
                if (attachStorage) {
                    val storage = File("/storage/emulated/0")
                    if (storage.exists()) fullCmd += "--bind=${storage.absolutePath}"
                }

                // Attach Android SDK
                if (attachAndroidSdk) {
                    val sdk = getFile("androidSdkDir")
                    val rootfsSdk = getString("rootfsAndroidSdkPath")
                    if (sdk.exists()) fullCmd += "--bind=${sdk.absolutePath}:$rootfsSdk"
                }

                // Custom attached directories
                attachedDirs.forEach { (hostDir, mountAt) ->
                    fullCmd += if (mountAt != null) {
                        "--bind=${hostDir.absolutePath}:$mountAt"
                    } else {
                        "--bind=${hostDir.absolutePath}"
                    }
                }

                // The actual server command
                fullCmd.addAll(command)

                // Build environment: start from IDE defaults, merge plugin vars on top
                @Suppress("UNCHECKED_CAST")
                val baseEnv = ideEnvClass
                    .getMethod("getEnvironment", Map::class.java)
                    .invoke(ideEnv, emptyMap<String, String>()) as Map<String, String>

                val mergedEnv = baseEnv + envVars

                return ProcessBuilder(*fullCmd.toTypedArray())
                    .also { pb ->
                        if (localWorkingDir != null) pb.directory(localWorkingDir)
                        pb.environment().clear()
                        pb.environment().putAll(mergedEnv)
                    }
                    .start()
            }
        }
    }
}