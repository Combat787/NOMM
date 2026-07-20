package com.combat.nomm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateEvent
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import kotlin.coroutines.resume

@Composable
fun LaunchOptionDialog(onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    val command = "WINEDLLOVERRIDES=\"winhttp.dll=n,b\" %command%"

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Required Launch Options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("To make BepInEx work on Linux, add this to the Steam Launch Options:")
                Surface(
                    onClick = { onCopy(command) },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = command,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    )
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
fun Dialogs() {
    val clipboard = LocalClipboard.current

    val launchOptionDialog by RepoMods.launchOptionDialog.collectAsState()


    if (launchOptionDialog) {
        LaunchOptionDialog(
            onDismiss = { RepoMods.launchOptionDialog.value = false },
            onCopy = { text ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(StringSelection(text)))
                }
            }
        )
    }

    var postUpdateEvent by remember { mutableStateOf<UpdateEvent?>(null) }

    LaunchedEffect(Unit) {

        postUpdateEvent = updater.consumeUpdateEvent()
        postUpdateEvent?.let { event ->
            SettingsManager.criticalInformation.add(
                Triple(
                    "Updated from ${event.previousVersion} to ${event.newVersion}",
                    BuildKonfig.CHANGELOG,
                    null
                    )
            )
        }
        checkForUpdate()
        //postUpdateEvent = UpdateEvent(
        //    "0.0",
        //    "1.0",
        //    UpdateLevel.MAJOR
        //)
    }


    SettingsManager.criticalInformation.lastOrNull()?.let { (criticalInformation, additional) ->
        CriticalDialog(criticalInformation, additional) {
            SettingsManager.criticalInformation.removeLast().third?.resume(Unit)
        }

    }



    SettingsManager.availableUpdateInfo?.let { info ->
        UpdateDialog(
            info = info,
            updater = updater,
            onDismiss = { SettingsManager.availableUpdateInfo = null }
        )
    }

}


suspend fun checkForUpdate() {
    val result = updater.checkForUpdates()
    if (result is UpdateResult.Available) {
        SettingsManager.availableUpdateInfo = result.info
    }
}

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    updater: NucleusUpdater,
    onDismiss: () -> Unit,
) {
    var progress by remember { mutableDoubleStateOf(0.0) }
    var isDownloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Update ${info.version} Available",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { (progress / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(32.dp),
                        strokeCap = StrokeCap.Round
                    )
                    Text(
                        text = "${progress.toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isDownloading,
                onClick = {
                    isDownloading = true
                    scope.launch {
                        try {
                            updater.downloadUpdate(info)
                                .flowOn(Dispatchers.IO)
                                .collect { downloadProgress ->
                                    progress = downloadProgress.percent

                                    downloadProgress.file?.let { installerFile ->
                                        updater.installAndRestart(installerFile)
                                    }
                                }
                        } catch (_: Exception) {
                            isDownloading = false
                        }
                    }
                }
            ) {
                Text(if (isDownloading) "Downloading..." else "Download & Install")
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = {
                    onDismiss()
                }) {
                    Text("Later")
                }
            }
        }
    )
}

@Composable
fun CriticalDialog(criticalInformation: String, additional: String, onDismiss: () -> Unit) {

    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = {
            Text(
                text = criticalInformation,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = additional,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
