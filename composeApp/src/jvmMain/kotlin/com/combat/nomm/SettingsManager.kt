package com.combat.nomm

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
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
    val manifestUrl: String = "https://kopterbuzz.github.io/NOModManifestTesting/manifest/manifest.json",
    @Serializable(with = ColorSerializer::class) val themeColor: Color = Color.Green,
)

object SettingsManager {
    val config: State<Configuration>
        field = mutableStateOf(load())

    val gameFolder: File? = config.value.gamePath?.let { File(it) }
    val bepInExFolder: File?
        get() = gameFolder?.let { File(it, "BepInEx") }


    private fun load(): Configuration {
        return if (DataStorage.configFile.exists() && DataStorage.configFile.length() > 0) {
            try {
                DataStorage.json.decodeFromString<Configuration>(DataStorage.configFile.readText())
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
        DataStorage.configFile.writeText(DataStorage.json.encodeToString(config))
    }
}