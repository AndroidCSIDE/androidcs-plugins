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

import com.nullij.androidcodestudio.internals.LSPAccessor

/**
 * Entry point for the Bash Language Server plugin.
 *
 * Declared as a Kotlin `object` (singleton) so that `DexActionLoader.executeStaticMethod`
 * resolves the `INSTANCE` field (step 1 of its lookup chain) instead of falling through to
 * the `Companion` field (step 2).
 *
 * The IDE calls [setContext] immediately after loading the plugin. Registration and server
 * start happen there — no separate action trigger is needed.
 *
 * bash-language-server must be installed inside the ACS rootfs:
 * ```
 * npm install -g bash-language-server
 * ```
 *
 * The language ID used is `shellscript`, which is the standard VS Code / LSP identifier
 * for Bash/shell files (`.sh`, `.bash`, `.zsh`, etc.).
 *
 * @author nullij @ https://github.com/nullij
 */
object BashLanguageServer {

    private const val TAG         = "BashLanguageServer"
    private const val LANGUAGE_ID = "shellscript"

    private var appContext: Context? = null
    private var serverRegistered     = false

    /**
     * Called by the IDE immediately after loading the plugin.
     * This is where we register and start the server.
     */
    fun setContext(context: Context) {
        appContext = context
        registerBashServer()
    }

    fun getContext(): Context? = appContext

    private fun registerBashServer() {
        if (serverRegistered) {
            Log.d(TAG, "Bash server already registered")
            return
        }

        val context = appContext ?: return

        try {
            Log.d(TAG, "Registering Bash language server...")

            val lsp = LSPAccessor.resolve(context)
            if (lsp == null) {
                Log.e(TAG, "LSPAccessor.resolve() returned null — not inside EditorActivity")
                return
            }

            // Register file extensions BEFORE registerServer so that any file
            // already open when the plugin loads is immediately routable.
            lsp.registerExtension("sh",      LANGUAGE_ID)
            lsp.registerExtension("bash",    LANGUAGE_ID)
            lsp.registerExtension("zsh",     LANGUAGE_ID)
            lsp.registerExtension("ksh",     LANGUAGE_ID)
            lsp.registerExtension("fish",    LANGUAGE_ID)
            lsp.registerExtension("env",     LANGUAGE_ID)
            lsp.registerExtension("bashrc",  LANGUAGE_ID)
            lsp.registerExtension("zshrc",   LANGUAGE_ID)
            lsp.registerExtension("profile", LANGUAGE_ID)

            if (!lsp.hasServer(LANGUAGE_ID)) {
                lsp.registerServer(LANGUAGE_ID, BashLanguageServerManager(context))
            }

            serverRegistered = true
            Log.i(TAG, "✅ Bash language server registered")

            // Start the server on a background thread
            Thread({
                Log.d(TAG, "Starting Bash language server...")
                val started = lsp.startServer(LANGUAGE_ID)
                if (started) {
                    Log.i(TAG, "✅ Bash language server is ready")
                } else {
                    Log.e(TAG, "❌ Failed to start Bash language server")
                }
            }, "bash-lsp-start").start()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Bash server", e)
        }
    }
}