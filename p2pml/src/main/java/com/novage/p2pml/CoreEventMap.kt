package com.novage.p2pml

import kotlinx.serialization.Serializable

/**
 * A sealed class representing different P2P core events, each with its own payload type.
 */
sealed class CoreEventMap<T> {
    /**
     * Companion object holding all event singletons so Java can do:
     *     CoreEventMaps.OnSegmentLoaded
     * from Java code.
     */
    companion object {
        @JvmField
        val OnSegmentLoaded = OnSegmentLoadedEvent

        @JvmField
        val OnSegmentStart = OnSegmentStartEvent

        @JvmField
        val OnSegmentError = OnSegmentErrorEvent

        @JvmField
        val OnSegmentAbort = OnSegmentAbortEvent

        @JvmField
        val OnPeerConnect = OnPeerConnectEvent

        @JvmField
        val OnPeerClose = OnPeerCloseEvent

        @JvmField
        val OnPeerError = OnPeerErrorEvent

        @JvmField
        val OnChunkDownloaded = OnChunkDownloadedEvent

        @JvmField
        val OnChunkUploaded = OnChunkUploadedEvent

        @JvmField
        val OnTrackerError = OnTrackerErrorEvent

        @JvmField
        val OnTrackerWarning = OnTrackerWarningEvent
    }

    object OnSegmentLoadedEvent : CoreEventMap<SegmentLoadDetails>()

    object OnSegmentStartEvent : CoreEventMap<SegmentStartDetails>()

    object OnSegmentErrorEvent : CoreEventMap<SegmentErrorDetails>()

    object OnSegmentAbortEvent : CoreEventMap<SegmentAbortDetails>()

    object OnPeerConnectEvent : CoreEventMap<PeerDetails>()

    object OnPeerCloseEvent : CoreEventMap<PeerDetails>()

    object OnPeerErrorEvent : CoreEventMap<PeerErrorDetails>()

    object OnChunkDownloadedEvent : CoreEventMap<ChunkDownloadedDetails>()

    object OnChunkUploadedEvent : CoreEventMap<ChunkUploadedDetails>()

    object OnTrackerErrorEvent : CoreEventMap<TrackerErrorDetails>()

    object OnTrackerWarningEvent : CoreEventMap<TrackerWarningDetails>()
}

/**
 * Represents a range of bytes, used for specifying a segment of data to download.
 *
 * @property start The starting byte index of the range.
 * @property end The ending byte index of the range.
 */
@Serializable
data class SegmentByteRange(
    val start: Int,
    val end: Int,
)

/**
 * Represents the details of a peer in a peer-to-peer network.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property streamType The type of stream that the peer is connected to.
 */
@Serializable
data class PeerDetails(
    val peerId: String,
    val streamType: String,
)

/**
 * Represents the details of a peer error event.
 *
 * @property peerId The unique identifier for a peer in the network.
 * @property streamType The type of stream that the peer is connected to.
 * @property error The error that occurred during the peer-to-peer connection.
 */
@Serializable
data class PeerErrorDetails(
    val peerId: String,
    val streamType: String,
    val error: String,
)

/**
 * Describes a media segment with its unique identifiers, location, and timing information.
 *
 * @property runtimeId The unique identifier of the segment.
 * @property externalId The external identifier of the segment.
 * @property url The URL of the segment.
 * @property byteRange The byte range of the segment.
 * @property startTime The start time of the segment.
 * @property endTime The end time of the segment.
 */
@Serializable
data class SegmentDetails(
    val runtimeId: String,
    val externalId: Int,
    val url: String,
    val byteRange: SegmentByteRange? = null,
    val startTime: Double,
    val endTime: Double,
)

/**
 * Represents the details about a loaded segment.
 *
 * @property segmentUrl The URL of the segment.
 * @property bytesLength The length of the segment in bytes.
 * @property downloadSource The source from which the segment was downloaded.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentLoadDetails(
    val segmentUrl: String,
    val bytesLength: Int,
    val downloadSource: String,
    val peerId: String? = null,
    val streamType: String,
)

/**
 * Represents details about a segment event.
 *
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download.
 * @property peerId The ID of the peer from which the segment was downloaded.
 *
 */
@Serializable
data class SegmentStartDetails(
    val segment: SegmentDetails,
    val downloadSource: String,
    val peerId: String? = null,
)

/**
 * Represents details about a segment error event.
 *
 *  @property error The error message.
 *  @property segment The segment that the event is about.
 *  @property downloadSource The origin of the segment download.
 *  @property peerId The ID of the peer from which the segment was downloaded.
 *  @property streamType The type of stream.
 *
 */
@Serializable
data class SegmentErrorDetails(
    val error: String,
    val segment: SegmentDetails,
    val downloadSource: String,
    val peerId: String?,
    val streamType: String,
)

/**
 * Represents details about a segment abort event.
 *
 * @property segment The segment that the event is about.
 * @property downloadSource The origin of the segment download.
 * @property peerId The ID of the peer from which the segment was downloaded.
 * @property streamType The type of stream.
 */
@Serializable
data class SegmentAbortDetails(
    val segment: SegmentDetails,
    val downloadSource: String,
    val peerId: String? = null,
    val streamType: String,
)

/**
 * Represents the details of a chunk downloaded event.
 *
 * @property bytesLength The length of the chunk in bytes.
 * @property downloadSource The source from which the chunk was downloaded.
 * @property peerId The ID of the peer from which the chunk was downloaded (if downloaded from a peer).
 */
data class ChunkDownloadedDetails(
    val bytesLength: Int,
    val downloadSource: String,
    val peerId: String?,
)

/**
 * Represents the details of a chunk uploaded event.
 *
 * @property bytesLength The length of the chunk in bytes.
 * @property peerId The ID of the peer to which the chunk was uploaded.
 */
data class ChunkUploadedDetails(
    val bytesLength: Int,
    val peerId: String,
)

/**
 * Represents the details of a tracker error event.
 *
 * @property streamType The type of stream that the tracker is for.
 * @property error The error that occurred during the tracker request.
 */
@Serializable
data class TrackerErrorDetails(
    val streamType: String,
    val error: String,
)

/**
 * Represents the details of a tracker warning event.
 *
 * @property streamType The type of stream that the tracker is for.
 * @property warning The warning that occurred during the tracker request.
 */
@Serializable
data class TrackerWarningDetails(
    val streamType: String,
    val warning: String,
)
