package com.combat.nomm

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


@Serializable(with = VersionSerializer::class)
class Version private constructor(private val parts: List<String>) : Comparable<Version> {
    constructor(vararg components: Int) : this(components.map { it.toString() })

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
            val thisPart = this.parts.getOrElse(i) { "0" }
            val otherPart = other.parts.getOrElse(i) { "0" }
            val comparison = comparePart(thisPart, otherPart)
            if (comparison != 0) {
                return comparison
            }
        }
        return 0
    }

    private fun comparePart(left: String, right: String): Int {
        val leftNumber = left.filter(Char::isDigit).toIntOrNull()
        val rightNumber = right.filter(Char::isDigit).toIntOrNull()

        return when {
            leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
            leftNumber != null -> 1
            rightNumber != null -> -1
            else -> left.compareTo(right, ignoreCase = true)
        }
    }

    companion object {
        fun parse(value: String): Version {
            val parts = value.split('.')
            require(parts.all { it.isNotEmpty() }) { "Invalid version: $value" }
            return Version(parts)
        }
    }
}

object VersionSerializer : KSerializer<Version> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Version", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Version) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Version {
        return Version.parse(decoder.decodeString())
    }
}
