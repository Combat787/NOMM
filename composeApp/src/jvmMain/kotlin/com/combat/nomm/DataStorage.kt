package com.combat.nomm

import java.io.File

object DataStorage {
    const val APP_NAME = "Nuclear Option Mod Manager"
    val osName = System.getProperty("os.name").lowercase()
    val configPath = when {
        osName.contains("win") -> File(System.getenv("AppData"), APP_NAME)
        osName.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/$APP_NAME")
        else -> File(System.getProperty("user.home"), ".config/$APP_NAME")
    }
    val configFile = File(configPath, "config.json")
    
    

    init {
        if (!configPath.exists()) configPath.mkdirs()
    }
}