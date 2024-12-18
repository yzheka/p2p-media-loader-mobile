package com.novage.p2pml.utils

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import com.novage.p2pml.PlaybackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class PlaybackSegment(
    var startTime: Double,
    var endTime: Double,
    val absoluteStartTime: Double,
    val absoluteEndTime: Double,
    val externalId: Long,
)

@UnstableApi
internal class ExoPlayerPlaybackCalculator {
    private var exoPlayer: ExoPlayer? = null

    private var currentMediaPlaylist: HlsMediaPlaylist? = null
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
        segment: HlsMediaPlaylist.Segment,
    ) {
        val prevSegment = currentSegments[segmentId - 1]
        val currentSegment =
            currentSegments[segmentId]
                ?: throw IllegalStateException("Current segment is null")

        val segmentDurationInSeconds = segment.durationUs / 1_000_000.0
        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val relativeEndTime = relativeStartTime + segmentDurationInSeconds

        currentSegment.startTime = relativeStartTime
        currentSegment.endTime = relativeEndTime
    }

    private fun addSegment(
        segment: HlsMediaPlaylist.Segment,
        externalId: Long,
    ) {
        val prevSegment = currentSegments[externalId - 1]
        val segmentDurationInSeconds = segment.durationUs / 1_000_000.0

        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val absoluteStartTime = prevSegment?.absoluteEndTime ?: currentAbsoluteTime!!

        val relativeEndTime = relativeStartTime + segmentDurationInSeconds
        val absoluteEndTime = absoluteStartTime + segmentDurationInSeconds

        currentSegments[externalId] =
            PlaybackSegment(
                relativeStartTime,
                relativeEndTime,
                absoluteStartTime,
                absoluteEndTime,
                externalId,
            )
    }

    suspend fun getAbsolutePlaybackPosition(parsedMediaPlaylist: HlsMediaPlaylist): Double =
        mutex.withLock {
            currentMediaPlaylist = parsedMediaPlaylist

            val newMediaSequence = parsedMediaPlaylist.mediaSequence
            currentAbsoluteTime = System.currentTimeMillis() / 1000.0

            removeObsoleteSegments(newMediaSequence)

            parsedMediaPlaylist.segments.forEachIndexed { index, segment ->
                val segmentIndex = newMediaSequence + index

                if (!currentSegments.contains(segmentIndex)) {
                    addSegment(segment, segmentIndex)
                } else {
                    updateExistingSegmentRelativeTime(segmentIndex, segment)
                }
            }

            return@withLock currentAbsoluteTime!!
        }

    suspend fun getPlaybackPositionAndSpeed(): PlaybackInfo =
        mutex.withLock {
            val player = exoPlayer ?: throw IllegalStateException("ExoPlayer instance is null")

            val playbackPositionInSeconds =
                withContext(Dispatchers.Main) {
                    player.currentPosition / 1000.0
                }
            val playbackSpeed = withContext(Dispatchers.Main) { player.playbackParameters.speed }

            if (currentMediaPlaylist == null || currentMediaPlaylist?.hasEndTag == true) {
                return PlaybackInfo(playbackPositionInSeconds, playbackSpeed)
            }

            val currentPlayback =
                if (playbackPositionInSeconds < 0) {
                    0.0
                } else {
                    playbackPositionInSeconds
                }

            val currentSegment =
                currentSegments.values.find {
                    currentPlayback >= it.startTime && currentPlayback <= it.endTime
                } ?: throw IllegalStateException("Current segment is null")

            val segmentPlayTime = currentPlayback - currentSegment.startTime
            val segmentAbsolutePlayTime = currentSegment.absoluteStartTime + segmentPlayTime

            return PlaybackInfo(segmentAbsolutePlayTime, playbackSpeed)
        }

    suspend fun reset() =
        mutex.withLock {
            currentSegments.clear()
            currentMediaPlaylist = null
            currentAbsoluteTime = null
            exoPlayer = null
        }
}
