package com.example.p2pml

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.media3.exoplayer.ExoPlayer
import androidx.webkit.WebViewClientCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class CoreWebView(
    context: Context,
    private val coroutineScope: CoroutineScope,
    private val fileToLoad: String = "file:///android_asset/core.html"
) {
    private var playbackInfo: PlaybackInfo? = null

    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = WebViewClientCompat()
        visibility = View.GONE

        loadUrl(fileToLoad)
    }
    private val webMessageProtocol = WebMessageProtocol(webView, coroutineScope)

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
        var currentPosition = 0f
        var playbackSpeed = 1f

        withContext(Dispatchers.Main) {
            currentPosition = (playbackInfo?.currentPosition?.div(1000f)) ?: 0f
            playbackSpeed = playbackInfo?.playbackSpeed ?: 1f
        }

       return webMessageProtocol.requestSegmentBytes(segmentUrl, currentPosition, playbackSpeed)
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
