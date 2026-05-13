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

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ClangConfigDialog(onDismiss: () -> Unit) {

    var clangdPath by remember { mutableStateOf(ClangConfig.clangdPath ?: "") }
    var extraArgs by remember { mutableStateOf(ClangConfig.extraArgs) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Clang Language Server", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // clangd path
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "clangd executable path",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = clangdPath,
                        onValueChange = { clangdPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Auto-detect", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        supportingText = {
                            Text(
                                "Leave empty to auto-detect from common locations.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                // Extra arguments
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Extra arguments",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = extraArgs,
                        onValueChange = { extraArgs = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. --background-index --clang-tidy", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        minLines = 2,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        supportingText = {
                            Text(
                                "Space-separated flags passed to clangd. " +
                                "--log=error, --clang-tidy, and --pch-storage=memory are always included.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }

                // Hint about restart
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Changes take effect the next time the language server starts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ClangConfig.clangdPath = clangdPath.trim().takeIf { it.isNotEmpty() }
                ClangConfig.extraArgs = extraArgs.trim()
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
