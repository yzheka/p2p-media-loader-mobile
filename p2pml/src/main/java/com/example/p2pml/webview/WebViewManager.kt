package com.example.p2pml.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.webkit.WebViewClientCompat
import com.example.p2pml.ExoPlayerPlaybackCalculator
import com.example.p2pml.P2PStateManager
import com.example.p2pml.utils.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(UnstableApi::class)
internal class WebViewManager(
    context: Context,
    private val coroutineScope: CoroutineScope,
    private val p2pEngineStateManager: P2PStateManager,
    private val exoPlayerPlaybackCalculator: ExoPlayerPlaybackCalculator,
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

    private var playbackInfoJob: Job? = null

    init {
        coroutineScope.launch {
            p2pEngineStateManager.setOnP2PEngineStatusChange { isP2PEngineEnabled ->
                changeP2PEngineStatus(isP2PEngineEnabled)
            }
        }
    }

    private fun startPlaybackInfoUpdate() {
        if (playbackInfoJob !== null) return

        playbackInfoJob = coroutineScope.launch {
            while (isActive) {
                try {
                    if (!p2pEngineStateManager.isP2PEngineEnabled()) {
                        Log.d(
                            "WebViewManager",
                            "P2P Engine disabled, stopping playback info update."
                        )
                        playbackInfoJob?.cancel()
                        playbackInfoJob = null
                        break
                    }

                    val currentPlaybackInfo =
                        exoPlayerPlaybackCalculator.getPlaybackPositionAndSpeed()
                    val playbackInfoJSON = Json.encodeToString(currentPlaybackInfo)

                    sendPlaybackInfo(playbackInfoJSON)

                    delay(400)
                } catch (e: Exception) {
                    Log.e("WebViewManager", "Error sending playback info: ${e.message}")
                }
            }
        }
    }

    fun loadWebView(url: String) {
        Utils.runOnUiThread {
            webView.loadUrl(url)
        }
    }

    suspend fun requestSegmentBytes(segmentUrl: String): CompletableDeferred<ByteArray>? {
        if (!p2pEngineStateManager.isP2PEngineEnabled()) return null

        startPlaybackInfoUpdate()
        return webMessageProtocol.requestSegmentBytes(segmentUrl)
    }

    suspend fun sendInitialMessage() {
        if (!p2pEngineStateManager.isP2PEngineEnabled()) return

        webMessageProtocol.sendInitialMessage()
    }

    private suspend fun sendPlaybackInfo(playbackInfoJSON: String) {
        if (!p2pEngineStateManager.isP2PEngineEnabled()) return

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.updatePlaybackInfo('$playbackInfoJSON');",
                null
            )
        }
    }

    suspend fun sendAllStreams(streamsJSON: String) {
        if (!p2pEngineStateManager.isP2PEngineEnabled()) return

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.parseAllStreams('$streamsJSON');",
                null
            )
        }
    }

    fun initP2P(coreConfigJson: String) {
        Utils.runOnUiThread {
            webView.evaluateJavascript(
                "javascript:window.p2p.initP2P('$coreConfigJson');",
                null
            )
        }
    }

    suspend fun sendStream(streamJSON: String) {
        if (!p2pEngineStateManager.isP2PEngineEnabled()) return

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.parseStream('$streamJSON');",
                null
            )
        }
    }

    suspend fun setManifestUrl(manifestUrl: String) {
        if (!p2pEngineStateManager.isP2PEngineEnabled()) return

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.setManifestUrl('$manifestUrl');",
                null
            )
        }
    }

    private suspend fun changeP2PEngineStatus(isP2PEngineStatusEnabled: Boolean) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.manageP2PState($isP2PEngineStatusEnabled);",
                null
            )
        }
    }

    fun destroy() {
        playbackInfoJob?.cancel()
        playbackInfoJob = null
        coroutineScope.cancel()
        webView.apply {
            parent?.let { (it as ViewGroup).removeView(this) }
            destroy()
        }
    }
}