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
import com.nullij.androidcodestudio.plugins.api.PluginApi

object BashLanguageServer {

    private const val TAG = "BashLanguageServer"
    private const val LANGUAGE_ID = "shellscript"

    private var serverRegistered = false

    fun setContext(context: Context) {
        registerBashServer(context)
    }

    private fun registerBashServer(context: Context) {
        if (serverRegistered) {
            Log.d(TAG, "Bash server already registered")
            return
        }

        try {
            Log.d(TAG, "Registering Bash language server...")

            val lsp = PluginApi.lsp
            if (lsp == null) {
                Log.e(TAG, "PluginApi.lsp is null — not inside EditorActivity")
                return
            }

            lsp.registerExtension("sh", LANGUAGE_ID)
            lsp.registerExtension("bash", LANGUAGE_ID)
            lsp.registerExtension("zsh", LANGUAGE_ID)
            lsp.registerExtension("ksh", LANGUAGE_ID)
            lsp.registerExtension("fish", LANGUAGE_ID)
            lsp.registerExtension("env", LANGUAGE_ID)
            lsp.registerExtension("bashrc", LANGUAGE_ID)
            lsp.registerExtension("zshrc", LANGUAGE_ID)
            lsp.registerExtension("profile", LANGUAGE_ID)

            if (!lsp.hasServer(LANGUAGE_ID)) {
                lsp.registerServer(LANGUAGE_ID, BashLanguageServerManager())
            }

            serverRegistered = true
            Log.i(TAG, "✅ Bash language server registered")

            Thread(
                    {
                        Log.d(TAG, "Starting Bash language server...")
                        val started = lsp.startServer(LANGUAGE_ID)
                        if (started) {
                            Log.i(TAG, "✅ Bash language server is ready")
                        } else {
                            Log.e(TAG, "❌ Failed to start Bash language server")
                        }
                    },
                    "bash-lsp-start",
                )
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register Bash server", e)
        }
    }
}
