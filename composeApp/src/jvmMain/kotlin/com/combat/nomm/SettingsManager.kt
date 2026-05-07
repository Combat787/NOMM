package com.combat.nomm

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowState
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import io.github.kdroidfilter.nucleus.updater.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.time.Duration.Companion.seconds

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
    val ignoreHashMismatch: Boolean = false,
    val manifestVersion: Version = Version(0),
    val cachedManifest: Manifest = emptyList(),
    val hueValue: Float = 0.3f,
    @Serializable(with = WindowStateSerializer::class)
    val windowState: WindowState = WindowState(),
) {
    val themeColor: Color
        get() = Color.hsv(hueValue * 360f, 1f, 1f)
}

@OptIn(FlowPreview::class)
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
        saveSignal.tryEmit(Unit)
        return default
    }

    val saveSignal = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    init {
        scope.launch(Dispatchers.IO) {
            saveSignal
                .debounce(5.seconds)
                .collect {
                    saveToFile()
                }
        }
    }

    fun updateConfig(newConfig: Configuration) {
        config.value = newConfig
        saveSignal.tryEmit(Unit)
    }

    suspend fun saveToFile() {
        withContext(Dispatchers.IO) {
            val currentConfig = config.value
            DataStorage.configFile.writeText(json.encodeToString(currentConfig))
        }
    }
}