package com.example.p2pml

import androidx.media3.exoplayer.ExoPlayer

class PlaybackInfo(private val exoPlayer: ExoPlayer) {
    val currentPosition: Long
        get() = exoPlayer.currentPosition

    val playbackSpeed: Float
        get() = exoPlayer.playbackParameters.speed
}