package com.combat.nomm

import java.io.File
import java.nio.file.Files

private val windowsDrivePath = Regex("^[A-Za-z]:")

internal fun resolveArchiveEntry(root: File, entryName: String): File {
    val normalizedEntryName = entryName.replace('\\', '/')
    if (normalizedEntryName.isEmpty()) return root
    if (normalizedEntryName.startsWith('/') || windowsDrivePath.matches(normalizedEntryName)) {
        throw SecurityException("Archive entry is an absolute path: $entryName")
    }

    val rootPath = root.toPath().toAbsolutePath().normalize()
    val targetPath = rootPath.resolve(normalizedEntryName).normalize()
    if (!targetPath.startsWith(rootPath)) {
        throw SecurityException("Archive entry escapes its destination: $entryName")
    }

    var currentPath = targetPath
    while (currentPath != rootPath) {
        if (Files.isSymbolicLink(currentPath)) {
            throw SecurityException("Archive entry passes through a symbolic link: $entryName")
        }
        currentPath = currentPath.parent ?: break
    }

    return targetPath.toFile()
}
