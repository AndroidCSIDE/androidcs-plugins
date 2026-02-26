/*
 *  This file is part of FlutterTemplatePlugin.
 *
 *  FlutterTemplatePlugin is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  FlutterTemplatePlugin is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with FlutterTemplatePlugin.  If not, see <https://www.gnu.org/licenses/>.
*/

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
    id("io.github.nullij").version("1.0.0") // acp-gradle-plugin | important

}

val pluginVersion = "1.0"
val pluginName = "FlutterTemplate"
val packageName = "io.github.nullij.plugins.templates"

acpPlugin {
    metaFolderPath = "meta"        // Source folder (default: 'meta')
    outputFileName = "${pluginName}.acp"  // Output name (default: 'plugin.acp')
}

android {
    namespace = "${packageName}.flutter"
    compileSdk = 36
    buildToolsVersion = "35.0.0"
    
    defaultConfig {
        minSdk = 26 // not necessary 
        targetSdk = 34 // not necessary 
        versionCode = 1 // not necessary
        versionName = pluginVersion // not necessary 
        
    }
    
    buildFeatures {
        compose = true // important cuz plugin src must be either be an m3, no xml code or a jetpack compose ( jetpack compose is recommended )
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
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
    
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    
    implementation("androidx.lifecycle:lifecycle-common:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
    implementation("androidx.savedstate:savedstate:1.2.1")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    
    implementation("com.airbnb.android:lottie:6.3.0")
    implementation("com.airbnb.android:lottie-compose:6.3.0")
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