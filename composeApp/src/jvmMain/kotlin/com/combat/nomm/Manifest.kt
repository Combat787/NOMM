package com.combat.nomm

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.security.MessageDigest
import java.util.concurrent.ThreadLocalRandom

typealias Manifest = List<Extension>

@Serializable
data class Extension(
    val id: String,
    val displayName: String = id,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val urls: List<UrlReference> = emptyList(),
    val authors: List<String> = emptyList(),
    val artifacts: List<Artifact>,
    val downloadCount: Int? = null,
    @Transient val real: Boolean = true
)

@Serializable
data class Artifact(
    val fileName: String? = null,
    val version: Version,
    val category: String? = null,
    val type: String? = null,
    val gameVersion: String? = null,
    val downloadUrl: String,
    val hash: String? = null,
    val extends: PackageReference? = null,
    val dependencies: List<PackageReference> = emptyList(),
    val incompatibilities: List<PackageReference> = emptyList()
)

@Serializable
data class UrlReference(
    val name: String,
    val url: String,
)

@Serializable
data class PackageReference(
    val id: String,
    val version: Version? = null
)


fun computeModHashPrefix(id: String, version: Version): String {
    val input = "$id|$version"
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return buildString(6) {
        for (i in 0 until 3) {
            append(Integer.toHexString(digest[i].toInt() and 0xFF).padStart(2, '0'))
        }
    }
}

fun buildModHashLookup(manifest: Manifest): Map<String, PackageReference> {
    val lookup = HashMap<String, PackageReference>()
    manifest.forEach { ext ->
        ext.artifacts.forEach { artifact ->
            val hashPrefix = computeModHashPrefix(ext.id, artifact.version)
            lookup[hashPrefix] = PackageReference(ext.id, artifact.version)
        }
    }
    return lookup
}

fun fetchFakeManifest(): List<Extension> {
    val latinWords = arrayOf(
        "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit",
        "terra", "nova", "ignis", "aqua", "ventus", "lux", "umbra", "vita"
    )

    val rnd = ThreadLocalRandom.current()
    val modCount = rnd.nextInt(1000, 3000)

    val manifest = ArrayList<Extension>(modCount)
    val allIds = Array(modCount) { i -> "pkg_$i" }
    val upperLatin = Array(latinWords.size) { latinWords[it].uppercase() }

    for (i in 0 until modCount) {
        val pkgId = allIds[i]
        val author = upperLatin[rnd.nextInt(16)]
        val name1 = latinWords[rnd.nextInt(16)]
        val name2 = latinWords[rnd.nextInt(16)]
        val pkgName = "$name1 $name2"

        val versionCount = rnd.nextInt(10, 30)
        val artifacts = ArrayList<Artifact>(versionCount)

        for (v in 0 until versionCount) {
            val depCount = rnd.nextInt(10, 20)
            val deps = ArrayList<PackageReference>(depCount)
            repeat(depCount) {
                deps.add(PackageReference(allIds[rnd.nextInt(modCount)], Version(1, rnd.nextInt(10), 0)))
            }
            val incompatsCount = rnd.nextInt(10, 20)
            val incompats = ArrayList<PackageReference>(depCount)
            repeat(incompatsCount) {
                incompats.add(PackageReference(allIds[rnd.nextInt(modCount)], Version(1, rnd.nextInt(10), 0)))
            }
            val fastHash = java.lang.Long.toHexString(rnd.nextLong()) + java.lang.Long.toHexString(rnd.nextLong())

            artifacts.add(Artifact(
                fileName = "$pkgId-$v.zip",
                version = Version(1, v, 0),
                category = "Release",
                type = "Mod",
                gameVersion = "0.33",
                downloadUrl = "https://cdn.ex.com/$author/$pkgId-$v.zip",
                hash = fastHash,
                extends = null,
                dependencies = deps,
                incompatibilities = incompats
            ))
        }

        val tagCount = rnd.nextInt(1, 5)
        val tags = ArrayList<String>(tagCount)
        repeat(tagCount) {
            tags.add(latinWords[rnd.nextInt(16)])
        }

        manifest.add(Extension(
            id = pkgId,
            displayName = pkgName,
            description = List(rnd.nextInt(50,150)) { latinWords[rnd.nextInt(0, latinWords.size-1)] }.joinToString(" "),
            tags = tags,
            urls = listOf(UrlReference("Info", "https://ex.com/$author/$pkgId")),
            authors = listOf(author),
            artifacts = artifacts,
            downloadCount = rnd.nextInt(1, 100000000),
            real = false,
        ))
    }
    return manifest
}