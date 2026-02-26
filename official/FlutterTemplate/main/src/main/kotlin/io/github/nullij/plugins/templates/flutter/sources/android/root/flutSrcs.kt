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
object flutSrcs {

    val appEnAsArb: String = """
        {
          "@@locale": "en",
          "appTitle": "Flutter Activity Template",
          "helloMessage": "Hello from flutter activity template"
        }
    """.trimIndent()

    val mainAsDart: String = """
        import 'package:dynamic_color/dynamic_color.dart';
        import 'package:flutter/material.dart';
        import 'package:flutter_localizations/flutter_localizations.dart';
        import 'package:flutter_m3_template/l10n/app_localizations.dart';
        
        
        void main() {
          runApp(const MyApp());
        }
        
        class MyApp extends StatelessWidget {
          const MyApp({super.key});
        
          static final _defaultLightColorScheme = ColorScheme.fromSeed(
            seedColor: Colors.deepPurple,
            brightness: Brightness.light,
          );
        
          static final _defaultDarkColorScheme = ColorScheme.fromSeed(
            seedColor: Colors.deepPurple,
            brightness: Brightness.dark,
          );
        
          @override
          Widget build(BuildContext context) {
            return DynamicColorBuilder(
              builder: (ColorScheme? lightDynamic, ColorScheme? darkDynamic) {
                return MaterialApp(
                  onGenerateTitle: (context) => AppLocalizations.of(context)!.appTitle,
                  debugShowCheckedModeBanner: false,
                  localizationsDelegates: const [
                    AppLocalizations.delegate,
                    GlobalMaterialLocalizations.delegate,
                    GlobalWidgetsLocalizations.delegate,
                    GlobalCupertinoLocalizations.delegate,
                  ],
                  supportedLocales: AppLocalizations.supportedLocales,
                  theme: ThemeData(
                    useMaterial3: true,
                    colorScheme: lightDynamic?.harmonized() ?? _defaultLightColorScheme,
                  ),
                  darkTheme: ThemeData(
                    useMaterial3: true,
                    colorScheme: darkDynamic?.harmonized() ?? _defaultDarkColorScheme,
                  ),
                  themeMode: ThemeMode.system,
                  home: const HomePage(),
                );
              },
            );
          }
        }
        
        class HomePage extends StatelessWidget {
          const HomePage({super.key});
        
          @override
          Widget build(BuildContext context) {
            final l10n = AppLocalizations.of(context)!;
        
            return Scaffold(
              body: Center(
                child: Card(
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(24),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
                    child: Text(
                      l10n.helloMessage,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                ),
              ),
            );
          }
        }    
    """.trimIndent()

    val l10nAsYaml: String = """
        arb-dir: lib/l10n
        template-arb-file: app_en.arb
        output-localization-file: app_localizations.dart
    """.trimIndent()

    val pubSpecAsYaml: String = """
        name: flutter_m3_template
        description: A modern Flutter app template with Material 3 design
        publish_to: 'none'
        version: 1.0.0+1
        
        environment:
          sdk: '>=3.5.0 <4.0.0'
        
        dependencies:
          flutter:
            sdk: flutter
          cupertino_icons: ^1.0.8
          dynamic_color: ^1.7.0
          flutter_localizations:
            sdk: flutter
        
        dev_dependencies:
          flutter_test:
            sdk: flutter
          flutter_lints: ^5.0.0
        
        flutter:
          uses-material-design: true
          generate: true
          
          # assets:
          #   - images/
          
          # fonts:
          #   - family: Roboto
          #     fonts:
          #       - asset: fonts/Roboto-Regular.ttf
    """.trimIndent()


}