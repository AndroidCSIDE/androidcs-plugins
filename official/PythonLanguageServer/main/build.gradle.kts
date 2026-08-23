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

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.security.MessageDigest


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("io.github.nullij.acside-gradle-plugin").version("0.2.0") // acp-gradle-plugin | important
}

val pluginVersion = "1.0"
val pluginName = "PythonLanguageServer"
val packageName = "io.github.nullij.plugins.lsp"

acpPlugin {
    metaFolderPath = "meta"        // Source folder (default: 'meta')
    outputFileName = "${pluginName}.acp"  // Output name (default: 'plugin.acp')
}

android {
    namespace = "${packageName}"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    
    defaultConfig {
        minSdk = 26 // not necessary 
        targetSdk = 34 // not necessary 
        versionCode = 1 // not necessary
        versionName = pluginVersion // not necessary 
        
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
        
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    compileOnly(project(":editor"))
    
    implementation(libs.acside.plugin.api)
    implementation(libs.google.gson)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    
}

tasks.register("updatePluginInf") {
    doLast {
        val pluginJsonFile = file("meta/plugin.json")
        if (pluginJsonFile.exists()) {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonContent = pluginJsonFile.readText()
            val jsonObject = gson.fromJson(jsonContent, JsonObject::class.java)
            jsonObject.addProperty("version", pluginVersion)
            jsonObject.addProperty("name", pluginName)
            pluginJsonFile.writeText(gson.toJson(jsonObject))
            println("Updated plugin.json\n\t - version to: $pluginVersion\n\t - name to : $pluginName")

        }
    }
}

tasks.named("preBuild") {
    dependsOn("updatePluginInf")
}

tasks.matching { it.name == "assembleRelease" || it.name == "assembleDebug" }.configureEach {
    finalizedBy("updateRepositoryJson")
}

tasks.register("updateRepositoryJson") {
    description = "Copies .acp to root dir, then updates repository.json with checksum, size, version, and timestamp."
    group = "publishing"

    doLast {
        val acpFile = listOf(
            file("build/${pluginName}.acp"),
            file("build/outputs/acp/${pluginName}.acp"),
            file("build/libs/${pluginName}.acp")
        ).firstOrNull { it.exists() }
        val rootDir  = rootProject.projectDir
        val repoFile = rootDir.resolve("repository.json")
        val destAcp  = rootDir.resolve("${pluginName}.acp")

        if (acpFile == null) {
            println("Warning: ${pluginName}.acp not found in build outputs — skipping.")
            return@doLast
        }

        acpFile.copyTo(destAcp, overwrite = true)
        println("Copied ${acpFile.name} to ${destAcp.absolutePath}")

        if (!repoFile.exists()) {
            println("repository.json not found - skipping")
            return@doLast
        }

        val gson = GsonBuilder().setPrettyPrinting().create()
        val json = gson.fromJson(repoFile.readText(), JsonObject::class.java)

        json.addProperty("latestVersion", pluginVersion)

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        json.addProperty("lastUpdated", timestamp)

        val bytes    = destAcp.readBytes()
        val digest   = MessageDigest.getInstance("SHA-256")
        val checksum = digest.digest(bytes).joinToString("") { b -> "%02x".format(b) }
        json.addProperty("checksum", checksum)
        json.addProperty("size", bytes.size.toLong())

        repoFile.writeText(gson.toJson(json))

        println("Updated repository.json\n\t- version    : $pluginVersion\n\t- lastUpdated: $timestamp\n\t- checksum   : $checksum\n\t- size       : ${bytes.size} bytes")
    }
}