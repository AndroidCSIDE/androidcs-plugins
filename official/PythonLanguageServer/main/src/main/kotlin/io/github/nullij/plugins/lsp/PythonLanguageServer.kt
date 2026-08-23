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

import android.content.Context
import com.nullij.androidcodestudio.plugins.api.PluginApi

object PythonLanguageServer {

    private var serverRegistered = false

    fun setContext(context: Context) {
        registerServers()
    }

    fun getContext() {}

    private fun registerServers() {
        if (serverRegistered) return

        val lsp = PluginApi.lsp ?: return
        lsp.registerExtension("py", "python")

        if (!lsp.hasServer("python")) lsp.registerServer("python", PythonLanguageServerManager())
        serverRegistered = true

        Thread({ lsp.startServer("python") }, "lsp-start").start()
    }
}
