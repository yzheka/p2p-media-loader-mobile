package com.novage.p2pml

import kotlinx.serialization.Serializable

sealed class CoreEventMap<out T> {
    data object OnSegmentLoaded : CoreEventMap<SegmentLoadDetails>()

    data object OnSegmentStart : CoreEventMap<SegmentStartDetails>()

    data object OnSegmentError : CoreEventMap<SegmentErrorDetails>()

    data object OnSegmentAbort : CoreEventMap<SegmentAbortDetails>()

    data object OnPeerConnect : CoreEventMap<PeerDetails>()

    data object OnPeerClose : CoreEventMap<PeerDetails>()

    data object OnPeerError : CoreEventMap<PeerErrorDetails>()

    data object OnChunkDownloaded : CoreEventMap<ChunkDownloadedDetails>()

    data object OnChunkUploaded : CoreEventMap<ChunkUploadedDetails>()

    data object OnTrackerError : CoreEventMap<TrackerErrorDetails>()

    data object OnTrackerWarning : CoreEventMap<TrackerWarningDetails>()
}

@Serializable
data class SegmentByteRange(
    val start: Int,
    val end: Int,
)

@Serializable
data class PeerDetails(
    val peerId: String,
    val streamType: String,
)

@Serializable
data class PeerErrorDetails(
    val peerId: String,
    val streamType: String,
    val error: String,
)

@Serializable
data class SegmentDetails(
    val runtimeId: String,
    val externalId: Int,
    val url: String,
    val byteRange: SegmentByteRange? = null,
    val startTime: Double,
    val endTime: Double,
)

@Serializable
data class SegmentLoadDetails(
    val segmentUrl: String,
    val bytesLength: Int,
    val downloadSource: String,
    val peerId: String? = null,
    val streamType: String,
)

@Serializable
data class SegmentStartDetails(
    val segment: SegmentDetails,
    val downloadSource: String,
    val peerId: String? = null,
)

data class SegmentErrorDetails(
    val error: String,
    val segment: SegmentDetails,
    val downloadSource: String,
    val peerId: String?,
    val streamType: String,
)

@Serializable
data class SegmentAbortDetails(
    val segment: SegmentDetails,
    val downloadSource: String,
    val peerId: String? = null,
    val streamType: String,
)

data class ChunkDownloadedDetails(
    val bytesLength: Int,
    val downloadSource: String,
    val peerId: String?,
)

data class ChunkUploadedDetails(
    val bytesLength: Int,
    val peerId: String,
)

@Serializable
data class TrackerErrorDetails(
    val streamType: String,
    val error: String,
)

@Serializable
data class TrackerWarningDetails(
    val streamType: String,
    val warning: String,
)
