package com.combat.nomm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import java.io.File

object RepoMods {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    val mods: StateFlow<List<Extension>>
        field = MutableStateFlow<List<Extension>>(emptyList())

    val isLoading: StateFlow<Boolean>
        field = MutableStateFlow(false)

    init {
        fetchManifest()
    }

    fun fetchManifest() {
        scope.launch {
            if (!mutex.tryLock()) return@launch
            try {
                isLoading.value = true
                val fetched = if (SettingsManager.config.value.fakeManifest) {
                    fetchFakeManifest()
                } else {
                    NetworkClient.fetchManifest(SettingsManager.config.value.manifestUrl) ?: SettingsManager.config.value.cachedManifest
                }
                mods.value = fetched
            } finally {
                isLoading.value = false
                mutex.unlock()
            }
        }
    }

    val launchOptionDialog = MutableStateFlow(false)

    fun downloadBepInEx() {
        val url = "https://github.com/BepInEx/BepInEx/releases/download/v5.4.23.4/BepInEx_win_x64_5.4.23.4.zip"
        val gameFolder = SettingsManager.gameFolder ?: return
        if (LocalMods.isBepInExInstalled.value) {
            return
        }

        if (System.getProperty("os.name").lowercase().let {
                !(it.contains("win") || it.contains("mac"))
            }) {
            launchOptionDialog.update { true }
        }

        val configDir = File(gameFolder, "BepInEx/config")
        configDir.mkdirs()
        val config = File(configDir, "BepInEx.cfg")
        config.createNewFile()
        config.writeText("""
            [Chainloader]
            HideManagerGameObject = true
            """.trimIndent())
        Installer.installMod("BepInEx", url, gameFolder, true) {
            LocalMods.refresh()
        }
    }

    fun installMod(id: String, version: Version?, processing: MutableSet<String> = mutableSetOf()) {
        val bepinexFolder = SettingsManager.bepInExFolder
        if (bepinexFolder == null || !bepinexFolder.exists()) {
            downloadBepInEx()
            return
        }

        if (id in processing) return
        processing.add(id)

        val extension = mods.value.find { it.id == id } ?: return
        val targetArtifact = version?.let { v -> extension.artifacts.find { it.version == v } }
            ?: extension.artifacts.maxByOrNull { it.version }
            ?: return

        val installedMod = LocalMods.mods.value[id]
        if (installedMod != null) {
            val currentVersion = installedMod.artifact?.version
            if (currentVersion != null && currentVersion == targetArtifact.version) return
        }

        targetArtifact.dependencies.forEach { installMod(it.id, null, processing) }
        targetArtifact.extends?.let { installMod(it.id, null, processing) }

        installedMod?.disable()

        val disabledFolder = File(bepinexFolder, "disabledPlugins").apply { mkdirs() }
        val dir = File(disabledFolder, id)

        if (dir.exists()) dir.deleteRecursively()
        if (!dir.mkdirs()) return

        Installer.installMod(extension.id, targetArtifact.downloadUrl, dir) {
            val metaData = ModMeta(
                id = extension.id,
                artifact = targetArtifact,
            )

            runCatching {
                File(dir, "meta.json").writeText(json.encodeToString(metaData))
                LocalMods.refresh()
                LocalMods.mods.value[extension.id]?.enable()
            }
        }
    }
}