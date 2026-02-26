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

package io.github.nullij.plugins.templates.flutter.sources.android.root

import io.github.nullij.plugins.templates.flutter.constants.Constants as CV

/**
  * @author nullij @ https://github.com/nullij
 */
object rootSrcs {

    /* Groovy (settings.gradle) */
    val settingsSrcAsGroovy: String = """
        pluginManagement {
            def flutterSdkPath = {
                def properties = new Properties()
                file("local.properties").withInputStream { properties.load(it) }
                def flutterSdkPath = properties.getProperty("flutter.sdk")
                assert flutterSdkPath != null, "flutter.sdk not set in local.properties"
                return flutterSdkPath
            }()
        
            includeBuild("${'$'}flutterSdkPath/packages/flutter_tools/gradle")
        
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }
        
        plugins {
            id "dev.flutter.flutter-plugin-loader" version "${CV.flutterPluginLoaderVersion}"
            id "com.android.application" version "${CV.androidApplicationVersion}" apply false
            id "org.jetbrains.kotlin.android" version "${CV.jetbrainsAndroidKotlinVersion}" apply false
        }
        
        include ":app"
    """.trimIndent()

    /* Kotlin DSL (settings.gradle.kts) */
    val settingsSrcAsKts: String = """
        pluginManagement {
            val flutterSdkPath = run {
                val properties = java.util.Properties()
                file("local.properties").inputStream().use { properties.load(it) }
                val flutterSdkPath = properties.getProperty("flutter.sdk")
                    ?: throw GradleException("flutter.sdk not set in local.properties")
                return@run flutterSdkPath
            }
        
            includeBuild("${'$'}flutterSdkPath/packages/flutter_tools/gradle")
        
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }
        
        plugins {
            id("dev.flutter.flutter-plugin-loader") version "${CV.flutterPluginLoaderVersion}"
            id("com.android.application") version "${CV.androidApplicationVersion}" apply false
            id("org.jetbrains.kotlin.android") version "${CV.jetbrainsAndroidKotlinVersion}" apply false
        }
        
        include(":app")
    """.trimIndent()

    /* Groovy (build.gradle) */
    val buildGradleSrcAsGroovy: String = """
        buildscript {
            ext.kotlin_version = '${CV.jetbrainsAndroidKotlinVersion}'
            repositories {
                google()
                mavenCentral()
            }
        
            dependencies {
                classpath 'com.android.tools.build:gradle:${CV.androidApplicationVersion}'
                classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${CV.jetbrainsAndroidKotlinVersion}"
            }
        }
        
        allprojects {
            repositories {
                google()
                mavenCentral()
            }
        }
        
        rootProject.buildDir = '../build'
        subprojects {
            project.buildDir = "${'$'}{rootProject.buildDir}/${'$'}{project.name}"
        }
        subprojects {
            project.evaluationDependsOn(':app')
        }
        
        tasks.register("clean", Delete) {
            delete rootProject.buildDir
        }
    """.trimIndent()

    /* Kotlin DSL (build.gradle.kts) */
    val buildGradleSrcAsKts: String = """
        buildscript {
            extra.set("kotlin_version", "${CV.jetbrainsAndroidKotlinVersion}")
            repositories {
                google()
                mavenCentral()
            }
        
            dependencies {
                classpath("com.android.tools.build:gradle:${CV.androidApplicationVersion}")
                classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${CV.jetbrainsAndroidKotlinVersion}")
            }
        }
        
        allprojects {
            repositories {
                google()
                mavenCentral()
            }
        }
        
        allprojects {
            layout.buildDirectory.set(rootDir.resolve("../build/${'$'}{project.name}"))
        }
        subprojects {
            project.evaluationDependsOn(":app")
        }
        
        tasks.register("clean", Delete::class) {
            delete(rootProject.layout.buildDirectory)
        }
    """.trimIndent()

    /* Local properties */
    val localPropertiesAsProps: String = """
        sdk.dir=/root/Android/Sdk
        flutter.sdk=/root/flutter
        cmake.dir=/home/Android/Sdk/cmake/4.3.0
    """.trimIndent()

    /* Gradle properties */
    val gradlePropertiesAsProps: String = """
        android.nonTransitiveRClass=true
        org.gradle.daemon.idletimeout=10800000
        systemProp.user.country=US
        systemProp.user.language=en
        org.gradle.daemon=true
        org.gradle.parallel=true
        org.gradle.jvmargs=-Dfile.encoding\=UTF-8 -Dsun.jnu.encoding\=UTF-8 -Duser.language\=en -Duser.country\=US -Xmx2048m -XX\:MaxMetaspaceSize\=512m -XX\:+HeapDumpOnOutOfMemoryError
        android.enableJetifier=true
        android.useAndroidX=true
        android.defaults.buildfeatures.buildconfig=true
        systemProp.sun.jnu.encoding=UTF-8
        systemProp.file.encoding=UTF-8
        org.gradle.configureondemand=false
    """.trimIndent()

}