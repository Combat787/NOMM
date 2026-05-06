package com.combat.nomm

import net.sf.sevenzipjbinding.SevenZip
import java.io.File
import java.lang.management.ManagementFactory

fun initializeSevenZipNative(): Boolean {
    return try {
        val pid = ManagementFactory.getRuntimeMXBean().name.split("@")[0]
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
            } catch (_: Exception) {
            }
        })

        SevenZip.initSevenZipFromPlatformJAR(currentInstanceDir)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}