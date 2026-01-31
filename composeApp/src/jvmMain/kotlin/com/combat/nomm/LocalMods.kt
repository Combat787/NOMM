package com.combat.nomm

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object LocalMods {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }
    val isBepInExInstalled: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val isGameExeFound: StateFlow<Boolean>
        field = MutableStateFlow(false)
    
    val mods: StateFlow<Map<String, ModMeta>>
        field = MutableStateFlow(loadInstalledModMetas(File(SettingsManager.gameFolder, "BepInEx")))
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)



    fun exportMods() {
        scope.launch {
            val exportData = json.encodeToString(
                mods.value.filter { it.value.enabled!! }.map { PackageReference(it.value.id, it.value.artifact?.version) }
            )

            val file = FileKit.openFileSaver(
                suggestedName = "modpack",
                extension = "nomm.json"
            )

            file?.writeString(exportData)
        }
    }
    fun addFromFile() {
        scope.launch {
            val file = FileKit.openFilePicker(
                title = "Add From Files",
            )

            file?.let { platformFile ->
                val pluginsDir = File(SettingsManager.bepInExFolder, "plugins")
                if (!pluginsDir.exists()) pluginsDir.mkdirs()

                val sourceFile = File(platformFile.path)
                val destinationFile = File(pluginsDir, sourceFile.name)

                if (sourceFile.moveTo(destinationFile)) {
                    refresh()
                }
            }
        }
    }
    fun importMods() {
        scope.launch {
            val file = FileKit.openFilePicker(
                title = "Import Modpack",
                type = FileKitType.File(extension = "nomm.json"),
            )

            file?.let { platformFile ->
                try {
                    val jsonString = platformFile.readString()

                    val imported: List<PackageReference> = json.decodeFromString(jsonString)

                    RepoMods.fetchManifest()
                    imported.forEach {
                        mods.value[it.id] ?: run {
                            RepoMods.installMod(it.id,it.version)
                        }
                    }
                    val importedIds = imported.map { it.id }
                    mods.value.forEach { (_, meta) -> 
                        if (importedIds.contains(meta.id)) {
                            meta.enable()
                        } else  {
                            meta.disable()
                        }
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun loadInstalledModMetas(bepinexFolder: File): Map<String, ModMeta> {
        if (!bepinexFolder.exists()) {
            isBepInExInstalled.value = false
            return emptyMap()
        } else  { 
            isBepInExInstalled.value = true
        }

        isGameExeFound.value = File(SettingsManager.gameFolder,"NuclearOption.exe").exists()

        val plugins = File(bepinexFolder, "plugins").apply { mkdirs() }
        val disabled = File(bepinexFolder, "disabledPlugins").apply { mkdirs() }
        val foundMods = mutableMapOf<String, ModMeta>()

        fun scan(root: File, isEnabled: Boolean, depth: Int = 0) {
            if (depth > 10) return
            val children = root.listFiles() ?: return

            for (file in children) {
                if (file.name == "addons" || file.name == "meta.json") continue

                val metaJson = if (file.isDirectory) File(file, "meta.json") else null
                val meta = if (metaJson?.exists() == true) {
                    runCatching { RepoMods.json.decodeFromString<ModMeta>(metaJson.readText()) }.getOrNull()
                } else null

                val id = meta?.id ?: file.nameWithoutExtension
                val existing = foundMods[id]

                if (existing != null) {
                    val currentVersion = meta?.artifact?.version
                    val existingVersion = existing.artifact?.version

                    val isNewer = if (currentVersion != null && existingVersion != null) {
                        currentVersion > existingVersion
                    } else {
                        file.lastModified() > (existing.file?.lastModified() ?: 0L)
                    }

                    if (isNewer) {
                        existing.file?.deleteRecursively()
                    } else {
                        file.deleteRecursively()
                        continue
                    }
                }

                foundMods[id] = (meta ?: ModMeta(id = id)).copy(
                    file = file,
                    enabled = isEnabled,
                    isUnidentified = meta == null
                )

                if (file.isDirectory) {
                    val addonFolder = File(file, "addons")
                    if (addonFolder.exists()) scan(addonFolder, isEnabled, depth + 1)
                }
            }
        }

        scan(plugins, true)
        scan(disabled, false)
        foundMods.map { (string, meta) ->
            val repoMod = RepoMods.mods.value.find { it.id == meta.id }
            val artifact = repoMod?.artifacts?.maxByOrNull { it.version }
            val version = meta.artifact?.version

            if (artifact == null) {
                Pair(string, meta)
            } else {
                Pair(
                    string,
                    meta.copy(cachedExtension = repoMod, hasUpdate = version?.let { it <= artifact.version } ?: true))
            }
        }
        return foundMods
    }

    fun updateModState(id: String, meta: ModMeta?) {
        mods.update { current ->
            val newMap = current.toMutableMap()
            if (meta == null) newMap.remove(id) else newMap[id] = meta
            newMap
        }
    }

    fun refresh() {
        val bepInExDir = File(SettingsManager.gameFolder, "BepInEx")
        val freshMetas = loadInstalledModMetas(bepInExDir)
        mods.update { freshMetas }
    }
}

@Serializable
data class ModMeta(
    val id: String,
    val artifact: Artifact? = null,
    val cachedExtension: Extension? = null,
    @Transient val enabled: Boolean? = null,
    @Transient val file: File? = null,
    @Transient val isUnidentified: Boolean = false,
    @Transient val hasUpdate: Boolean = false,
) {
    fun enable(): Boolean {
        val currentSelf = LocalMods.mods.value[id] ?: this
        val currentFile = currentSelf.file ?: return false
        if (currentSelf.enabled == true && currentFile.exists()) return true

        artifact?.dependencies?.forEach { dep ->
            val depMod = LocalMods.mods.value[dep.id] ?: return false
            if (!depMod.enable()) return false
        }

        val parentId = artifact?.extends?.id
        val targetDir = if (parentId != null) {
            val parentMod = LocalMods.mods.value[parentId] ?: return false
            if (!parentMod.enable()) return false
            File(parentMod.file, "addons/$id")
        } else {
            File(SettingsManager.bepInExFolder, "plugins/${currentFile.name}")
        }

        if (currentFile.moveTo(targetDir)) {
            LocalMods.updateModState(id, copy(file = targetDir, enabled = true))
            return true
        }
        return false
    }

    fun disable() {
        val currentSelf = LocalMods.mods.value[id] ?: this
        val currentFile = currentSelf.file ?: return
        if (currentSelf.enabled == false || !currentFile.exists()) return

        LocalMods.mods.value.values.toList().forEach { other ->
            val isExtending = other.artifact?.extends?.id == id
            val isDependent = other.artifact?.dependencies?.any { it.id == id } == true
            if (isExtending || isDependent) {
                other.disable()
            }
        }

        val destination = File(SettingsManager.bepInExFolder, "disabledPlugins/${currentFile.name}")
        if (currentFile.moveTo(destination)) {
            LocalMods.updateModState(id, copy(file = destination, enabled = false))
        }
    }

    fun uninstall() {
        disable()
        (LocalMods.mods.value[id]?.file ?: file)?.deleteRecursively()
        LocalMods.updateModState(id, null)
    }

    fun update() {
        RepoMods.installMod(id, null)
    }

}

fun File.moveTo(destination: File): Boolean {
    if (!this.exists()) return false
    if (this.canonicalPath == destination.canonicalPath) return true

    return runCatching {
        destination.deleteRecursively()
        destination.parentFile?.mkdirs()
        Files.move(
            toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
        true
    }.getOrElse {
        runCatching {
            this.copyRecursively(destination, overwrite = true)
            this.deleteRecursively()
            true
        }.getOrDefault(false)
    }
}