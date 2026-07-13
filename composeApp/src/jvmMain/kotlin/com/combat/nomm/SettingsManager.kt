package com.combat.nomm

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.WindowPlacement
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import io.github.kdroidfilter.nucleus.updater.UpdateInfo
import io.github.vinceglb.filekit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.coroutines.Continuation
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
data class CachedManifest(
    val version: Version = Version(0),
    val manifest: Manifest = emptyList(),
)


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
    val ignoreNewUpdates: Boolean = false,
    val hueValue: Float = 0.3f,
    val placement: WindowPlacement = WindowPlacement.Floating,
) {
    val themeColor: Color
        get() = Color.hsv(hueValue * 360f, 1f, 1f)
}

@OptIn(FlowPreview::class)
object SettingsManager {
    val saveSignal = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    val config: State<Configuration>
        field = mutableStateOf(loadConfiguration())

    val cachedManifest: State<CachedManifest>
        field = mutableStateOf(loadCachedManifest())


    val criticalInformation = mutableStateListOf<Triple<String, String, Continuation<Unit>?>>()

    var availableUpdateInfo by mutableStateOf<UpdateInfo?>(null)

    val gameFolder: File?
        get() = config.value.gamePath?.let { File(it) }
    val bepInExFolder: File?
        get() = gameFolder?.let { File(it, "BepInEx") }

    fun loadCachedManifest(): CachedManifest = runBlocking {
        if ((FileKit.filesDir / "manifest.json").exists()) {
            try {
                return@runBlocking json.decodeFromString<CachedManifest>((FileKit.filesDir / "manifest.json").readString())
            } catch (_: Exception) {
            }
        }
        CachedManifest()
    }


    fun loadConfiguration(): Configuration = runBlocking {
        if ((FileKit.filesDir / "config.json").exists()) {
            try {
                return@runBlocking json.decodeFromString<Configuration>((FileKit.filesDir / "config.json").readString())
            } catch (_: Exception) {
            }
        }

        val path = getGameFolder("Nuclear Option", "NuclearOption.exe")?.path
        val default = Configuration(gamePath = path)
        saveSignal.tryEmit(Unit)
        default
    }

    init {
        scope.launch(Dispatchers.IO) {
            saveSignal
                .debounce(5.seconds)
                .collect {
                    saveConfig()
                }
        }
    }

    fun updateCachedManifest(newCachedManifest: CachedManifest) {
        cachedManifest.value = newCachedManifest
        scope.launch {
            saveCachedManifest()
        }
    }

    fun updateConfig(newConfig: Configuration) {
        config.value = newConfig
        saveSignal.tryEmit(Unit)
    }

    suspend fun saveConfig() {
        withContext(Dispatchers.IO) {
            val currentConfig = config.value
            (FileKit.filesDir / "config.json").writeString(json.encodeToString(currentConfig))
        }
    }

    suspend fun saveCachedManifest() {
        withContext(Dispatchers.IO) {
            val currentCachedManifest = cachedManifest.value
            (FileKit.filesDir / "manifest.json").writeString(json.encodeToString(currentCachedManifest))
        }
    }
}