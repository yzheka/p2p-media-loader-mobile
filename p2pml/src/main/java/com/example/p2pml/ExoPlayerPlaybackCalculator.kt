package com.example.p2pml

import androidx.core.net.toUri
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.example.p2pml.Constants.MICROSECONDS_IN_SECOND
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

private data class PlaybackSegment(
    val startTime: Double,
    val endTime: Double,
    val absoluteStartTime: Long,
    val absoluteEndTime: Long,
    val externalId: Int
)

@UnstableApi
class ExoPlayerPlaybackCalculator {
    private val parser = HlsPlaylistParser()
    private lateinit var exoPlayer: ExoPlayer
    private var parsedManifest: HlsMediaPlaylist? = null
    private var lastAbsolutePlaybackInMicroseconds: Long? = null
    private var segments = mutableListOf<PlaybackSegment>()
    private val mutex = Mutex()

    fun setExoPlayer(exoPlayer: ExoPlayer) {
        this.exoPlayer = exoPlayer
    }

    suspend fun getAbsolutePlaybackPosition(manifestUrl: String, manifest: String): Long = mutex.withLock {
        val executionTime = measureTimeMillis {
            lastAbsolutePlaybackInMicroseconds = System.currentTimeMillis() * 1000
            parsedManifest = parser.parse(manifestUrl.toUri(), manifest.byteInputStream()) as? HlsMediaPlaylist
                ?: run {
                    Log.e("ExoPlayerPlayback", "Parsed manifest is null for URL: $manifestUrl")
                    throw IllegalStateException("Parsed manifest is null")
                }
            segments.clear()

            var startTime = 0.0
            var absoluteStartTime = lastAbsolutePlaybackInMicroseconds!!
            var initialSegmentIndex = parsedManifest!!.mediaSequence

            parsedManifest!!.segments.forEach { segment ->
                val endTime = startTime + segment.durationUs / MICROSECONDS_IN_SECOND.toDouble()
                val absoluteEndTime = absoluteStartTime + segment.durationUs

                Log.d("ExoPlayerPlayback", "Segment: $startTime - $endTime - ${segment.durationUs.toDouble()}")
                segments.add(
                    PlaybackSegment(
                        startTime,
                        endTime,
                        absoluteStartTime,
                        absoluteEndTime,
                        initialSegmentIndex.toInt()
                    )
                )

                startTime = endTime
                absoluteStartTime = absoluteEndTime
                initialSegmentIndex++
            }
        }

        Log.d("ExoPlayerPlayback", "Execution time: $executionTime ms")

        return@withLock lastAbsolutePlaybackInMicroseconds!!
    }

    suspend fun getPlaybackPositionAndSpeed(): Pair<Long, Float> = mutex.withLock {
            val playbackPositionInMicroseconds = exoPlayer.currentPosition * 1000
            val playbackSpeed = exoPlayer.playbackParameters.speed

            //return@withLock Pair(playbackPositionInMicroseconds, playbackSpeed)
            if (parsedManifest?.hasEndTag == true || segments.isEmpty()) {
                return Pair(playbackPositionInMicroseconds, playbackSpeed)
            }

            val currentPlaybackInMicroseconds = if (playbackPositionInMicroseconds < 0) 0 else playbackPositionInMicroseconds
            val currentPlayPositionInSeconds = currentPlaybackInMicroseconds / MICROSECONDS_IN_SECOND.toDouble()

            val currentSegment = segments.find {
                currentPlayPositionInSeconds >= it.startTime && currentPlayPositionInSeconds < it.endTime
            } ?: throw IllegalStateException("Current segment not found")

            val segmentPlayTime = currentPlayPositionInSeconds - currentSegment.startTime
            val segmentPlayTimeInMicroSeconds = (segmentPlayTime * MICROSECONDS_IN_SECOND).toLong()
            val segmentAbsolutePlayTime = currentSegment.absoluteStartTime + segmentPlayTimeInMicroSeconds

            return Pair(segmentAbsolutePlayTime, playbackSpeed)
    }

}
