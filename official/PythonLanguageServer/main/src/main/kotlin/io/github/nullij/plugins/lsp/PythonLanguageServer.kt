/*
 *  This file is part of PythonLanguageServer.
 *
 *  PythonLanguageServer is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  PythonLanguageServer is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with PythonLanguageServer.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.nullij.plugins.lsp

import android.content.Context
import android.util.Log

import com.nullij.androidcodestudio.internals.LSPAccessor

/**
 * Entry point for the Python Language Server plugin.
 *
 * Declared as a Kotlin `object` (singleton) so that `DexActionLoader.executeStaticMethod`
 * resolves the `INSTANCE` field (step 1 of its lookup chain) instead of falling through to
 * the `Companion` field (step 2), which was causing:
 *
 *   IllegalArgumentException: Expected receiver of type PythonLanguageServer,
 *                              but got PythonLanguageServer$Companion
 *
 * The IDE calls [setContext] immediately after loading the plugin. Registration and server
 * start happen there — no separate action trigger is needed.
 *
 * pylsp must be installed inside the ACS rootfs:
 * ```
 * pip install python-lsp-server
 * ```
 *
 * @author nullij @ https://github.com/nullij
 */
object PythonLanguageServer {

    private const val TAG = "PythonLanguageServer"
    private const val LANGUAGE_ID = "python"

    private var appContext: Context? = null
    private var serverRegistered = false

    /**
     * Called by the IDE immediately after loading the plugin.
     * This is where we register and start the server.
     */
    fun setContext(context: Context) {
        appContext = context
        registerPythonServer()
    }

    fun getContext(): Context? = appContext

    private fun registerPythonServer() {
        if (serverRegistered) {
            Log.d(TAG, "Python server already registered")
            return
        }

        val context = appContext ?: return

        try {
            Log.d(TAG, "Registering Python language server...")

            val lsp = LSPAccessor.resolve(context)
            if (lsp == null) {
                Log.e(TAG, "LSPAccessor.resolve() returned null — not inside EditorActivity")
                return
            }

            if (!lsp.hasServer(LANGUAGE_ID)) {
                lsp.registerServer(LANGUAGE_ID, PythonLanguageServerManager(context))
            }

            serverRegistered = true
            Log.i(TAG, "✅ Python language server registered")

            // Start the server on a background thread
            Thread({
                Log.d(TAG, "Starting Python language server...")
                val started = lsp.startServer(LANGUAGE_ID)
                if (started) {
                    Log.i(TAG, "✅ Python language server is ready")
                } else {
                    Log.e(TAG, "❌ Failed to start Python language server")
                }
            }, "python-lsp-start").start()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Python server", e)
        }
    }
}