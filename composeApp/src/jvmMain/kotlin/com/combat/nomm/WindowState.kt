package com.combat.nomm

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object WindowPositionSerializer : KSerializer<WindowPosition> {

    @Serializable
    private sealed class WindowPositionSurrogate {
        @Serializable
        @SerialName("platform_default")
        object PlatformDefault : WindowPositionSurrogate()

        @Serializable
        @SerialName("aligned")
        data class Aligned(val alignment: String) : WindowPositionSurrogate()

        @Serializable
        @SerialName("absolute")
        data class Absolute(val x: Float, val y: Float) : WindowPositionSurrogate()
    }

    private val alignmentMap = mapOf(
        Alignment.TopStart to "TopStart",
        Alignment.TopCenter to "TopCenter",
        Alignment.TopEnd to "TopEnd",
        Alignment.CenterStart to "CenterStart",
        Alignment.Center to "Center",
        Alignment.CenterEnd to "CenterEnd",
        Alignment.BottomStart to "BottomStart",
        Alignment.BottomCenter to "BottomCenter",
        Alignment.BottomEnd to "BottomEnd"
    )

    private val reverseAlignmentMap = alignmentMap.entries.associate { it.value to it.key }

    override val descriptor: SerialDescriptor = WindowPositionSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: WindowPosition) {
        val surrogate = when (value) {
            is WindowPosition.PlatformDefault -> WindowPositionSurrogate.PlatformDefault
            is WindowPosition.Aligned -> WindowPositionSurrogate.Aligned(
                alignmentMap[value.alignment] ?: "Center"
            )
            is WindowPosition.Absolute -> WindowPositionSurrogate.Absolute(
                value.x.value,
                value.y.value
            )
        }
        encoder.encodeSerializableValue(WindowPositionSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): WindowPosition {
        return when (val surrogate = decoder.decodeSerializableValue(WindowPositionSurrogate.serializer())) {
            is WindowPositionSurrogate.PlatformDefault -> WindowPosition.PlatformDefault
            is WindowPositionSurrogate.Aligned -> {
                val alignment = reverseAlignmentMap[surrogate.alignment] ?: Alignment.Center
                WindowPosition.Aligned(alignment)
            }
            is WindowPositionSurrogate.Absolute -> {
                WindowPosition.Absolute(Dp(surrogate.x), Dp(surrogate.y))
            }
        }
    }
}


object DpSizeSerializer : KSerializer<DpSize> {

    @Serializable
    private data class DpSizeSurrogate(val width: Float, val height: Float)

    override val descriptor: SerialDescriptor = DpSizeSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DpSize) {
        val surrogate = if (value.isSpecified) {
            DpSizeSurrogate(value.width.value, value.height.value)
        } else {
            DpSizeSurrogate(Float.NaN, Float.NaN)
        }
        encoder.encodeSerializableValue(DpSizeSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): DpSize {
        val surrogate = decoder.decodeSerializableValue(DpSizeSurrogate.serializer())

        return if (surrogate.width.isNaN() || surrogate.height.isNaN()) {
            DpSize.Unspecified
        } else {
            DpSize(Dp(surrogate.width), Dp(surrogate.height))
        }
    }
}

object WindowStateSerializer : KSerializer<WindowState> {
    @Serializable
    private data class WindowStateSurrogate(
        val placement: WindowPlacement,
        val isMinimized: Boolean,
        @Serializable(with = WindowPositionSerializer::class)
        val position: WindowPosition,
        @Serializable(with = DpSizeSerializer::class)
        val size: DpSize
    )

    override val descriptor: SerialDescriptor = WindowStateSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: WindowState) {
        val surrogate = WindowStateSurrogate(
            placement = value.placement,
            isMinimized = value.isMinimized,
            position = value.position,
            size = value.size
        )
        encoder.encodeSerializableValue(WindowStateSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): WindowState {
        val surrogate = decoder.decodeSerializableValue(WindowStateSurrogate.serializer())
        
        return object : WindowState {
            override var placement = surrogate.placement
            override var isMinimized = surrogate.isMinimized
            override var position = surrogate.position
            override var size = surrogate.size
        }
    }
}