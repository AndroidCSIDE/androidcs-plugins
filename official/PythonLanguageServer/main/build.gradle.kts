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

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
    id("io.github.nullij").version("1.0.0") // acp-gradle-plugin | important

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
    implementation("com.google.code.gson:gson:2.10.1")
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