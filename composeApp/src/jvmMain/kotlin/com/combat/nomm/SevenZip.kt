package com.combat.nomm

import net.sf.sevenzipjbinding.SevenZip
import java.io.File

fun initializeSevenZipNative(): Boolean {
    return try {
        val pid = ProcessHandle.current().pid().toString()
        val appDataRoot = File(System.getProperty("user.home"), ".nomm/natives")
        val currentInstanceDir = File(appDataRoot, pid)

        if (appDataRoot.exists()) {
            appDataRoot.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name != pid) {
                    file.deleteRecursively()
                }
            }
        }

        if (!currentInstanceDir.exists()) {
            currentInstanceDir.mkdirs()
        }

        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                currentInstanceDir.deleteRecursively()
            } catch (e: Exception) {
                println("[NOMM] Shutdown hook failed to clean natives: ${e.message}")
            }
        })

        SevenZip.initSevenZipFromPlatformJAR(currentInstanceDir)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}