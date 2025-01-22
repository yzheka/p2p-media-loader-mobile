package com.novage.p2pml

sealed class CoreEventMap<out T> {
    data object OnSegmentLoaded : CoreEventMap<SegmentLoadDetails>()

    data object OnSegmentStarted : CoreEventMap<SegmentStartDetails>()

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

data class SegmentByteRange(
    val start: Int,
    val end: Int,
)

data class PeerDetails(
    val peerId: String,
    val streamType: String,
)

data class PeerErrorDetails(
    val peerId: String,
    val streamType: String,
    val error: String,
)

data class SegmentDetails(
    val runtimeId: String,
    val externalId: Int,
    val url: String,
    val byteRange: SegmentByteRange? = null,
    val startTime: Double,
    val endTime: Double,
)

data class SegmentLoadDetails(
    val segmentUrl: String,
    val bytesLength: Int,
    val downloadSource: String,
    val peerId: String?,
    val streamType: String,
)

data class SegmentStartDetails(
    val segment: SegmentDetails,
    val downloadSource: String,
    val peerId: String?,
)

data class SegmentErrorDetails(
    val error: String,
    val segment: SegmentDetails,
    val downloadSource: String,
    val peerId: String?,
    val streamType: String,
)

data class SegmentAbortDetails(
    val segment: SegmentDetails,
    val downloadSource: String,
    val peerId: String?,
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

data class TrackerErrorDetails(
    val streamType: String,
    val error: String,
)

data class TrackerWarningDetails(
    val streamType: String,
    val warning: String,
)
