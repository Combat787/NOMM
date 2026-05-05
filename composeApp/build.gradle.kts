import com.codingfeline.buildkonfig.compiler.FieldSpec
import io.github.kdroidfilter.nucleus.desktop.application.dsl.CompressionLevel
import io.github.kdroidfilter.nucleus.desktop.application.dsl.PublishMode
import io.github.kdroidfilter.nucleus.desktop.application.dsl.ReleaseChannel
import io.github.kdroidfilter.nucleus.desktop.application.dsl.ReleaseType
import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat


plugins {
        alias(libs.plugins.kotlinMultiplatform)
        alias(libs.plugins.composeMultiplatform)
        alias(libs.plugins.composeCompiler)
        alias(libs.plugins.nucleus)
        alias(libs.plugins.kotlin.serialization)
        alias(libs.plugins.buildkonfig)
    }

val appVersion = "4.0.0"

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
        
        all {
            languageSettings.enableLanguageFeature("ExplicitBackingFields")
        }
        commonMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)

            implementation(libs.nucleus.core)
            implementation(libs.nucleus.window.jni)
            implementation(libs.nucleus.window.material3)
            implementation(libs.nucleus.darkmode)
            implementation(libs.nucleus.taskbar)
            implementation(libs.nucleus.updater)
            implementation(libs.nucleus.nativehttp)
            implementation(libs.nucleus.aot)

            implementation(libs.nucleus.notif.win)
            implementation(libs.nucleus.notif.mac)
            implementation(libs.nucleus.notif.linux)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs.compose)
            implementation(libs.materialKolor)

            implementation(libs.jetbrains.navigation3.ui)
            
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
    }
}

nucleus.application {
    mainClass = "com.combat.nomm.MainKt"
    jvmArgs += "--enable-native-access=ALL-UNNAMED"
    
    val authorEmail = "787combat787@gmail.com"
    nativeDistributions {
        targetFormats(
            TargetFormat.Portable,
            TargetFormat.AppImage,
            TargetFormat.Msi,
            TargetFormat.Dmg,
            TargetFormat.Rpm,
            TargetFormat.Deb,
            TargetFormat.Flatpak,
            TargetFormat.Snap,
            TargetFormat.Zip
        )

        appName = "Nuclear Option Mod Manager"
        packageName = "NOMM"
        packageVersion = appVersion
        vendor = "Combat"
        description = "A Mod Manager For Nuclear Option"

        cleanupNativeLibs = true
        enableAotCache = true
        
        homepage = "https://github.com/Combat787/NOMM"
        
        compressionLevel = CompressionLevel.Normal

        windows {
            upgradeUuid = "fdac94b6-2774-4802-96c4-67ada2e62a57"
            menuGroup = "Combat"
            shortcut = true
            nsis {
                runAfterFinish = true
                deleteAppDataOnUninstall = false
                installerIcon.set(project.file("packaging/installer.ico"))
                uninstallerIcon.set(project.file("packaging/uninstaller.ico"))
                license.set(project.file("LICENSE"))
            }
        }
        
        linux {
            shortcut = true
            packageName = "Nuclear Option Mod Manager"
            appCategory = "Utility"
            debMaintainer = "Combat <${authorEmail}>"
        }
        
        macOS {
            bundleID = "com.combat.nomm"
        }

        publish {
            publishMode = PublishMode.Auto
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