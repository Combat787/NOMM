package com.combat.nomm

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


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

object VersionSerializer : KSerializer<Version> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Version", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Version) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Version {
        val string = decoder.decodeString()
        val parts = string.split('.')
            .map { it.replace("\\D+".toRegex() ,"").toInt() }
            .toIntArray()

        return Version(*parts)
    }
}
