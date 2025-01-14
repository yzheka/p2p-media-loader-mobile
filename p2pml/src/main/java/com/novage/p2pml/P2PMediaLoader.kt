package com.novage.p2pml

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.Constants.CORE_FILE_URL
import com.novage.p2pml.Constants.CUSTOM_FILE_URL
import com.novage.p2pml.Constants.QueryParams.MANIFEST
import com.novage.p2pml.interop.OnErrorCallback
import com.novage.p2pml.interop.P2PReadyCallback
import com.novage.p2pml.parser.HlsManifestParser
import com.novage.p2pml.server.ServerModule
import com.novage.p2pml.utils.ExoPlayerPlaybackCalculator
import com.novage.p2pml.utils.P2PStateManager
import com.novage.p2pml.utils.Utils
import com.novage.p2pml.webview.WebViewManager
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * `P2PMediaLoader` facilitates peer-to-peer media streaming within an Android application.
 *
 * @param readyCallback Callback invoked when the P2P engine is ready for use
 * @param onReadyErrorCallback Callback invoked when an error occurs
 * @param coreConfigJson Sets core P2P configurations. See [P2PML Core Config](https://novage.github.io/p2p-media-loader/docs/v2.1.0/types/p2p_media_loader_core.CoreConfig.html)
 * JSON string with core configurations. Default: empty string (uses default config)
 *
 * @param serverPort Port number for the local server. Default: 8080
 * @param customJavaScriptInterfaces List of custom JavaScript interfaces to inject into the WebView.
 * The feature has to be used with custom engine implementation. Default: empty list
 *
 * @param customEngineImplementationPath Resource path for custom implementation.
 * Default: null (uses built-in implementation)
 */
@UnstableApi
class P2PMediaLoader(
    private val readyCallback: P2PReadyCallback,
    private val onReadyErrorCallback: OnErrorCallback,
    private val coreConfigJson: String = "",
    private val serverPort: Int = Constants.DEFAULT_SERVER_PORT,
    private val customJavaScriptInterfaces: List<Pair<String, Any>> = emptyList(),
    private val customEngineImplementationPath: String? = null,
) {
    // Second constructor for Java compatibility
    constructor(
        readyCallback: P2PReadyCallback,
        onReadyErrorCallback: OnErrorCallback,
        serverPort: Int,
        coreConfigJson: String,
    ) : this(
        readyCallback,
        onReadyErrorCallback,
        coreConfigJson,
        serverPort,
        emptyList(),
        null
    )

    private val engineStateManager = P2PStateManager()
    private val playbackCalculator = ExoPlayerPlaybackCalculator()
    private val manifestParser = HlsManifestParser(playbackCalculator, serverPort)

    private var job: Job? = null
    private var scope: CoroutineScope? = null

    private var appState = AppState.INITIALIZED
    private var webViewManager: WebViewManager? = null
    private var serverModule: ServerModule? = null

    /**
     * Initializes and starts P2P media streaming components.
     *
     * @param context Android context required for WebView initialization
     * @throws IllegalStateException if called in an invalid state
     */
    fun start(context: Context) {
        if (appState == AppState.STARTED) {
            throw IllegalStateException("Cannot start P2PMediaLoader in state: $appState")
        }

        job = Job()
        scope = CoroutineScope(job!! + Dispatchers.Main)

        initializeComponents(context)
        appState = AppState.STARTED
    }

    private fun initializeComponents(context: Context) {
        webViewManager =
            WebViewManager(
                context,
                scope!!,
                engineStateManager,
                playbackCalculator,
                customJavaScriptInterfaces,
                onPageLoadFinished = { onWebViewLoaded() },
            )

        serverModule =
            ServerModule(
                webViewManager!!,
                manifestParser,
                engineStateManager,
                customEngineImplementationPath,
                onServerStarted = { onServerStarted() },
                onManifestChanged = { onManifestChanged() },
            ).apply { start(serverPort) }
    }

    /**
     * Applies dynamic core configurations to the `P2PMediaLoader` engine.
     *
     * @param dynamicCoreConfigJson A JSON string containing dynamic core configurations for the P2P engine.
     * Refer to the [DynamicCoreConfig Documentation](https://novage.github.io/p2p-media-loader/docs/v2.1.0/types/p2p_media_loader_core.DynamicCoreConfig.html).
     * @throws IllegalStateException if P2PMediaLoader is not started
     */
    fun applyDynamicConfig(dynamicCoreConfigJson: String) {
        ensureStarted()

        webViewManager?.applyDynamicConfig(dynamicCoreConfigJson)
    }

    /**
     * Connects an ExoPlayer instance for playback monitoring.
     * Required for P2P segment distribution and synchronization.
     *
     * @param exoPlayer ExoPlayer instance to monitor
     * @throws IllegalStateException if P2PMediaLoader is not started
     */
    fun attachPlayer(exoPlayer: ExoPlayer) {
        ensureStarted()

        playbackCalculator.setExoPlayer(exoPlayer)
    }

    /**
     * Converts an external HLS manifest URL to a local URL handled by the P2P engine.
     *
     * @param manifestUrl External HLS manifest URL (.m3u8)
     * @return Local URL for P2P-enabled playback
     * @throws IllegalStateException if P2PMediaLoader is not started
     */
    fun getManifestUrl(manifestUrl: String): String {
        ensureStarted()

        val encodedManifestURL = manifestUrl.encodeURLQueryComponent()
        return Utils.getUrl(serverPort, "$MANIFEST$encodedManifestURL")
    }

    private fun ensureStarted() {
        if (appState != AppState.STARTED) {
            throw IllegalStateException("Operation not allowed in state: $appState")
        }
    }

    /**
     * Stops P2P streaming and releases all resources.
     * Call [start] to reinitialize after stopping.
     *
     * @throws IllegalStateException if P2PMediaLoader is not started
     */
    fun stop() {
        if (appState != AppState.STARTED) {
            throw IllegalStateException("Cannot stop P2PMediaLoader in state: $appState")
        }

        runBlocking {
            webViewManager?.destroy()
            webViewManager = null

            serverModule?.stop()
            serverModule = null

            playbackCalculator.reset()
            manifestParser.reset()
            engineStateManager.reset()

            appState = AppState.STOPPED

            job?.cancel()
            job = null
            scope = null
        }
    }

    private suspend fun onManifestChanged() {
        playbackCalculator.resetData()
        manifestParser.reset()
    }

    private fun onWebViewLoaded() {
        scope?.launch {
            webViewManager?.initCoreEngine(coreConfigJson)

            try {
                readyCallback.onReady()
            } catch (e: Exception) {
                onReadyErrorCallback.onError(e.message ?: "Unknown error")
            }
        }
    }

    private fun onServerStarted() {
        val urlPath =
            if (customEngineImplementationPath != null) {
                Utils.getUrl(serverPort, CUSTOM_FILE_URL)
            } else {
                Utils.getUrl(serverPort, CORE_FILE_URL)
            }

        webViewManager?.loadWebView(urlPath)
    }
}
