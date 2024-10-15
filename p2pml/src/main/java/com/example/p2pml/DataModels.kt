package com.example.p2pml

import kotlinx.serialization.Serializable

@Serializable
data class Stream(
    val runtimeId: String,
    val type: String,
    val index: Int,
)

@Serializable
data class Segment(
    val runtimeId: String,
    val externalId: Int,
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
data class UpdateStreamParams(
    val streamRuntimeId: String,
    val addSegments: List<Segment>,
    val removeSegmentsIds: List<String>,
)

@Serializable
data class SegmentRequest(
    val segmentUrl: String,
    val currentPlayPosition: Float,
    val currentPlaySpeed: Float
)