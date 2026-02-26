/*
 *  This file is part of AndroidCodeStudio.
 *
 *  AndroidCodeStudio is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidCodeStudio is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidCodeStudio.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.github.nullij.plugins.templates.flutter.sources.android.module

import io.github.nullij.plugins.templates.flutter.constants.Constants as CV
/**
  * @author nullij @ https://github.com/nullij
 */
object moduleSrcs {

    val mainActivityAsKotlin: String = """
        package com.example.flutter_m3_template
        
        import io.flutter.embedding.android.FlutterActivity
        
        class MainActivity: FlutterActivity()
    """.trimIndent()

    val mainActivityAsJava: String = """
        package com.example.flutter_m3_template;
        
        import io.flutter.embedding.android.FlutterActivity;
        
        public class MainActivity extends FlutterActivity {
        }
    """.trimIndent()

    val AndroidManifestAsXml: String = """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <uses-permission android:name="android.permission.INTERNET"/>
            
            <application
                android:label="Flutter M3 Template"
                android:name="io.flutter.app.FlutterApplication"
                android:icon="@mipmap/ic_launcher"
                android:enableOnBackInvokedCallback="true">
                <activity
                    android:name=".MainActivity"
                    android:exported="true"
                    android:launchMode="singleTop"
                    android:taskAffinity=""
                    android:theme="@style/LaunchTheme"
                    android:configChanges="orientation|keyboardHidden|keyboard|screenSize|smallestScreenSize|locale|layoutDirection|fontScale|screenLayout|density|uiMode"
                    android:hardwareAccelerated="true"
                    android:windowSoftInputMode="adjustResize">
                    <meta-data
                      android:name="io.flutter.embedding.android.NormalTheme"
                      android:resource="@style/NormalTheme"
                      />
                    <intent-filter>
                        <action android:name="android.intent.action.MAIN"/>
                        <category android:name="android.intent.category.LAUNCHER"/>
                    </intent-filter>
                </activity>
                <meta-data
                    android:name="flutterEmbedding"
                    android:value="2" />
            </application>
            <queries>
                <intent>
                    <action android:name="android.intent.action.VIEW" />
                    <data android:scheme="https" />
                </intent>
            </queries>
        </manifest>
    """.trimIndent()

    /* Groovy (app/build.gradle) */
    val buildGradleSrcAsGroovy: String
        get() = """
        plugins {
            id "com.android.application"
            id "kotlin-android"
            id "dev.flutter.flutter-gradle-plugin"
        }
        
        def localProperties = new Properties()
        def localPropertiesFile = rootProject.file('local.properties')
        if (localPropertiesFile.exists()) {
            localPropertiesFile.withReader('UTF-8') { reader ->
                localProperties.load(reader)
            }
        }
        
        def flutterVersionCode = localProperties.getProperty('flutter.versionCode')
        if (flutterVersionCode == null) {
            flutterVersionCode = '1'
        }
        
        def flutterVersionName = localProperties.getProperty('flutter.versionName')
        if (flutterVersionName == null) {
            flutterVersionName = '1.0'
        }
        
        android {
            namespace "${CV.packageName}"
            compileSdk ${CV.compileSdk}
            ndkVersion "${CV.ndkVersion}"
        
            compileOptions {
                sourceCompatibility JavaVersion.VERSION_17
                targetCompatibility JavaVersion.VERSION_17
            }
        
            sourceSets {
                main.java.srcDirs += 'src/main/kotlin'
            }
        
            defaultConfig {
                applicationId "${CV.packageName}"
                minSdk ${CV.minSdk}
                targetSdk ${CV.compileSdk}
                versionCode flutterVersionCode.toInteger()
                versionName flutterVersionName
                
                externalNativeBuild {
                    cmake {
                        arguments "-DANDROID_STL=c++_shared"
                    }
                }
            }
        
            buildTypes {
                release {
                    signingConfig signingConfigs.debug
                    minifyEnabled true
                    shrinkResources true
                    proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
                }
            }
            
            externalNativeBuild {
                cmake {
                    version "4.3.0"
                    path "CMakeLists.txt"
                }
            }
        }
        
        flutter {
            source '../..'
        }
        
        dependencies {
            implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${CV.jetbrainsAndroidKotlinVersion}"
        }
    """.trimIndent()

