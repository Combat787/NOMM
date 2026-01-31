package com.combat.nomm

import com.github.junrar.Archive
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

object Installer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val locks = ConcurrentHashMap<String, Mutex>()

    val bepinexStatus: StateFlow<TaskState?>
        field = MutableStateFlow<TaskState?>(null)

    val installStatuses: StateFlow<Map<String, TaskState>>
        field = MutableStateFlow<Map<String, TaskState>>(emptyMap())

    fun installMod(modId: String, url: String, dir: File, isBepInEx: Boolean = false, onSuccess: () -> Unit) {

        updateState(modId, TaskState(TaskState.Phase.DOWNLOADING, 0f, null, false), isBepInEx)

        scope.launch {
            val mutex = locks.getOrPut(modId) { Mutex() }
            mutex.withLock {
                val job = coroutineContext[Job]
                try {
                    val bytes = downloadWithRetry(job, url, modId, isBepInEx)
                    updateState(modId, TaskState(TaskState.Phase.EXTRACTING, null, null, false), isBepInEx)

                    withContext(Dispatchers.IO) {
                        if (!dir.exists()) dir.mkdirs()
                        extract(bytes, url, dir, isBepInEx)
                    }
                    onSuccess()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    updateState(modId, TaskState(TaskState.Phase.EXTRACTING, null, e.localizedMessage), isBepInEx)
                } finally {
                    clearStatus(modId, isBepInEx)
                }
            }
        }
    }

    private suspend fun downloadWithRetry(
        parentJob: Job?,
        url: String,
        modId: String,
        isBepInEx: Boolean,
        attempts: Int = 3,
    ): ByteArray {
        var lastErr: Exception? = null
        repeat(attempts) { i ->
            try {
                return NetworkClient.client.get(url) {
                    onDownload { sent, total ->
                        val p = if ((total ?: 0L) > 0) sent.toFloat() / total!! else null
                        updateState(
                            modId,
                            TaskState(TaskState.Phase.DOWNLOADING, p, null, true) { parentJob?.cancel() },
                            isBepInEx
                        )
                    }
                }.readRawBytes()
            } catch (e: Exception) {
                lastErr = e
                if (i < attempts - 1) delay(1000L * (i + 1))
            }
        }
        throw lastErr ?: Exception("Failed to download")
    }

    private fun extract(bytes: ByteArray, url: String, target: File, noOverwrite: Boolean) {
        val ext = url.substringAfterLast(".", "").lowercase()
        runCatching {
            when (ext) {
                "zip" -> extractZip(ByteArrayInputStream(bytes), target, noOverwrite)
                "7z" -> extract7z(bytes, target, noOverwrite)
                "rar" -> extractRar(ByteArrayInputStream(bytes), target, noOverwrite)
                else -> {
                    val file = File(target, url.substringAfterLast("/"))
                    if (!noOverwrite || !file.exists()) file.writeBytes(bytes)
                }
            }
        }.onFailure {
            if (!noOverwrite) target.deleteRecursively()
            throw it
        }
    }

    private fun extractZip(input: InputStream, target: File, noOverwrite: Boolean) {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val file = File(target, entry.name)
                if (entry.isDirectory) file.mkdirs() else {
                    if (!noOverwrite || !file.exists()) {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
            }
        }
    }

    private fun extract7z(bytes: ByteArray, target: File, noOverwrite: Boolean) {
        SevenZFile.builder().setSeekableByteChannel(SeekableInMemoryByteChannel(bytes)).get().use { sz ->
            while (true) {
                val entry = sz.nextEntry ?: break
                val file = File(target, entry.name)
                if (entry.isDirectory) file.mkdirs() else {
                    if (!noOverwrite || !file.exists()) {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { out ->
                            val buffer = ByteArray(8192)
                            var len: Int
                            while (sz.read(buffer).also { len = it } != -1) out.write(buffer, 0, len)
                        }
                    }
                }
            }
        }
    }

    private fun extractRar(input: InputStream, target: File, noOverwrite: Boolean) {
        Archive(input).use { arc ->
            while (true) {
                val entry = arc.nextFileHeader() ?: break
                val file = File(target, entry.fileName)
                if (entry.isDirectory) file.mkdirs() else {
                    if (!noOverwrite || !file.exists()) {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { arc.extractFile(entry, it) }
                    }
                }
            }
        }
    }


    private fun updateState(id: String, state: TaskState, isBep: Boolean) {
        if (isBep) {
            bepinexStatus.value = state
        } else {
            installStatuses.update { it + (id to state) }
        }
    }

    private fun clearStatus(id: String, isBep: Boolean) {
        if (isBep) {
            bepinexStatus.value = null
        } else {
            installStatuses.update { it - id }
        }

    }
}

data class TaskState(
    val phase: Phase,
    val progress: Float? = 0f,
    val error: String? = null,
    val isCancellable: Boolean = true,
    private val onCancel: (() -> Unit)? = null,
) {
    enum class Phase { DOWNLOADING, EXTRACTING }

    fun cancel() {
        if (isCancellable) onCancel?.invoke()
    }
}