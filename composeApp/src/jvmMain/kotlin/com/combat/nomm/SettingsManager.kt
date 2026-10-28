package com.combat.nomm

import androidx.compose.runtime.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.window.WindowPlacement
import com.materialkolor.Contrast
import com.materialkolor.PaletteStyle
import dev.nucleusframework.updater.UpdateInfo
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
    val gamePath: String? = null,
    val paletteStyle: PaletteStyle = PaletteStyle.Expressive,
    val contrast: Contrast = Contrast.Default,
    val fakeManifest: Boolean = false,
    val manifestUrl: String = "https://kopterbuzz.github.io/NOMNOM/manifest/manifest.json",
    val manifestVersionUrl: String = "https://kopterbuzz.github.io/NOMNOM/manifest/version.json",
    val ignoreHashMismatch: Boolean = false,
    val ignoreNewUpdates: Boolean = false,
    val nosmr: Boolean = true,
    val steamworks: Boolean = true,
    val seedColorHSVColor: HSVColor = HSVColor(0.3f, 1f),
    val customScheme: Boolean = false,
    val primaryColorHSVColor: HSVColor = HSVColor(0.3f, 1f),
    val secondaryColorHSVColor: HSVColor = HSVColor(0.3f, 1f),
    val tertiaryColorHSVColor: HSVColor = HSVColor(0.3f, 1f),
    val neutralColorHSVColor: HSVColor = HSVColor(0.3f, 1f),
    val neutralVariantColorHSVColor: HSVColor = HSVColor(0.3f, 1f),
    val errorColorHSVColor: HSVColor = HSVColor(0.3f, 1f),
    val placement: WindowPlacement = WindowPlacement.Floating,
)

@OptIn(FlowPreview::class)
object SettingsManager {
    val saveSignal = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    val config: State<Configuration>
        field = mutableStateOf(loadConfiguration())

    val cachedManifest: State<CachedManifest>
        field = mutableStateOf(loadCachedManifest())


    val criticalInformation = mutableStateListOf<Triple<AnnotatedString, AnnotatedString, Continuation<Unit>?>>()

    var availableUpdateInfo by mutableStateOf<UpdateInfo?>(null)

    val gameFolder: File?
        get() = config.value.gamePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it).absoluteFile }
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

fun reportNommError(title: String, message: String) {
    Log.log("$title: $message")
    SettingsManager.criticalInformation.add(
        Triple(
            buildAnnotatedString { append(title) },
            buildAnnotatedString { append(message) },
            null
        )
    )
}
