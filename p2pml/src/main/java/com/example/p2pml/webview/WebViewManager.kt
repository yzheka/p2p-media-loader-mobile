package com.example.p2pml.webview

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
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
    private val engineStateManager: P2PStateManager,
    private val playbackCalculator: ExoPlayerPlaybackCalculator,
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
                    if (engineStateManager.isEngineDisabled()) {
                        Log.d(
                            "WebViewManager",
                            "P2P Engine disabled, stopping playback info update."
                        )
                        playbackInfoJob?.cancel()
                        playbackInfoJob = null
                        break
                    }

                    val currentPlaybackInfo =
                        playbackCalculator.getPlaybackPositionAndSpeed()
                    val playbackInfoJson = Json.encodeToString(currentPlaybackInfo)

                    sendPlaybackInfo(playbackInfoJson)

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

    fun applyDynamicConfig(dynamicCoreConfigJson: String) {
        coroutineScope.launch(Dispatchers.Main) {
            val isP2PDisabled = determineP2PDisabledStatus(dynamicCoreConfigJson) ?: return@launch

            engineStateManager.changeP2PEngineStatus(isP2PDisabled)

            webView.evaluateJavascript(
                "javascript:window.p2p.applyDynamicP2PCoreConfig('$dynamicCoreConfigJson');",
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
        if (engineStateManager.isEngineDisabled()) return null

        startPlaybackInfoUpdate()
        return webMessageProtocol.requestSegmentBytes(segmentUrl)
    }

    suspend fun sendInitialMessage() {
        if (engineStateManager.isEngineDisabled()) return

        webMessageProtocol.sendInitialMessage()
    }

    private suspend fun sendPlaybackInfo(playbackInfoJson: String) {
        if (engineStateManager.isEngineDisabled()) return

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.updatePlaybackInfo('$playbackInfoJson');",
                null
            )
        }
    }

    suspend fun sendAllStreams(streamsJson: String) {
        if (engineStateManager.isEngineDisabled()) return

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.parseAllStreams('$streamsJson');",
                null
            )
        }
    }

    suspend fun initCoreEngine(coreConfigJson: String) {
       withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.initP2P('$coreConfigJson');",
                null
            )
        }
    }

    suspend fun sendStream(streamJson: String) {
        if (engineStateManager.isEngineDisabled()) return

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.parseStream('$streamJson');",
                null
            )
        }
    }

    suspend fun setManifestUrl(manifestUrl: String) {
        if (engineStateManager.isEngineDisabled()) return

        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "javascript:window.p2p.setManifestUrl('$manifestUrl');",
                null
            )
        }
    }

    private fun destroyWebView() {
        webView.apply {
            clearHistory()
            clearCache(false)
            removeJavascriptInterface("Android")
            destroy()
        }
    }

    suspend fun destroy() {
        playbackInfoJob?.cancel()
        playbackInfoJob = null

        webMessageProtocol.clear()
        destroyWebView()
    }
}