    /* Kotlin DSL (app/build.gradle.kts) */
    val buildGradleSrcAsKts: String
        get() = """
        import java.util.Properties
        
        plugins {
            id("com.android.application")
            id("kotlin-android")
            id("dev.flutter.flutter-gradle-plugin")
        }
        
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.reader(Charsets.UTF_8).use { reader: java.io.Reader ->
                localProperties.load(reader)
            }
        }
        
        val flutterVersionCode = localProperties.getProperty("flutter.versionCode") ?: "1"
        val flutterVersionName = localProperties.getProperty("flutter.versionName") ?: "1.0"
        
        android {
            namespace = "${CV.packageName}"
            compileSdk = ${CV.compileSdk}
            ndkVersion = "${CV.ndkVersion}"
        
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        
            sourceSets {
                getByName("main") {
                    java.srcDirs("src/main/kotlin")
                }
            }
        
            defaultConfig {
                applicationId = "${CV.packageName}"
                minSdk = ${CV.minSdk}
                targetSdk = ${CV.compileSdk}
                versionCode = flutterVersionCode.toInt()
                versionName = flutterVersionName
                
                externalNativeBuild {
                    cmake {
                        arguments += "-DANDROID_STL=c++_shared"
                    }
                }
            }
        
            buildTypes {
                release {
                    signingConfig = signingConfigs.getByName("debug")
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )
                }
            }
            
            externalNativeBuild {
                cmake {
                    version = "4.3.0"
                    path = file("CMakeLists.txt")
                }
            }
        }
        
        flutter {
            source = "../.."
        }
        
        dependencies {
            implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${CV.jetbrainsAndroidKotlinVersion}")
        }
    """.trimIndent()

    val cmakeListAsTxt: String = """
        cmake_minimum_required(VERSION 3.31.6)
        project(flutter_m3_template)
        
        # This CMakeLists.txt is a placeholder for native code integration
        # Add your native C/C++ source files here when needed
        
        # Example:
        # add_library(native_lib SHARED native_lib.cpp)
        # target_link_libraries(native_lib ${'$'}{log-lib})
    """.trimIndent()
    
    val proguardRulesAsPro: String = """
        # Add project specific ProGuard rules here.
        # By default, the flags in this file are appended to flags specified
        # in /usr/local/Cellar/android-sdk/24.3.3/tools/proguard/proguard-android.txt
        # You can edit the include path and order by changing the proguardFiles
        # directive in build.gradle.
        #
        # For more details, see
        #   http://developer.android.com/guide/developing/tools/proguard.html
        
        # Add any project specific keep options here:
        
        # If your project uses WebView with JS, uncomment the following
        # and specify the fully qualified class name to the JavaScript interface
        # class:
        #-keepclassmembers class fqcn.of.javascript.interface.for.webview {
        #   public *;
        #}
        
        # Uncomment this to preserve the line number information for
        # debugging stack traces.
        #-keepattributes SourceFile,LineNumberTable
        
        # If you keep the line number information, uncomment this to
        # hide the original source file name.
        #-renamesourcefileattribute SourceFile
        
        # Flutter wrapper
        -keep class io.flutter.app.** { *; }
        -keep class io.flutter.plugin.**  { *; }
        -keep class io.flutter.util.**  { *; }
        -keep class io.flutter.view.**  { *; }
        -keep class io.flutter.**  { *; }
        -keep class io.flutter.plugins.**  { *; }
        -dontwarn io.flutter.embedding.**
    """.trimIndent()
}