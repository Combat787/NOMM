# NOMM - Nuclear Option Mod Manager

A Mod Manager for the Game Nuclear Option

# Features
- Automatic BepInEx installation
- Mod searching
- Mod installing and updating
- Mod conflict warnings
- Mod automatic dependency resolution
- Adding Mods from Files
- Mod toggling
- Mod Uninstalling
- Customizable Theme


## Installation

Download the appropriate file for your platform from the [Latest Release](https://github.com/Combat787/NOMM/releases/latest).

### Windows
* **Portable:** `portable.exe`
* **Installer:** `.msi`

### Linux
* **Debian:** `.deb`
* **Fedora:** `.rpm`
* **Standalone:** `.AppImage`
* **Flatpak:** `.flatpak` *(Flatpak may or may not work)*

To work NOMM retrieves a manifest from [NOMNOM](https://github.com/KopterBuzz/NOMNOM) to get the list of mods. To add your own mods go there.

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
