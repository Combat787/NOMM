import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)

    alias(libs.plugins.kotlin.serialization)
}



kotlin {
    jvm()
    jvmToolchain(21)
    sourceSets {
        sourceSets.all {
            languageSettings.enableLanguageFeature("ExplicitBackingFields")
        }
        commonMain.dependencies {
            implementation(kotlin("reflect"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(projects.shared)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs)
            implementation(libs.filekit.dialogs.compose)


            implementation(libs.compose.colorpicker)
            implementation(libs.materialKolor)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.cio)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.combat.nomm.MainKt"

        nativeDistributions {

            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)

            packageVersion = "1.0.0"
            packageName = "Nuclear Option Mod Manager"
            vendor = "Combat"
            description = "A Mod Manager For Nuclear Option"



            windows {
                dirChooser = true
                perUserInstall = true
                shortcut = true
                menu = true
                
                menuGroup = "Combat"
                upgradeUuid = "fdac94b6-2774-4802-96c4-67ada2e62a57"
            }

            macOS {
                bundleID = "com.combat.nomm"
            }

            linux {
                shortcut = true
            }

            buildTypes.release {
                proguard {
                    isEnabled = false
                }
            }
        }
    }
}
