package com.example.p2pml

import androidx.core.net.toUri
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
    val absoluteStartTime: Double,
    val absoluteEndTime: Double,
    val externalId: Long
)

@UnstableApi
internal class ExoPlayerPlaybackCalculator {
    private val parser = HlsPlaylistParser()
    private lateinit var exoPlayer: ExoPlayer
    private var parsedManifest: HlsMediaPlaylist? = null
    private var currentAbsoluteTime: Double? = null

    private var currentSegments = mutableMapOf<Long, PlaybackSegment>()
    private val mutex = Mutex()

    fun setExoPlayer(exoPlayer: ExoPlayer) {
        this.exoPlayer = exoPlayer
    }

    private fun removeObsoleteSegments(removeUntilId: Long) {
        val obsoleteIds = currentSegments.keys.filter { it < removeUntilId }

        obsoleteIds.forEach { id ->
            currentSegments.remove(id)
        }
    }

    private fun updateExistingSegmentRelativeTime(
        segmentId: Long,
        segment: HlsMediaPlaylist.Segment
    ) {
        val prevSegment = currentSegments[segmentId - 1]
        val currentSegment = currentSegments[segmentId]
            ?: throw IllegalStateException("Current segment is null")

        val segmentDurationInSeconds = segment.durationUs / 1_000_000.0
        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val relativeEndTime = relativeStartTime + segmentDurationInSeconds

        currentSegment.startTime = relativeStartTime
        currentSegment.endTime = relativeEndTime
    }

    private fun addSegment(segment: HlsMediaPlaylist.Segment, externalId: Long) {
        val prevSegment = currentSegments[externalId - 1]
        val segmentDurationInSeconds = segment.durationUs / 1_000_000.0

        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val absoluteStartTime = prevSegment?.absoluteEndTime ?: currentAbsoluteTime!!

        val relativeEndTime = relativeStartTime + segmentDurationInSeconds
        val absoluteEndTime = absoluteStartTime + segmentDurationInSeconds

        currentSegments[externalId] = PlaybackSegment(
            relativeStartTime,
            relativeEndTime,
            absoluteStartTime,
            absoluteEndTime,
            externalId
        )
    }

    suspend fun getAbsolutePlaybackPosition(
        manifestUrl: String,
        manifest: String
    ): Double = mutex.withLock {
        parsedManifest =
            parser.parse(manifestUrl.toUri(), manifest.byteInputStream()) as? HlsMediaPlaylist
                ?: throw IllegalStateException("Parsed manifest is null")

        val newMediaSequence = parsedManifest!!.mediaSequence
        currentAbsoluteTime = System.currentTimeMillis() / 1000.0

        removeObsoleteSegments(newMediaSequence)

        parsedManifest!!.segments.forEachIndexed { index, segment ->
            val segmentIndex = newMediaSequence + index

            if (!currentSegments.contains(segmentIndex))
                addSegment(segment, segmentIndex)
            else
                updateExistingSegmentRelativeTime(segmentIndex, segment)
        }

        return@withLock currentAbsoluteTime!!
    }

    suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo = mutex.withLock {
        val playbackPositionInSeconds = withContext(Dispatchers.Main) {
            exoPlayer.currentPosition / 1000.0
        }
        val playbackSpeed = withContext(Dispatchers.Main) { exoPlayer.playbackParameters.speed }

        if (parsedManifest == null || parsedManifest?.hasEndTag == true)
            return PlaybackInfo(playbackPositionInSeconds, playbackSpeed)

        val currentPlaybackInMs = if (playbackPositionInSeconds < 0) 0.0
            else playbackPositionInSeconds

        val currentSegment = currentSegments.values.find {
            currentPlaybackInMs >= it.startTime && currentPlaybackInMs <= it.endTime
        } ?: throw IllegalStateException("Current segment is null")

        val segmentPlayTime = currentPlaybackInMs - currentSegment.startTime
        val segmentAbsolutePlayTime = currentSegment.absoluteStartTime + segmentPlayTime

        return PlaybackInfo(segmentAbsolutePlayTime, playbackSpeed)
    }
}
