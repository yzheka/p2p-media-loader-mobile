package com.novage.p2pml

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.novage.p2pml.Constants.CORE_FILE_URL
import com.novage.p2pml.Constants.CUSTOM_FILE_URL
import com.novage.p2pml.Constants.QueryParams.MANIFEST
import com.novage.p2pml.interop.OnErrorCallback
import com.novage.p2pml.interop.OnReadyCallback
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
 * @constructor Private constructor. Use the [Builder] to create instances.
 *
 * Usage:
 * ```
 * val p2pml = P2PMediaLoader.Builder()
 *    .setCoreConfig(coreConfigJson) // Optional
 *    .setServerPort(serverPort) // Optional
 *    .setCustomImplementationPath(customEngineImplementationPath) // Optional
 *    .build()
 *    ```
 */
@UnstableApi
class P2PMediaLoader private constructor(
    private val coreConfigJson: String,
    private val serverPort: Int,
    private val onReady: () -> Unit,
    private val onError: (String) -> Unit,
    private val customJavaScriptInterfaces: List<Pair<String, Any>>,
    private val customEngineImplementationPath: String?,
) {
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

            job?.cancel()
            job = null
            scope = null

            appState = AppState.STOPPED
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
                onReady()
            } catch (e: Exception) {
                onError(e.message ?: "Error occurred while initializing P2P engine")
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

    /**
     * Builder class for constructing instances of [P2PMediaLoader].
     */
    class Builder {
        private var coreConfig: String = ""
        private var serverPort: Int = Constants.DEFAULT_SERVER_PORT
        private var customEngineImplementationPath: String? = null
        private var customJavaScriptInterfaces: MutableList<Pair<String, Any>> = mutableListOf()
        private var onReady: (() -> Unit)? = null
        private var onError: ((String) -> Unit)? = null

        /**
         * Sets core P2P configurations. See [P2PML Core Config](https://novage.github.io/p2p-media-loader/docs/v2.1.0/types/p2p_media_loader_core.CoreConfig.html)
         * @param coreConfigJson JSON string with core configurations. Default: empty string (uses default config)
         */
        fun setCoreConfig(coreConfigJson: String) = apply { this.coreConfig = coreConfigJson }

        /**
         * Sets the internal server port.
         * @param port Server port number. Default: `8080`
         */
        fun setServerPort(port: Int) = apply { this.serverPort = port }

        /**
         * Sets custom engine implementation path relative to src/main/resources.
         * Allows providing custom index.html with P2PML engine implementation.
         * @param path Resource path for custom implementation. Default: null (uses built-in implementation)
         */
        fun setCustomEngineImplementationPath(path: String?) =
            apply {
                this.customEngineImplementationPath = path
            }

        /**
         * Adds a custom JavaScript interface to the WebView.
         * The feature has to be used with custom engine implementation.
         * methods has to be annotated with @JavascriptInterface.
         *
         * @param name Interface name
         * @param obj Object with methods annotated with @JavascriptInterface
         */
        fun addCustomJavaScriptInterface(
            name: String,
            obj: Any,
        ) = apply {
            customJavaScriptInterfaces.add(Pair(name, obj))
        }

        /**
         * Sets a callback to be invoked when the P2P engine is ready.
         * @param callback Callback function to be invoked when the P2P engine is ready
         */
        fun setOnReady(callback: OnReadyCallback) = apply { this.onReady = { callback.onReady() } }

        /**
         * Sets a callback to be invoked when an error occurs.
         * @param callback Callback function to be invoked when an error occurs
         */
        fun setOnError(callback: OnErrorCallback) = apply { this.onError = { callback.onError(it) } }

        /**
         * @return A new [P2PMediaLoader] instance.
         */
        fun build(): P2PMediaLoader {
            val onReadyCallback =
                onReady ?: throw IllegalStateException("`onReady` callback must be set")
            val onErrorCallback =
                onError ?: throw IllegalStateException("`onError` callback must be set")

            return P2PMediaLoader(
                coreConfig,
                serverPort,
                onReadyCallback,
                onErrorCallback,
                customJavaScriptInterfaces,
                customEngineImplementationPath,
            )
        }
    }
}
