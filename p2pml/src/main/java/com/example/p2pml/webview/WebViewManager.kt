package com.example.p2pml.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.webkit.WebViewClientCompat
import com.example.p2pml.ExoPlayerPlaybackCalculator
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.utils.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
internal class WebViewManager
    (
    context: Context,
    coroutineScope: CoroutineScope,
    private val exoPlayerPlaybackCalculator: ExoPlayerPlaybackCalculator,
    private val manifestParser: HlsManifestParser,
    onPageLoadFinished: () -> Unit
) {
    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        webViewClient = WebViewClientCompat()
        visibility = View.GONE
        addJavascriptInterface(JavaScriptInterface(onPageLoadFinished), "Android")
    }
    private val webMessageProtocol = WebMessageProtocol(webView, coroutineScope)
    private var playbackInfoCallback: () -> Pair<Float, Float> = { Pair(0f, 1f) }

    fun loadWebView(url: String) {
        Utils.runOnUiThread {
            webView.loadUrl(url)
        }
    }

    fun setUpPlaybackInfoCallback(callback: () -> Pair<Float, Float>) {
        this.playbackInfoCallback = callback
    }

    suspend fun requestSegmentBytes(segmentUrl: String): CompletableDeferred<ByteArray> = withContext(Dispatchers.Main) {
        val currentPlaybackInfo = runCatching {
            exoPlayerPlaybackCalculator.getPlaybackPositionAndSpeed()
        }.onFailure { exception ->
            Log.e("PlaybackError", "Error occurred: ${exception.message}", exception)
        }.getOrNull() ?: run {
            Log.e("PlaybackError", "Error getting playback position and speed")
            throw IllegalStateException("Error getting playback position and speed")
        }

        Log.d("PlaybackInfo", "Current position: ${currentPlaybackInfo.first}, Speed: ${currentPlaybackInfo.second}")

        if (!manifestParser.isLastRequestedStreamLive()) {
            Log.d("PlaybackInfo", "Is not live")
            return@withContext webMessageProtocol.requestSegmentBytes(segmentUrl, currentPlaybackInfo.first / 1_000_000f , currentPlaybackInfo.second)
        }
        Log.d("PlaybackInfo", "Is live ${manifestParser.isLastRequestedStreamLive()}")
        return@withContext webMessageProtocol.requestSegmentBytes(segmentUrl, currentPlaybackInfo.first / 1_000_000f, currentPlaybackInfo.second)
    }

    fun sendInitialMessage() {
        webMessageProtocol.sendInitialMessage()
    }

    fun sendAllStreams(streamsJSON: String) {
        Utils.runOnUiThread {
            webView.evaluateJavascript(
                "javascript:window.p2p.parseAllStreams('$streamsJSON');",
                null
            )
        }
    }

    fun sendStream(streamJSON: String) {
        Utils.runOnUiThread {
            webView.evaluateJavascript(
                "javascript:window.p2p.parseStream('$streamJSON');",
                null
            )
        }
    }

    fun setManifestUrl(manifestUrl: String) {
        Utils.runOnUiThread {
            webView.evaluateJavascript(
                "javascript:window.p2p.setManifestUrl('$manifestUrl');",
                null
            )
        }
    }


    fun destroy() {
        webView.apply {
            parent?.let { (it as ViewGroup).removeView(this) }
            destroy()
        }
    }
}