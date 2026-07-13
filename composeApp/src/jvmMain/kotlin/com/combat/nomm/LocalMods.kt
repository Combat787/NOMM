package com.combat.nomm

import io.github.vinceglb.filekit.*
import io.github.vinceglb.filekit.dialogs.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.time.Clock

object LocalMods {
    val isBepInExInstalled: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val isGameExeFound: StateFlow<Boolean>
        field = MutableStateFlow(false)

    val mods: StateFlow<Map<String, ModMeta>>
        field = MutableStateFlow(emptyMap())



    fun exportMods() {
        scope.launch {
            val byteStream = ByteArrayOutputStream()
            ZipOutputStream(byteStream).use { zipStream ->
                val modList = json.encodeToString(
                    mods.value.filter { it.value.enabled == true }
                        .map { PackageReference(it.value.id, it.value.artifact?.version) }
                )
                zipStream.putNextEntry(ZipEntry("modlist.nomm.json"))
                zipStream.write(modList.toByteArray())
                zipStream.closeEntry()

                mods.value
                    .asSequence()
                    .filter { it.value.enabled == true }
                    .filter { it.value.isUnidentified }.mapNotNull { it.value.file }
                    .filter { it.exists() }
                    .toList()
                    .forEach { modFile ->
                        if (modFile.isDirectory) {
                            modFile.walkTopDown().forEach { file ->
                                val relativePath = file.relativeTo(modFile.parentFile).path.replace('\\', '/')
                                if (file.isDirectory) {
                                    zipStream.putNextEntry(ZipEntry("mods/$relativePath/"))
                                } else {
                                    zipStream.putNextEntry(ZipEntry("mods/$relativePath"))
                                    file.inputStream().use { fileStream -> fileStream.copyTo(zipStream) }
                                }
                                zipStream.closeEntry()
                            }
                        } else {
                            zipStream.putNextEntry(ZipEntry("mods/${modFile.name}"))
                            modFile.inputStream().use { fileStream -> fileStream.copyTo(zipStream) }
                            zipStream.closeEntry()
                        }
                    }
            }
            val currentInstant = Clock.System.now()
            val dt = currentInstant.toLocalDateTime(TimeZone.currentSystemDefault())

            val year = dt.year
            val month = dt.month.number.toString().padStart(2, '0')
            val day = dt.day.toString().padStart(2, '0')
            val hour = dt.hour.toString().padStart(2, '0')
            val minute = dt.minute.toString().padStart(2, '0')
            val second = dt.second.toString().padStart(2, '0')

            val file = FileKit.openFileSaver(
                suggestedName = "${year}-${month}-${day}_${hour}-${minute}-${second}",
                defaultExtension = "nommpack",
                directory = null,
                dialogSettings = FileKitDialogSettings.createDefault()
            )

            file?.write(byteStream.toByteArray())
        }
    }

    fun addFilesToPlugins(files: List<File>) {
        val pluginsDir = File(SettingsManager.bepInExFolder, "plugins")
        if (!pluginsDir.exists()) pluginsDir.mkdirs()
        files.forEach { file ->
            val destinationFile = File(pluginsDir, file.name)
            file.moveTo(destinationFile)
        }
        refresh()
    }

    fun addFromFile() {
        scope.launch {
            val files = FileKit.openFilePicker(
                mode = FileKitMode.Multiple(),
                dialogSettings = FileKitDialogSettings("Add From Files"),
            )

            if (!files.isNullOrEmpty()) {
                addFilesToPlugins(files.map { it.file })
            }
        }
    }

    fun importMods() {
        scope.launch {
            val file = FileKit.openFilePicker(
                dialogSettings = FileKitDialogSettings("Import Modpack"),
                type = FileKitType.File("nomm.json", "nommpack"),
            )

            importMods(file)
        }
    }
    
    fun importMods(file: PlatformFile?) {
        scope.launch {
            file?.let { platformFile ->
                val jsonString: String? = if (file.extension == "json") {
                    platformFile.readString()
                } else {
                    var modlist: String? = null
                    val warnedMods = mutableSetOf<String>()

                    ZipInputStream(file.readBytes().inputStream()).use { zipStream ->
                        var entry = zipStream.nextEntry

                        while (entry != null) {
                            when {
                                entry.name.endsWith("modlist.nomm.json") -> {
                                    if (!entry.isDirectory) {
                                        modlist = zipStream.readBytes().decodeToString()
                                    }
                                }

                                entry.name.startsWith("mods/") -> {
                                    val fileName = entry.name.removePrefix("mods/")
                                    if (fileName.isNotEmpty()) {
                                        val rootModName = fileName.substringBefore('/')
                                        if (warnedMods.add(rootModName)) {
                                            suspendCancellableCoroutine { continuation ->
                                                SettingsManager.criticalInformation.add(
                                                    Triple(
                                                        "Modpack includes the Local Mod $rootModName",
                                                        "Make sure you trust the source, or remove the mod to stay safe.",
                                                        continuation
                                                    )
                                                )
                                            }
                                        }

                                        val pluginsDir = File(SettingsManager.bepInExFolder, "plugins")
                                        val destinationFile = File(pluginsDir, fileName)
                                        
                                        if (entry.isDirectory) {
                                            destinationFile.mkdirs()
                                        } else {
                                            destinationFile.parentFile?.mkdirs()
                                            if (destinationFile.exists()) destinationFile.delete()

                                            destinationFile.outputStream().use { outStream ->
                                                zipStream.copyTo(outStream)
                                            }
                                        }
                                    }
                                }
                            }
                            entry = zipStream.nextEntry
                        }
                    }
                    refresh()
                    modlist ?: run {
                        suspendCancellableCoroutine { continuation ->
                            SettingsManager.criticalInformation.add(
                                Triple(
                                    "Modpack does not include a Modlist.",
                                    "Local Mods from the Modpack have been added.",
                                    continuation
                                )
                            )
                        }
                        null
                    }
                }

                val imported = jsonString?.let { json.decodeFromString<List<PackageReference>>(it) }

                RepoMods.fetchManifest()
                imported?.forEach {
                    mods.value[it.id] ?: run {
                        RepoMods.installMod(it.id, it.version)
                    }
                }

                val importedIds = imported?.map { it.id }
                mods.value.forEach { (_, meta) ->
                    if (importedIds?.contains(meta.id) == true) {
                        meta.enable()
                    } else {
                        meta.disable()
                    }
                }
            }
        }
    }

