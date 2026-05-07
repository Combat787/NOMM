package com.combat.nomm

import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.util.ByteArrayStream
import java.io.File
import java.io.FileOutputStream
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
        isBepInEx: Boolean = false, onSuccess: () -> Unit,
    ) {
        scope.launch {
            val currentJob = coroutineContext[Job]
            val cancelAction: () -> Unit = {
                currentJob?.cancel()
            }

            updateState(modId, TaskState(TaskState.Phase.DOWNLOADING, 0f, true, cancelAction), isBepInEx)

            val mutex = locks.getOrPut(modId) { Mutex() }

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

                    withContext(Dispatchers.IO) {
                        if (!dir.exists()) dir.mkdirs()
                        extract(bytes, url, dir, isBepInEx)
                    }

                    onSuccess()
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) {
                    dir.deleteRecursively()
                }
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(NonCancellable + Dispatchers.IO) {
                    dir.deleteRecursively()
                }
            } finally {
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
        throw Exception("All download attempts failed")
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
                        items.forEachIndexed { _, item ->
                            ensureActive()

                            val file = File(target, item.path)
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
                    val file = File(target, url.substringAfterLast("/"))
                    file.writeBytes(bytes)
                }
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