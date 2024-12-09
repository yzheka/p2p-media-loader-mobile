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
import com.example.p2pml.DynamicP2PCoreConfig
import com.example.p2pml.utils.ExoPlayerPlaybackCalculator
import com.example.p2pml.utils.P2PStateManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    onPageLoadFinished: suspend () -> Unit
) {
    @SuppressLint("SetJavaScriptEnabled")
    private val webView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = WebViewClientCompat()
        visibility = View.GONE
        addJavascriptInterface(
            JavaScriptInterface(coroutineScope, onPageLoadFinished), "Android"
        )
    }
    private val webMessageProtocol = WebMessageProtocol(webView, coroutineScope)

    private var playbackInfoJob: Job? = null

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
        coroutineScope.launch(Dispatchers.Main) {
            webView.loadUrl(url)
        }
    }

    fun applyDynamicP2PCoreConfig(coreDynamicConfigJson: String) {
        coroutineScope.launch(Dispatchers.Main) {
            val isP2PDisabled = determineP2PDisabledStatus(coreDynamicConfigJson) ?: return@launch

            p2pEngineStateManager.changeP2PEngineStatus(isP2PDisabled)

            webView.evaluateJavascript(
                "javascript:window.p2p.applyDynamicP2PCoreConfig('$coreDynamicConfigJson');",
                null
            )
        }
    }

    private fun determineP2PDisabledStatus(coreDynamicConfigJson: String): Boolean? {
        return try {
            val config = Json.decodeFromString<DynamicP2PCoreConfig>(coreDynamicConfigJson)

            config.isP2PDisabled
                ?: if (config.mainStream?.isP2PDisabled == config.secondaryStream?.isP2PDisabled) {
                    config.mainStream?.isP2PDisabled
                } else {
                    null
                }
        } catch (e: Exception) {
            null
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

    suspend fun initP2P(coreConfigJson: String) {
       withContext(Dispatchers.Main) {
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

    fun destroy() {
        playbackInfoJob?.cancel()
        playbackInfoJob = null

        webView.apply {
            parent?.let { (it as ViewGroup).removeView(this) }
            destroy()
        }
    }
}