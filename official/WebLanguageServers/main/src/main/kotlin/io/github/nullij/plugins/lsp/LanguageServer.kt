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
/*
 * @author nullij @ https://github.com/nullij
 */
object LanguageServer {

    private var serverRegistered = false

    fun setContext(context: Context) {
        registerServers()
    }

    fun getContext() {}

    private fun registerServers() {
        if (serverRegistered) return

        val lsp = PluginApi.lsp ?: return

        lsp.registerExtension("html", "html")
        lsp.registerExtension("css", "css")
        lsp.registerExtension("less", "less")
        lsp.registerExtension("scss", "scss")
        lsp.registerExtension("js", "javascript")
        lsp.registerExtension("mjs", "javascript")
        lsp.registerExtension("cjs", "javascript")
        lsp.registerExtension("json", "json")
        lsp.registerExtension("jsonc", "json")
        lsp.registerExtension("md", "markdown")
        lsp.registerExtension("markdown", "markdown")

        if (!lsp.hasServer("html")) lsp.registerServer("html", HtmlLanguageServerManager())
        if (!lsp.hasServer("css")) lsp.registerServer("css", CssLanguageServerManager("css"))
        if (!lsp.hasServer("less")) lsp.registerServer("less", CssLanguageServerManager("less"))
        if (!lsp.hasServer("scss")) lsp.registerServer("scss", CssLanguageServerManager("scss"))
        lsp.registerExtension("ts", "typescript")
        lsp.registerExtension("tsx", "typescriptreact")
        if (!lsp.hasServer("typescript"))
            lsp.registerServer("typescript", TypeScriptLanguageServerManager("typescript"))
        if (!lsp.hasServer("typescriptreact"))
            lsp.registerServer(
                "typescriptreact",
                TypeScriptLanguageServerManager("typescriptreact"),
            )

        if (!lsp.hasServer("javascript"))
            lsp.registerServer("javascript", TypeScriptLanguageServerManager("typescript"))
        if (!lsp.hasServer("json")) lsp.registerServer("json", JsonLanguageServerManager())
        if (!lsp.hasServer("markdown"))
            lsp.registerServer("markdown", MarkdownLanguageServerManager())

        serverRegistered = true

        Thread(
                {
                    lsp.startServer("html")
                    lsp.startServer("css")
                    lsp.startServer("less")
                    lsp.startServer("scss")
                    lsp.startServer("typescriptreact")
                    lsp.startServer("typescript")
                    lsp.startServer("javascript")
                    lsp.startServer("json")
                    lsp.startServer("markdown")
                },
                "lsp-start",
            )
            .start()
    }
}
