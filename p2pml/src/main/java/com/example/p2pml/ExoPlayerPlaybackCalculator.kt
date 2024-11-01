package com.example.p2pml

import androidx.core.net.toUri
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class PlaybackSegment(
    var startTime: Double,
    var endTime: Double,
    val absoluteStartTime: Long,
    val absoluteEndTime: Long,
    val externalId: Long
)

@UnstableApi
class ExoPlayerPlaybackCalculator {
    private val parser = HlsPlaylistParser()
    private lateinit var exoPlayer: ExoPlayer
    private var parsedManifest: HlsMediaPlaylist? = null
    private var currentAbsoluteTime: Long? = null

    private var currentSegments = mutableMapOf<Long, PlaybackSegment>()
    private var currentSegmentIds = mutableSetOf<Long>()
    private val mutex = Mutex()

    fun setExoPlayer(exoPlayer: ExoPlayer) {
        this.exoPlayer = exoPlayer
    }

    private fun removeObsoleteSegments(removeUntilId: Long) {
        val obsoleteIds = currentSegmentIds.filter { it < removeUntilId }

        obsoleteIds.forEach { id ->
            currentSegments.remove(id)
            currentSegmentIds.remove(id)
        }
    }

    private fun updateExistingSegmentRelativeTime(
        segmentId: Long,
        segment: HlsMediaPlaylist.Segment
    ) {
        val prevSegment = currentSegments[segmentId - 1]
        val currentSegment = currentSegments[segmentId]
            ?: throw IllegalStateException("Current segment is null")

        val segmentDurationInMs = segment.durationUs / 1000.0
        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val relativeEndTime = relativeStartTime + segmentDurationInMs

        currentSegment.startTime = relativeStartTime
        currentSegment.endTime = relativeEndTime
    }

    private fun addSegment(segment: HlsMediaPlaylist.Segment, externalId: Long) {
        val prevSegment = currentSegments[externalId - 1]
        val segmentDurationInMs = segment.durationUs / 1000.0
        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val absoluteStartTime = prevSegment?.absoluteEndTime ?: currentAbsoluteTime!!

        val relativeEndTime = relativeStartTime + segmentDurationInMs
        val absoluteEndTime = absoluteStartTime + segmentDurationInMs

        currentSegments[externalId] = PlaybackSegment(
            relativeStartTime,
            relativeEndTime,
            absoluteStartTime,
            absoluteEndTime.toLong(),
            externalId
        )
        currentSegmentIds.add(externalId)
    }

    suspend fun getAbsolutePlaybackPosition(
        manifestUrl: String,
        manifest: String
    ): Long = mutex.withLock {
        parsedManifest =
            parser.parse(manifestUrl.toUri(), manifest.byteInputStream()) as? HlsMediaPlaylist
                ?: throw IllegalStateException("Parsed manifest is null")

        val newMediaSequence = parsedManifest!!.mediaSequence
        removeObsoleteSegments(newMediaSequence)
        currentAbsoluteTime = System.currentTimeMillis()

        parsedManifest!!.segments.forEachIndexed { index, segment ->
            val segmentIndex = newMediaSequence + index
            if (!currentSegmentIds.contains(segmentIndex))
                addSegment(segment, segmentIndex)
            else
                updateExistingSegmentRelativeTime(segmentIndex, segment)
            Log.d(
                "==ExoPlayerPlayback", "Segment: $segmentIndex, " +
                        "Start: ${currentSegments[segmentIndex]?.absoluteStartTime}," +
                        " End: ${currentSegments[segmentIndex]?.absoluteEndTime}"
            )
        }

        return@withLock currentAbsoluteTime!!
    }

    suspend fun getPlaybackPositionAndSpeed(): Pair<Double, Float> = mutex.withLock {
        val playbackPositionInMs = withContext(Dispatchers.Main) { exoPlayer.currentPosition }
        val playbackSpeed = withContext(Dispatchers.Main) { exoPlayer.playbackParameters.speed }

        if (parsedManifest == null || parsedManifest?.hasEndTag == true)
            return Pair(playbackPositionInMs / 1000.0, playbackSpeed)

        val currentPlaybackInMs = if (playbackPositionInMs < 0) 0 else playbackPositionInMs

        val currentSegment = currentSegments.values.find {
            currentPlaybackInMs >= it.startTime && currentPlaybackInMs <= it.endTime
        } ?: throw IllegalStateException("Current segment is null")

        val segmentPlayTime = currentPlaybackInMs - currentSegment.startTime
        val segmentAbsolutePlayTime = currentSegment.absoluteStartTime + segmentPlayTime

        return Pair(segmentAbsolutePlayTime, playbackSpeed)
    }

}
