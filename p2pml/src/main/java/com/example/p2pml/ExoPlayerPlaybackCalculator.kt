package com.example.p2pml

import androidx.core.net.toUri
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

    private fun updateSegmentRelativeTime(segmentId: Long, segment: HlsMediaPlaylist.Segment) {
        if(!currentSegmentIds.contains(segmentId)) return

        val prevSegment = currentSegments[segmentId - 1]
        val currentSegment = currentSegments[segmentId]

        val segmentDurationInMs = segment.durationUs / 1000.0
        val relativeStartTime = prevSegment?.endTime ?: 0.0
        val relativeEndTime = relativeStartTime + segmentDurationInMs

        if(currentSegment == null) throw IllegalStateException("Current segment is null")

        currentSegment.startTime = relativeStartTime
        currentSegment.endTime = relativeEndTime
    }

    private fun addSegment(segment: HlsMediaPlaylist.Segment, externalId: Long) {
        if(currentSegmentIds.contains(externalId)) return

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
            parsedManifest = parser.parse(manifestUrl.toUri(), manifest.byteInputStream()) as? HlsMediaPlaylist
                ?: run {
                    Log.e("ExoPlayerPlayback", "Parsed manifest is null for URL: $manifestUrl")
                    throw IllegalStateException("Parsed manifest is null")
                }

            val newMediaSequence = parsedManifest!!.mediaSequence
            removeObsoleteSegments(newMediaSequence)
            currentAbsoluteTime = System.currentTimeMillis()

            parsedManifest!!.segments.forEachIndexed { index, segment ->
                val segmentIndex = newMediaSequence + index
                updateSegmentRelativeTime(segmentIndex, segment)
                addSegment(segment, segmentIndex)
                Log.d("==ExoPlayerPlayback", "Segment: $segmentIndex, " +
                        "Start: ${currentSegments[segmentIndex]?.absoluteStartTime}, End: ${currentSegments[segmentIndex]?.absoluteEndTime}")
            }

        return@withLock currentAbsoluteTime!!
    }

    fun getPlaybackPositionAndSpeed(): Pair<Long, Float>  {
        val playbackPositionInMs = exoPlayer.currentPosition
        val playbackSpeed = exoPlayer.playbackParameters.speed

        if (parsedManifest == null || parsedManifest?.hasEndTag == true) {
            Log.d("ExoPlayerPlayback", "End of stream reached $playbackPositionInMs")
            return Pair(playbackPositionInMs, playbackSpeed)
        }

        val currentPlaybackInMs = if (playbackPositionInMs < 0) 0 else playbackPositionInMs

        val currentSegment = currentSegments.values.find {
            currentPlaybackInMs >= it.startTime && currentPlaybackInMs <= it.endTime
        } ?: run {
            Log.e("ExoPlayerPlayback", "Current segment is null for playback position: $playbackPositionInMs")
            throw IllegalStateException("Current segment is null")
        }

        val segmentPlayTime = currentPlaybackInMs - currentSegment.startTime
        val segmentAbsolutePlayTime = currentSegment.absoluteStartTime + segmentPlayTime
        Log.d("==ExoPlayerPlayback", "CurrentPlayPositionMs: $playbackPositionInMs, Segment absolute play time: ${currentSegment.absoluteStartTime}")
        return Pair(currentSegment.absoluteStartTime, playbackSpeed)
    }

}
