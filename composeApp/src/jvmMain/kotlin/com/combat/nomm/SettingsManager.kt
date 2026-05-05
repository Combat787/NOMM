package com.combat.nomm

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import io.github.kdroidfilter.nucleus.updater.UpdateInfo
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
enum class Theme {
    LIGHT, DARK, SYSTEM;

    override fun toString(): String = when (this) {
        LIGHT -> "Light"
        DARK -> "Dark"
        SYSTEM -> "System"
    }
}

@Serializable
data class Configuration(
    val theme: Theme = Theme.SYSTEM,
    val gamePath: String? = "",
    val paletteStyle: PaletteStyle = PaletteStyle.Expressive,
    val contrast: Contrast = Contrast.Default,
    val fakeManifest: Boolean = false,
    val manifestUrl: String = "https://kopterbuzz.github.io/NOMNOM/manifest/manifest.json",
    val manifestVersionUrl: String = "https://kopterbuzz.github.io/NOMNOM/manifest/version.json",
    val ignoreManifestVersion: Boolean = false,
    val manifestVersion: Version = Version(0),
    val cachedManifest: Manifest = emptyList(),
    val hueValue: Float = 0.3f,
) {
    val themeColor: Color
        get() = Color.hsv(hueValue * 360f, 1f, 1f)
}

object SettingsManager {
    val config: State<Configuration>
        field = mutableStateOf(load())



    var availableUpdateInfo by mutableStateOf<UpdateInfo?>(null)

    val gameFolder: File? = config.value.gamePath?.let { File(it) }
    val bepInExFolder: File?
        get() = gameFolder?.let { File(it, "BepInEx") }


    private fun load(): Configuration {
        return if (DataStorage.configFile.exists() && DataStorage.configFile.length() > 0) {
            try {
                json.decodeFromString<Configuration>(DataStorage.configFile.readText())
            } catch (_: Exception) {
                createDefaultConfig()
            }
        } else {
            createDefaultConfig()
        }
    }

    private fun createDefaultConfig(): Configuration {
        val path = getGameFolder("Nuclear Option", "NuclearOption.exe")?.path
        val default = Configuration(gamePath = path)
        save(default)
        return default
    }

    fun updateConfig(newConfig: Configuration) {
        config.value = newConfig
        save(newConfig)
    }

    private fun save(config: Configuration) {
        DataStorage.configFile.writeText(json.encodeToString(config))
    }
}