package com.example.p2pml

import kotlinx.serialization.Serializable

@Serializable
internal data class Stream(
    val runtimeId: String,
    val type: String,
    val index: Int,
)

@Serializable
internal data class Segment(
    val runtimeId: String,
    val externalId: Long,
    val url: String,
    val byteRange: ByteRange?,
    val startTime: Double,
    val endTime: Double,
)

@Serializable
data class ByteRange(
    val start: Long,
    val end: Long,
)

@Serializable
internal data class UpdateStreamParams(
    val streamRuntimeId: String,
    val addSegments: List<Segment>,
    val removeSegmentsIds: List<String>,
    val isLive: Boolean,
)

@Serializable
internal data class SegmentRequest(
    val requestId: Int,
    val segmentUrl: String,
)

@Serializable
internal data class PlaybackInfo(
    val currentPlayPosition: Double,
    val currentPlaySpeed: Float,
)

@Serializable
internal data class StreamConfig(
    val isP2PDisabled: Boolean? = null,
)

@Serializable
internal data class DynamicP2PCoreConfig(
    val isP2PDisabled: Boolean? = null,
    val mainStream: StreamConfig? = null,
    val secondaryStream: StreamConfig? = null,
)

internal enum class AppState {
    INITIALIZED,
    STARTED,
    STOPPED,
}