    fun loadInstalledModMetas() {
        val bepinexFolder = SettingsManager.bepInExFolder
        if (bepinexFolder?.exists() == true) {
            isBepInExInstalled.value = true
        } else {
            isBepInExInstalled.value = false
            mods.update { emptyMap() }
            return
        }

        isGameExeFound.value = File(SettingsManager.gameFolder, "NuclearOption.exe").exists()

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
                    runCatching { json.decodeFromString<ModMeta>(metaJson.readText()) }.getOrNull()
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

        mods.update { foundMods }
        recalculateAllProblems()
    }

    fun recalculateAllProblems() {
        mods.update { current ->
            current.mapValues { (_, meta) ->
                val repoMod = RepoMods.mods.value.find { it.id == meta.id }
                val artifact = repoMod?.artifacts?.maxByOrNull { it.version }
                val hasUpdate =
                    if (artifact == null) false else meta.artifact?.version?.let { it < artifact.version } ?: true

                val probs = meta.retrieveProblems()
                meta.copy(
                    hasUpdate = hasUpdate,
                    problems = probs,
                )
            }
        }
    }

    fun updateModState(id: String, meta: ModMeta?) {
        mods.update { current ->
            val newMap = current.toMutableMap()
            if (meta == null) newMap.remove(id) else newMap[id] = meta
            newMap
        }
        recalculateAllProblems()
    }

    fun refresh() {
        loadInstalledModMetas()
        RepoMods.fetchManifest()
    }

    fun enableAll() {
        mods.value.forEach { (_, meta) -> 
            meta.enable()
        }
    }

    fun disableAll() {
        mods.value.forEach { (_, meta) -> 
            meta.disable()
        }
    }
}

@Serializable
data class ModMeta(
    val id: String,
    val artifact: Artifact? = null,
    @Transient val enabled: Boolean? = null,
    @Transient val file: File? = null,
    @Transient val isUnidentified: Boolean = false,
    @Transient val hasUpdate: Boolean = false,
    @Transient val problems: List<String> = emptyList(),
) {
    fun retrieveProblems(): List<String> {
        if (enabled != true) return emptyList()

        val foundProblems = mutableListOf<String>()

        artifact?.dependencies?.forEach { dep ->
            val depMod = LocalMods.mods.value[dep.id]
            if (depMod == null) {
                foundProblems.add("Dependency ${dep.id} not found")
            } else if (depMod.enabled == false) {
                foundProblems.add("Dependency ${dep.id} is disabled")
            }
        }

        artifact?.extends?.id?.let { parentId ->
            val parentMod = LocalMods.mods.value[parentId]
            if (parentMod == null) {
                foundProblems.add("Extended $parentId not found")
            } else if (parentMod.enabled == false) {
                foundProblems.add("Extended $parentId is disabled")
            }
        }

        return foundProblems
    }

    fun resolveProblems() {
        if (enabled != true) return

        artifact?.dependencies?.forEach { dep ->
            val depMod = LocalMods.mods.value[dep.id]
            if (depMod == null) {
                RepoMods.installMod(dep.id, null)
            } else if (depMod.enabled == false) {
                depMod.enable()
            }
        }

        artifact?.extends?.id?.let { parentId ->
            val parentMod = LocalMods.mods.value[parentId]
            if (parentMod == null) {
                RepoMods.installMod(parentId, null)
            } else if (parentMod.enabled == false) {
                parentMod.enable()
            }
        }
        LocalMods.refresh()
    }

    fun enable(): Boolean {
        val currentSelf = LocalMods.mods.value[id] ?: this
        val currentFile = currentSelf.file ?: return false
        if (currentSelf.enabled == true && currentFile.exists()) return true

        artifact?.extends?.id?.let { parentId ->
            val parentMod = LocalMods.mods.value[parentId] ?: return false
            if (parentMod.enabled != true) {
                val success = parentMod.enable()
                if (!success) return false
            }
        }

        val parentId = artifact?.extends?.id
        val targetDir = if (parentId != null) {
            val parentMod = LocalMods.mods.value[parentId] ?: return false
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

        LocalMods.mods.value.values.forEach { other ->
            if (other.artifact?.extends?.id == id && other.enabled == true) {
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