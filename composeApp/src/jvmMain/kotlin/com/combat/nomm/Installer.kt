package com.combat.nomm

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.util.ByteArrayStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

object Installer {
    private val locks = ConcurrentHashMap<String, Mutex>()
    val bepinexStatus = MutableStateFlow<TaskState?>(null)
    val installStatuses = MutableStateFlow<Map<String, TaskState>>(emptyMap())

    fun installMod(
        modId: String, url: String, dir: File,
        hash: String?,
        isBepInEx: Boolean = false,
        onError: (Exception) -> Unit = {},
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            val currentJob = coroutineContext[Job]
            val cancelAction: () -> Unit = {
                currentJob?.cancel()
            }

            updateState(modId, TaskState(TaskState.Phase.DOWNLOADING, 0f, true, cancelAction), isBepInEx)

            val mutex = locks.getOrPut(modId) { Mutex() }
            var stagingDir: File? = null

            try {
                mutex.withLock {

                    val bytes = downloadWithRetry(modId, url, isBepInEx, cancelAction) { downloadedBytes ->
                        if (hash == null || SettingsManager.config.value.ignoreHashMismatch) true else {
                            val expected = hash.removePrefix("sha256:").hexToByteArray()
                            val algorithm = MessageDigest.getInstance("SHA-256")
                            algorithm.digest(downloadedBytes).contentEquals(expected)
                        }
                    }

                    updateState(modId, TaskState(TaskState.Phase.EXTRACTING, null, true, cancelAction), isBepInEx)

                    stagingDir = withContext(Dispatchers.IO) {
                        Files.createTempDirectory("nomm-install-").toFile()
                    }

                    withContext(Dispatchers.IO) {
                        val staging = stagingDir ?: error("Install staging directory was not created")
                        extract(bytes, url, staging, isBepInEx)
                        if (isBepInEx) {
                            validateBepInExStaging(staging)
                            mergeStagedFiles(staging, dir)
                        } else {
                            promoteStagedDirectory(staging, dir)
                        }
                    }

                    onSuccess()
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (e is CancellationException) throw e
                    if (!isBepInEx && !dir.deleteRecursively()) {
                        println("NOMM: failed to clean up directory ${dir.absolutePath}")
                    }
                    val (title, detail) = buildErrorNotification(e, url, modId)
                    ErrorNotifications.push(title, detail)
                    updateState(modId, TaskState(TaskState.Phase.EXTRACTING, null, e.localizedMessage), isBepInEx)
                } finally {
                    clearStatus(modId, isBepInEx)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("[NOMM] Install failed for $modId: ${e.message}")
                runCatching { onError(e) }
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    stagingDir?.deleteRecursively()
                }
                locks.remove(modId, mutex)
                clearStatus(modId, isBepInEx)
            }
        }
    }

    suspend fun downloadWithRetry(
        modId: String, url: String, isBepInEx: Boolean,
        cancelAction: () -> Unit, attempts: Int = 3,
        checkHash: (ByteArray) -> Boolean,
    ): ByteArray {
        repeat(attempts) { i ->
            try {
                val response = NetworkClient.client.get(url) {
                    onDownload { sent, total ->
                        val p = if ((total ?: 0L) > 0) sent.toFloat() / total!! else null
                        updateState(modId, TaskState(TaskState.Phase.DOWNLOADING, p, true, cancelAction), isBepInEx)
                    }
                }
                val status = response.status
                if (!status.isSuccess()) {
                    throw DownloadHttpException(status.value, status.description, url)
                }
                val bytes = response.readRawBytes()
                if (bytes.isEmpty()) {
                    throw EmptyResponseException(url)
                }
                return bytes
            } catch (e: CancellationException) {
                throw e
                val bytes = response.readRawBytes()

                val isValid = withContext(Dispatchers.Default) { checkHash(bytes) }
                if (!isValid) throw Exception("Hash mismatch for $modId")

                return bytes
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (i < attempts - 1) delay((1000L * (i + 1)).milliseconds)
                else throw e
            }
        }
        throw lastErr ?: Exception("Failed to download: $url")
        throw IllegalStateException("unreachable")
    }

    suspend fun extract(bytes: ByteArray, url: String, target: File, noOverwrite: Boolean) {
        withContext(Dispatchers.IO) {
            ByteArrayStream(bytes, false).use { inStream ->
                val archive = try {
                    SevenZip.openInArchive(null, inStream)
                } catch (_: SevenZipException) {
                    null
                }

                if (archive != null) {
                    archive.use { arc ->
                        val items = arc.simpleInterface.archiveItems
                        items.forEach { item ->
                            ensureActive()

                            val file = resolveArchiveEntry(target, item.path)
                            if (item.isFolder) {
                                file.mkdirs()
                            } else {
                                if (!noOverwrite || !file.exists()) {
                                    file.parentFile?.mkdirs()
                                    FileOutputStream(file).use { out ->
                                        item.extractSlow { data ->
                                            if (!isActive) return@extractSlow -1

                                            out.write(data)
                                            data.size
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    ensureActive()
                    val file = resolveArchiveEntry(target, url.substringAfterLast("/"))
                    file.writeBytes(bytes)
                }
            }
        }
    }

    private fun validateBepInExStaging(stagingDir: File) {
        if (!File(stagingDir, "BepInEx").isDirectory) {
            throw IllegalStateException("The BepInEx archive did not contain a BepInEx directory")
        }
    }

    private fun promoteStagedDirectory(stagingDir: File, targetDir: File) {
        if (targetDir.exists()) {
            throw IllegalStateException("Install target already exists: ${targetDir.absolutePath}")
        }
        targetDir.parentFile?.mkdirs()

        try {
            Files.move(stagingDir.toPath(), targetDir.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            try {
                stagingDir.copyRecursively(targetDir, overwrite = false)
                stagingDir.deleteRecursively()
            } catch (copyError: Exception) {
                throw copyError
            }
        }
    }

    private suspend fun mergeStagedFiles(stagingDir: File, targetDir: File) {
        if (!targetDir.isDirectory) {
            throw IllegalStateException("BepInEx target is not a directory: ${targetDir.absolutePath}")
        }

        val createdFiles = mutableListOf<Path>()
        val createdDirectories = mutableListOf<Path>()

        try {
            stagingDir.walkTopDown().forEach { source ->
                currentCoroutineContext().ensureActive()
                if (source == stagingDir) return@forEach
                if (Files.isSymbolicLink(source.toPath())) {
                    throw SecurityException("BepInEx archive contains a symbolic link: ${source.name}")
                }

                val relativePath = stagingDir.toPath().relativize(source.toPath()).toString()
                val destination = resolveArchiveEntry(targetDir, relativePath).toPath()

                if (source.isDirectory) {
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        if (Files.isSymbolicLink(destination) || !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
                            throw IllegalStateException("Cannot create BepInEx directory: $destination")
                        }
                    } else {
                        createDirectories(destination, createdDirectories)
                    }
                } else {
                    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                        if (Files.isSymbolicLink(destination) || Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
                            throw IllegalStateException("Cannot install BepInEx file: $destination")
                        }
                        return@forEach
                    }

                    destination.parent?.let { createDirectories(it, createdDirectories) }
                    Files.copy(source.toPath(), destination, LinkOption.NOFOLLOW_LINKS)
                    createdFiles.add(destination)
                }
            }
        } catch (e: Exception) {
            createdFiles.asReversed().forEach { Files.deleteIfExists(it) }
            createdDirectories
                .sortedByDescending { it.nameCount }
                .forEach { Files.deleteIfExists(it) }
            throw e
        }
    }

    private fun createDirectories(path: Path, createdDirectories: MutableList<Path>) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw IllegalStateException("Cannot create directory: $path")
            }
            return
        }

        val missingDirectories = mutableListOf<Path>()
        var current = path
        while (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            missingDirectories.add(current)
            current = current.parent ?: break
        }

        Files.createDirectories(path)
        createdDirectories.addAll(missingDirectories)
    }

    private fun buildErrorNotification(e: Exception, url: String, modId: String): Pair<String, String> {
        val host = runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
        return when {
            e is EmptyResponseException ->
                "Download failed: Empty response" to
                "Mod: $modId\nURL: $url\n\nThe server returned an empty file. The release asset may have been uploaded incorrectly."
            e is DownloadHttpException && e.code == 403 ->
                "Download failed: Access denied (HTTP 403)" to
                "Mod: $modId\nURL: $url\n\nThe server rejected the request. Your IP may be rate-limited or the file requires authentication."
            e is DownloadHttpException && e.code == 404 ->
                "Download failed: File not found (HTTP 404)" to
                "Mod: $modId\nURL: $url\n\nThe download link is broken or the release was deleted. Try checking for an updated version."
            e is DownloadHttpException && e.code in 500..599 ->
                "Download failed: Server error (HTTP ${e.code})" to
                "Mod: $modId\nURL: $url\n\nThe download server is temporarily unavailable (${e.description}). Please try again later."
            e is DownloadHttpException ->
                "Download failed: HTTP ${e.code}" to
                "Mod: $modId\nURL: $url\n\n${e.description}"
            e is UnknownHostException ->
                "Download failed: DNS resolution error" to
                "Mod: $modId\nCould not resolve host: $host\n\nCheck your internet connection or DNS settings. If using a proxy, ensure it is correctly configured."
            e is ConnectException ->
                "Download failed: Connection refused" to
                "Mod: $modId\nHost: $host\n\nCould not connect to the server. It may be down, or the connection is blocked by a firewall."
            e is SocketTimeoutException || e is HttpRequestTimeoutException ->
                "Download failed: Connection timed out" to
                "Mod: $modId\nURL: $url\n\nThe server did not respond within the timeout period (60 s). Your connection may be too slow or the server is overloaded."
            e is SevenZipException ->
                "Installation failed: Archive extraction error" to
                "Mod: $modId\nURL: $url\n\nThe downloaded file could not be extracted (${e.localizedMessage}). The file may be corrupt — try reinstalling."
            e is IOException ->
                "Installation failed: Disk I/O error" to
                "Mod: $modId\n${e.javaClass.simpleName}: ${e.localizedMessage}\n\nCheck available disk space and write permissions for the game folder."
            else ->
                "Installation failed" to
                "Mod: $modId\nURL: $url\n${e.javaClass.simpleName}: ${e.localizedMessage}"
        }
    }
}

    private fun updateState(id: String, state: TaskState, isBep: Boolean) {
        if (isBep) bepinexStatus.value = state
        else installStatuses.update { it + (id to state) }
    }

    private fun clearStatus(id: String, isBep: Boolean) {
        if (isBep) bepinexStatus.value = null
        else installStatuses.update { it - id }
    }
}
data class TaskState(
    val phase: Phase,
    val progress: Float? = 0f,
    val isCancellable: Boolean = true,
    val onCancel: (() -> Unit)? = null,
) {
    enum class Phase { DOWNLOADING, EXTRACTING }

    fun cancel() {
        if (isCancellable) onCancel?.invoke()
    }
}

private class DownloadHttpException(val code: Int, val description: String, val url: String)
    : Exception("HTTP $code $description — $url")

private class EmptyResponseException(val url: String)
    : Exception("Empty response body from $url")
