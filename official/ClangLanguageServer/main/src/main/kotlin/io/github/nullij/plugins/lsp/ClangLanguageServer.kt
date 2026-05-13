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

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nullij.androidcodestudio.plugins.api.PluginApi

/** @author nullij @ https://github.com/nullij **/

object ClangLanguageServer {

    private var serverRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val clangdCandidates = arrayOf(
        "/bin/clangd",
        "/usr/lib/llvm-18/bin/clangd",
        "/usr/bin/clangd",
        "/usr/local/bin/clangd",
    )

    fun setContext(context: Context) {
        val clangdPath = resolveClangd()
        if (clangdPath == null) {
            showInstallDialog()
            return
        }
        registerServers(clangdPath)
    }

    fun getContext() {}

    fun configure() {
        mainHandler.post {
            PluginApi.ui?.showOverlay { handle ->
                ClangConfigDialog(onDismiss = { handle.dismiss() })
            }
        }
    }

    private fun canRunClangd(path: String): Boolean {
        return try {
            val proc = PluginApi.process
                .builder()
                .command(path, "--version")
                .attachStorage()
                .launch()
            proc.waitFor() == 0
        } catch (e: Exception) {
            android.util.Log.e("ClangResolver", "canRunClangd($path) failed: ${e.message}")
            false
        }
    }

    private fun resolveClangd(): String? {
        val configured = ClangConfig.clangdPath
        if (!configured.isNullOrBlank()) return configured
    
        for (path in clangdCandidates) {
            if (canRunClangd(path)) return path
        }
        return try {
            val proc = PluginApi.process
                .builder()
                .command("/bin/sh", "-c", "which clangd")
                .attachStorage()
                .launch()
            val resolved = proc.inputStream.bufferedReader().readLine()?.trim()
            proc.waitFor()
            if (!resolved.isNullOrEmpty() && canRunClangd(resolved)) resolved else null
        } catch (e: Exception) {
            android.util.Log.e("ClangResolver", "which clangd failed: ${e.message}")
            null
        }
    }

    private fun showInstallDialog() {
        mainHandler.post {
            PluginApi.ui?.showOverlay { handle ->
                ClangdInstallDialog(
                    onDismiss = { handle.dismiss() },
                )
            }
        }
    }

    private fun installClangd() {
        val outputLines = androidx.compose.runtime.mutableStateOf("")
        var progressHandle: com.nullij.androidcodestudio.plugins.api.OverlayHandle? = null

        Thread({
            try {
                val proc = PluginApi.process.installPackage("clangd")

                val lock = Any()
                val sb = StringBuilder()

                fun appendLine(line: String?) {
                    if (line == null) return
                    val snapshot = synchronized(lock) { sb.appendLine(line); sb.toString() }
                    mainHandler.post { outputLines.value = snapshot }
                }

                val stderrThread = Thread({
                    proc.errorStream.bufferedReader().forEachLine { appendLine(it) }
                }, "clangd-install-stderr")
                stderrThread.start()

                proc.inputStream.bufferedReader().forEachLine { appendLine(it) }
                stderrThread.join()

                val exitCode = proc.waitFor()

                mainHandler.post {
                    progressHandle?.dismiss()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    progressHandle?.dismiss()
                }
            }
        }, "clangd-install").start()
    }

    private var registeredClangdPath: String? = null
    
    private fun registerServers(clangdPath: String) {
        if (serverRegistered && clangdPath == registeredClangdPath) return
    
        val lsp = PluginApi.lsp ?: return
        
        // re-register with new path if changed
        if (serverRegistered && clangdPath != registeredClangdPath) {
            lsp.stopServer("c")
            serverRegistered = false
        }
    
        lsp.registerExtension("c", "c")
        lsp.registerExtension("cc", "c")
        lsp.registerExtension("cpp", "c")
        lsp.registerExtension("cxx", "c")
        lsp.registerExtension("h", "c")
        lsp.registerExtension("hpp", "c")
        lsp.registerExtension("hxx", "c")
        lsp.registerExtension("hh", "c")
        lsp.registerExtension("ixx", "c")
    
        lsp.registerServer("c", ClangLanguageServerManager(clangdPath))
        serverRegistered = true
        registeredClangdPath = clangdPath
    
        Thread({ lsp.startServer("c") }, "lsp-clang-start").start()
    }
}