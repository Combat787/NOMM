package com.combat.nomm

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.File

object RepoMods {
    private val mutex = Mutex()

    val mods: StateFlow<Map<String, Extension>>
        field = MutableStateFlow(emptyMap())

    val latestGameVersion: StateFlow<Version?>
        field = MutableStateFlow(null)

    val isLoading: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val manifestError: StateFlow<String?>
        field = MutableStateFlow(null)

    init {
        fetchManifest()
    }

    fun fetchManifest() {
        scope.launch {
            if (!mutex.tryLock()) {
                Log.log("Manifest fetch already in progress, skipping")
                return@launch
            }
            try {
                isLoading.value = true
                val useFakeManifest = SettingsManager.config.value.fakeManifest
                val networkFetched = if (useFakeManifest) null else NetworkClient.fetchManifest()
                val fetched = networkFetched ?: if (useFakeManifest) {
                        fetchFakeManifest()
                    } else {
                        SettingsManager.cachedManifest.value.manifest.also { fallback ->
                            if (fallback.isEmpty()) {
                                manifestError.value =
                                    "Failed to fetch mod manifest from ${SettingsManager.config.value.manifestUrl}"
                            } else {
                                Log.log("Manifest fetch failed, using cached manifest with ${fallback.size} mods")
                            }
                        }
                    }
                if (networkFetched != null || useFakeManifest) {
                    manifestError.value = null
                }
                val distinctMods = fetched.distinctBy { it.id }.associateBy { it.id }
                ModsSearch.filterTags = distinctMods.map { it.value }.commonTagFilters()
                mods.value = distinctMods
                latestGameVersion.value = fetched.latestGameVersion()
                ServerBrowser.modHashLookup = buildModHashLookup(mods.value.map { it.value })
            } finally {
                isLoading.value = false
                mutex.unlock()
            }
            val updatable = LocalMods.mods.value.filter { it.value.hasUpdate }
                .mapNotNull { mods.value[it.key] }
            if (updatable.isNotEmpty() && !SettingsManager.config.value.ignoreNewUpdates) {
                val hasExistingUpdateNotification = SettingsManager.criticalInformation.any {
                    it.first.contains("Available Mod Update")
                }
                if (!hasExistingUpdateNotification) {
                    SettingsManager.criticalInformation.add(
                        Triple(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("${updatable.size}")
                                }
                                append(" Available Mod Update${if (updatable.size > 1) "s" else ""}")
                            },
                            buildAnnotatedString {
                                append(updatable.joinToString(separator = "\n") { it.displayName })
                            },
                            null
                        )
                    )
                }
            }
        }
    }

    val launchOptionDialog = MutableStateFlow(false)

    fun downloadBepInEx() {
        val url = "https://github.com/BepInEx/BepInEx/releases/download/v5.4.23.4/BepInEx_win_x64_5.4.23.4.zip"
        if (isBepInExInstallationComplete(SettingsManager.gameFolder)) {
            return
        }

        val gameFolder = SettingsManager.gameFolder
        val gameFolderError = validateNuclearOptionGameFolder(gameFolder)
        if (gameFolderError != null || gameFolder == null) {
            reportNommError("Cannot install BepInEx", gameFolderError ?: "The game folder is not configured.")
            return
        }

        if (System.getProperty("os.name").lowercase().let {
                !(it.contains("win") || it.contains("mac"))
            }) {
            launchOptionDialog.update { true }
        }

        Installer.installMod(
            modId = "BepInEx",
            url = url,
            dir = gameFolder,
            hash = null,
            isBepInEx = true,
            onError = { error ->
                reportNommError("BepInEx installation failed", error.message ?: "See the terminal for details.")
            },
            onSuccess = {
                runCatching {
                    val configDir = File(gameFolder, "BepInEx/config")
                    configDir.mkdirs()
                    File(configDir, "BepInEx.cfg").writeText(
                        """
                        [Chainloader]
                        HideManagerGameObject = true
                        """.trimIndent()
                    )
                }.onFailure { error ->
                    reportNommError("BepInEx configuration failed", error.message ?: "Could not write BepInEx.cfg.")
                }
                LocalMods.refresh()
            }
        )
    }

    fun installMod(id: String, version: Version?, processing: MutableSet<String> = mutableSetOf()) {
        if (id in processing) return
        processing.add(id)

        val extension = mods.value[id] ?: return
        val targetArtifact = version?.let { v -> extension.artifacts.find { it.version == v } }
            ?: extension.artifacts.maxByOrNull { it.version }
            ?: return

        val installedMod = LocalMods.mods.value[id]
        val enableAfterInstall = shouldEnableAfterInstall(
            wasInstalled = installedMod != null,
            wasEnabled = installedMod?.enabled == true,
        )
        if (installedMod != null) {
            val currentVersion = installedMod.artifact?.version
            if (currentVersion != null && currentVersion == targetArtifact.version) return
        }

        targetArtifact.dependencies.forEach { installMod(it.id, null, processing) }
        targetArtifact.extends?.let { installMod(it.id, null, processing) }

        val downloadUrl = targetArtifact.resolvedDownloadUrl
        if (downloadUrl == null) {
            reportNommError(
                "Cannot install ${extension.displayName}",
                "This version has no download link in the manifest."
            )
            return
        }

        installMod(extension.id, downloadUrl, targetArtifact.hash) { dir ->
            val metaData = ModMeta(
                id = id,
                artifact = targetArtifact,
            )

            runCatching {
                File(dir, "meta.json").writeText(json.encodeToString(metaData))
                LocalMods.refresh()
                if (enableAfterInstall) {
                    LocalMods.mods.value[id]?.enable()
                }
            }
        }
    }


    fun installMod(
        id: String, url: String, hash: String? = null, onSuccess: (dir: File) -> Unit = {

        }
    ) {
        val gameFolderError = validateNuclearOptionGameFolder(SettingsManager.gameFolder)
        if (gameFolderError != null) {
            reportNommError("Cannot install mod", gameFolderError)
            return
        }

        val bepinexFolder = SettingsManager.bepInExFolder
        if (!isBepInExInstallationComplete(SettingsManager.gameFolder) || bepinexFolder == null) {
            downloadBepInEx()
            return
        }

        val bepinexFolderError = validateWritableDirectory(bepinexFolder)
        if (bepinexFolderError != null) {
            reportNommError("Cannot install mod", bepinexFolderError)
            return
        }


        val installedMod = LocalMods.mods.value[id]
        val wasEnabled = installedMod?.enabled == true
        installedMod?.disable()

        val disabledFolder = File(bepinexFolder, "disabledPlugins")
        if (!disabledFolder.isDirectory && !disabledFolder.mkdirs()) {
            reportNommError("Cannot install mod", "Could not create ${disabledFolder.absolutePath}.")
            return
        }
        val dir = try {
            resolveArchiveEntry(disabledFolder, id)
        } catch (e: SecurityException) {
            reportNommError("Cannot install mod", e.message ?: "Invalid mod id: $id")
            return
        }

        if (dir.exists() && !dir.deleteRecursively()) {
            reportNommError("Cannot install mod", "Could not remove the previous install for $id.")
            return
        }

        Installer.installMod(id, url, dir, hash, onError = {
            if (wasEnabled) {
                LocalMods.mods.value[id]?.enable()
            }
            reportNommError("Mod installation failed", it.message ?: "See the terminal for details.")
        }) {
            onSuccess(dir)
        }
    }
}

internal fun shouldEnableAfterInstall(
    wasInstalled: Boolean,
    wasEnabled: Boolean,
): Boolean = !wasInstalled || wasEnabled
