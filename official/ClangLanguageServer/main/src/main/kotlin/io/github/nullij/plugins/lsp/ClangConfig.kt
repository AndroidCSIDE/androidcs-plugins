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

import org.json.JSONObject
import java.io.File

/**
 ** Persists clangd configuration to ~/.acs/plugins/ClangLanguageServer/config.json
 ** @author nullij @ https://github.com/nullij
 */
object ClangConfig {

    private const val CONFIG_FILE = "config.json"
    private const val KEY_CLANGD_PATH = "clangdPath"
    private const val KEY_EXTRA_ARGS = "extraArgs"

    private fun configFile(): File {
        val pluginsDir = java.io.File(
            com.nullij.androidcodestudio.plugins.api.PluginApi.environment.homeDir,
            ".acs/plugins/ClangLanguageServer"
        )
        pluginsDir.mkdirs()
        return File(pluginsDir, CONFIG_FILE)
    }

    var clangdPath: String?
        get() = load().optString(KEY_CLANGD_PATH, null).takeIf { !it.isNullOrBlank() }
        set(value) = save { put(KEY_CLANGD_PATH, value ?: "") }

    var extraArgs: String
        get() = load().optString(KEY_EXTRA_ARGS, "")
        set(value) = save { put(KEY_EXTRA_ARGS, value) }

    /** Returns extraArgs split into a list, filtering blanks. */
    fun extraArgsList(): List<String> =
        extraArgs.split(" ").map { it.trim() }.filter { it.isNotEmpty() }

    private fun load(): JSONObject {
        return try {
            val f = configFile()
            if (f.exists()) JSONObject(f.readText()) else JSONObject()
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun save(block: JSONObject.() -> Unit) {
        try {
            val json = load()
            json.block()
            configFile().writeText(json.toString(2))
        } catch (e: Exception) {
            android.util.Log.e("ClangConfig", "Failed to save config: ${e.message}")
        }
    }
}