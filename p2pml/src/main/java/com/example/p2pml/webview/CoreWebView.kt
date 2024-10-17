package com.example.p2pml.webview

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.webkit.WebViewClientCompat
import com.example.p2pml.utils.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class CoreWebView(
    context: Context,
    coroutineScope: CoroutineScope,
    private val onPageLoadFinished: () -> Unit
) {
    private var playbackInfoCallback: () -> Pair<Float, Float> = { Pair(0f, 1f) }

    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        webViewClient = WebViewClientCompat()
        visibility = View.GONE
        addJavascriptInterface(JavaScriptInterface(context, onPageLoadFinished), "Android")
    }
    private val webMessageProtocol = WebMessageProtocol(webView, coroutineScope)

    fun loadUrl(url: String) {
        Utils.runOnUiThread {
            webView.loadUrl(url)
        }
    }

    fun destroy() {
        webView.apply {
            parent?.let { (it as ViewGroup).removeView(this) }
            destroy()
        }
    }

    fun setUpPlaybackInfoCallback(callback: () -> Pair<Float, Float>) {
        this.playbackInfoCallback = callback
    }

    suspend fun requestSegmentBytes(segmentUrl: String): CompletableDeferred<ByteArray> {
        var currentPosition: Float
        var playbackSpeed: Float

        // ExoPlayer is not thread-safe, so we need to access it on the main thread
        withContext(Dispatchers.Main) {
            currentPosition = playbackInfoCallback().first
            playbackSpeed = playbackInfoCallback().second
        }

       return webMessageProtocol.requestSegmentBytes(segmentUrl, currentPosition, playbackSpeed)
    }

    fun sendInitialMessage() {
        webMessageProtocol.sendInitialMessage()
    }

    fun sendAllStreams(streamsJSON: String){
        Utils.runOnUiThread {
            webView.evaluateJavascript(
                "javascript:window.p2p.parseAllStreams('$streamsJSON');",
                null
            )
        }
    }

    fun sendStream(streamJSON: String){
        Utils.runOnUiThread {
            webView.evaluateJavascript(
                "javascript:window.p2p.parseStream('$streamJSON');",
                null
            )
        }
    }

    fun setManifestUrl(manifestUrl: String){
        Utils.runOnUiThread {
            webView.evaluateJavascript(
                "javascript:window.p2p.setManifestUrl('$manifestUrl');",
                null
            )
        }
    }
}
