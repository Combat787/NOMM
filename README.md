# NOMM - Nuclear Option Mod Manager

A Mod Manager for the Game Nuclear Option

Features:
- Automatic BepInEx installation
- Mod searching
- Mod installing and updating
- Mod conflict warnings
- Mod automatic dependency resolution
- Adding Mods from Files
- Mod toggling
- Mod Uninstalling
- Customizable Theme


To work NOMM retrieves a manifest from [NOMNOM](https://kopterbuzz.github.io/NOModManifestTesting) to get the list of mods. To add your own mods go there.

App Icon made by Shumatsu

This is a Kotlin Multiplatform project targeting Desktop (JVM).

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
