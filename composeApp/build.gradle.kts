import com.codingfeline.buildkonfig.compiler.FieldSpec
import dev.nucleusframework.desktop.application.dsl.*


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)


    alias(libs.plugins.nucleus)


    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.buildkonfig)
}

val appVersion = "5.0.0"

val changelog = """
    Added a Server List where you can directly join other Servers and if the Server supports it or the host is using NOMM can autoinstall Mods.
    For Dedicated Server Hosts please check out https://github.com/RaylaValdez/NOSMR to implement support for your own Servers.
    Do not expect immediate support by every Dedicated Server.
    And big thanks to Gerry of Ravine/RaylaValdez who had the idea and implemented most of the backend for this.
""".trimIndent()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

kotlin {
    jvm()
    jvmToolchain(25)
    sourceSets {
        commonMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.nucleus.core)
            implementation(libs.nucleus.nucleus.application)
            implementation(libs.nucleus.decorated.window.tao)
            implementation(libs.nucleus.window.tao)
            implementation(libs.nucleus.window.material3)
            implementation(libs.nucleus.darkmode)
            implementation(libs.nucleus.taskbar)
            implementation(libs.nucleus.updater)
            implementation(libs.nucleus.native.http)
            implementation(libs.nucleus.native.http.ktor)
            implementation(libs.nucleus.aot)

            implementation(libs.nucleus.notif.win)
            implementation(libs.nucleus.notif.mac)
            implementation(libs.nucleus.notif.linux)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutinesSwing)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs.compose)

            implementation(libs.coil)
            implementation(libs.coil.ktor)

            implementation(libs.materialKolor)

            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.steamworks4j)

            implementation("net.sf.sevenzipjbinding:sevenzipjbinding:16.02-2.01")
            implementation("net.sf.sevenzipjbinding:sevenzipjbinding-all-platforms:16.02-2.01")
            implementation("org.slf4j:slf4j-simple:2.0.17")
        }
    }
}

buildkonfig {
    packageName = "com.combat.nomm"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "VERSION", appVersion)
        buildConfigField(FieldSpec.Type.STRING, "CHANGELOG", changelog)
    }
}

nucleus.application {
    mainClass = "com.combat.nomm.MainKt"
    jvmArgs += listOf(
        "--enable-native-access=ALL-UNNAMED",
    )

    val authorEmail = "787combat787@gmail.com"
    nativeDistributions {
        targetFormats(
            TargetFormat.Portable,
            TargetFormat.AppImage,
            TargetFormat.Msi,
            TargetFormat.Rpm,
            TargetFormat.Deb,
            TargetFormat.Flatpak,
            TargetFormat.Zip
        )

        modules(
            "java.base", "java.desktop", "java.logging",
            "jdk.security.auth",
            "java.management",
            "java.desktop",
            "java.naming",
            "jdk.unsupported"
        )
        
        appName = "Nuclear Option Mod Manager"
        packageName = "NOMM"
        packageVersion = appVersion
        vendor = "Combat"
        description = "A Mod Manager For Nuclear Option"

        artifactName = $$"${name}-${version}-${os}-${arch}.${ext}"

        cleanupNativeLibs = true
        enableAotCache = false
        homepage = "https://github.com/Combat787/NOMM"

        compressionLevel = CompressionLevel.Normal
        fileAssociation(
            mimeType = "application/nommpack",
            extension = "nommpack",
            description = "A Modpack for the Nuclear Option Mod Manager",
            linuxIconFile = project.file("icons/icon.png"),
            windowsIconFile = project.file("icons/icon.ico"),
            macOSIconFile = project.file("icons/icon.icns"),
        )
        windows {
            upgradeUuid = "fdac94b6-2774-4802-96c4-67ada2e62a57"
            menuGroup = "Combat"
            shortcut = true
            iconFile.set(project.file("icons/icon.ico"))
            nsis {
                runAfterFinish = true
                deleteAppDataOnUninstall = false
                installerIcon.set(project.file("packaging/installer.ico"))
                uninstallerIcon.set(project.file("packaging/uninstaller.ico"))
                license.set(project.file("LICENSE"))
            }
            console = true
        }

        linux {
            shortcut = true
            packageName = "NOMM"
            appCategory = "Utility"
            debMaintainer = "Combat <${authorEmail}>"
            iconFile.set(project.file("icons/icon.png"))
        }

        macOS {
            bundleID = "com.combat.nomm"
        }

        publish {
            publishMode = PublishMode.Never
            github {
                enabled = true
                owner = "Combat787"
                repo = "NOMM"
                token = System.getenv("GITHUB_TOKEN")
                channel = ReleaseChannel.Latest
                releaseType = ReleaseType.Release
            }
        }
    }
    buildTypes {
        release {
            proguard {
                version = "7.9.1"
                isEnabled = false
                optimize = true
                obfuscate.set(false)
                joinOutputJars.set(true)
            }
        }
    }
}
