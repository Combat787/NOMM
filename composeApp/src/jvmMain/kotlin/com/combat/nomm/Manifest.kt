package com.combat.nomm

import kotlinx.serialization.Serializable
import java.util.*
import kotlin.random.Random

typealias Manifest = List<Extension>

@Serializable
data class Extension(
    val id: String,
    val displayName: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val infoUrl: String,
    val authors: List<String>,
    val artifacts: List<Artifact>
)

@Serializable
data class Artifact(
    val fileName: String,
    val version: Version,
    val category: String,
    val type: String,
    val gameVersion: String? = null,
    val downloadUrl: String,
    val hash: String? = null,
    val extends: PackageReference? = null,
    val dependencies: List<PackageReference> = emptyList(),
    val incompatibilities: List<PackageReference> = emptyList()
)

@Serializable
data class PackageReference(
    val id: String,
    val version: Version?
) {
}

@Serializable(with = VersionSerializer::class)
class Version(vararg components: Int) : Comparable<Version> {

    private val parts: List<Int> = components.toList()

    override fun toString(): String = parts.joinToString(".")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Version) return false
        return this.parts == other.parts
    }

    override fun hashCode(): Int = parts.hashCode()

    override fun compareTo(other: Version): Int {
        val maxLength = maxOf(this.parts.size, other.parts.size)
        for (i in 0 until maxLength) {
            val thisPart = this.parts.getOrElse(i) { 0 }
            val otherPart = other.parts.getOrElse(i) { 0 }
            if (thisPart != otherPart) {
                return thisPart.compareTo(otherPart)
            }
        }
        return 0
    }
}

fun fetchFakeManifest(): Manifest {
    val latinWords = listOf(
        "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit",
        "terra", "nova", "ignis", "aqua", "ventus", "lux", "umbra", "vita"
    )

    fun generateLoremParagraph(sentenceCount: Int): String {
        return (1..sentenceCount).joinToString(" ") {
            val words = (5..15).map { latinWords.random() }.toMutableList()
            words[0] = words[0].replaceFirstChar { it.uppercase() }
            words.joinToString(" ") + "."
        }
    }

    fun generateVersionHistory(versionCount: Int): List<Version> {
        return List(versionCount) {
            Version(1, it, 0)
        }
    }

    val mods = List(Random.nextInt(30, 200)) { index ->
        val versions = generateVersionHistory(Random.nextInt(3, 8))
        val pkgName = "${latinWords.random()} ${latinWords.random()}"
        val pkgId = "$pkgName$index"
        Triple(versions, pkgName, pkgId)
    }

    return mods.map { (versions, pkgName, pkgId) ->
        val authors = List(Random.nextInt(1, 3)) {
            "${latinWords.random().uppercase()} ${
                latinWords.random().uppercase()
            }"
        }
        val repoAuthor = authors.random()
        Extension(
            id = pkgId,
            displayName = pkgName.replaceFirstChar { it.uppercase() },
            description = generateLoremParagraph(Random.nextInt(2, 5)),
            tags = listOf("QOL", "RANDOM", "UNITS").shuffled().take(Random.nextInt(1, 3)),
            infoUrl = "https://example.com/$repoAuthor/$pkgName/README.md",
            authors = authors,
            artifacts = versions.map { ver ->
                Artifact(
                    fileName = "$pkgName-$ver.zip",
                    version = ver,
                    category = listOf("Release", "Beta", "Alpha").random(),
                    type = "Mod",
                    gameVersion = "0.${Random.nextInt(30, 32)}",
                    downloadUrl = "https://example.com/$repoAuthor/$pkgName-$ver.zip",
                    hash = UUID.randomUUID().toString().replace("-", ""),
                    extends = if (Random.nextBoolean()) run {
                        val depend = mods.random()
                        PackageReference(
                            id = depend.third,
                            version = depend.first.random()
                        )
                    } else null,
                    dependencies = List(Random.nextInt(0, 3)) {
                        val depend = mods.random()
                        PackageReference(
                            id = depend.third,
                            version = depend.first.random()
                        )
                    },
                    incompatibilities = List(Random.nextInt(0, 3)) {
                        val depend = mods.random()
                        PackageReference(
                            id = depend.third,
                            version = depend.first.random()
                        )
                    }
                )
            }
        )
    }
}