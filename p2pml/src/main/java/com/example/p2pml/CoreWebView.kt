package com.example.p2pml

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.media3.exoplayer.ExoPlayer
import androidx.webkit.WebViewClientCompat
import kotlinx.coroutines.CompletableDeferred


class CoreWebView(
    context: Context,
    private val fileToLoad: String = "file:///android_asset/core.html"
) {
    private var playbackInfo: PlaybackInfo? = null

    // TODO: Make vebView private
    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = WebViewClientCompat()
        loadUrl(fileToLoad)
        visibility = View.GONE
    }
    private val webMessageProtocol = WebMessageProtocol(webView)

    fun destroy() {
        webView.apply {
            parent?.let { (it as ViewGroup).removeView(this) }
            destroy()
        }
    }

    fun setUpPlaybackInfo(exoPlayer: ExoPlayer) {
        this.playbackInfo = PlaybackInfo(exoPlayer)
    }

    suspend fun requestSegmentBytes(segmentUrl: String): CompletableDeferred<ByteArray> {
       return webMessageProtocol.requestSegmentBytes(segmentUrl)
    }

    fun updatePlaybackInfo() {
        playbackInfo?.let {
            val currentPosition = it.currentPosition
            val playbackSpeed = it.playbackSpeed
            webView.evaluateJavascript("javascript:window.p2p.core.updatePlayback($currentPosition, $playbackSpeed);", null)
        }
    }

    fun sendInitialMessage() {
        webMessageProtocol.sendInitialMessage()
    }

    fun sendAllStreams(streamsJSON: String){
        webView.evaluateJavascript("javascript:window.p2p.parseAllStreams('$streamsJSON');", null)
    }

    fun sendStream(streamJSON: String){
        webView.evaluateJavascript("javascript:window.p2p.parseStream('$streamJSON');", null)
    }

    fun setManifestUrl(manifestUrl: String){
        webView.evaluateJavascript("javascript:window.p2p.setManifestUrl('$manifestUrl');", null)
    }

}
