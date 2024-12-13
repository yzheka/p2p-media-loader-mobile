package com.example.p2pml

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.p2pml.Constants.CORE_FILE_URL
import com.example.p2pml.Constants.CUSTOM_FILE_URL
import com.example.p2pml.Constants.QueryParams.MANIFEST
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.server.ServerModule
import com.example.p2pml.utils.ExoPlayerPlaybackCalculator
import com.example.p2pml.utils.P2PStateManager
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

/**
 * `P2PMediaLoader` facilitates peer-to-peer media streaming within an Android application.
 *
 * @constructor Private constructor. Use the [Builder] to create instances.
 */
@UnstableApi
class P2PMediaLoader private constructor(
    private val coreConfigJson: String,
    private val serverPort: Int,
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
    private var webViewLoadCompletion: CompletableDeferred<Unit>? = null

    /**
     * Initializes the `P2PMediaLoader` by setting up the WebView and starting the internal server.
     *
     * This method must be called before using other functionalities of `P2PMediaLoader`.
     *
     * @param context The Android [Context] used to initialize the WebView.
     */
    fun start(context: Context) {
        if (appState != AppState.INITIALIZED && appState != AppState.STOPPED) {
            throw IllegalStateException("Cannot start P2PMediaLoader in state: $appState")
        }

        job = Job()
        scope = CoroutineScope(job!! + Dispatchers.Main)

        initializeComponents(context)
        appState = AppState.STARTED
    }

    private fun initializeComponents(context: Context) {
        webViewLoadCompletion = CompletableDeferred()

        webViewManager =
            WebViewManager(
                context = context,
                coroutineScope = scope!!,
                engineStateManager = engineStateManager,
                playbackCalculator = playbackCalculator,
                onPageLoadFinished = { onWebViewLoaded() },
            )

        serverModule =
            ServerModule(
                webViewManager = webViewManager!!,
                manifestParser = manifestParser,
                p2pEngineStateManager = engineStateManager,
                customEngineImplementationPath = customEngineImplementationPath,
                onServerStarted = { onServerStarted() },
            ).apply { start(serverPort) }
    }

    /**
     * Applies dynamic core configurations to the `P2PMediaLoader` engine.
     *
     *  @param dynamicCoreConfigJson A JSON string containing dynamic core configurations for the P2P engine.
     *  Refer to the [DynamicCoreConfig Documentation](https://novage.github.io/p2p-media-loader/docs/v2.1.0/types/p2p_media_loader_core.DynamicCoreConfig.html).
     */
    fun applyDynamicConfig(dynamicCoreConfigJson: String) {
        ensureStarted()

        webViewManager?.applyDynamicConfig(dynamicCoreConfigJson)
    }

    /**
     * Associates an [ExoPlayer] instance with the `P2PMediaLoader` manager.
     *
     * This allows `P2PMediaLoader` to monitor and calculate playback positions and speeds,
     * facilitating synchronized P2P streaming and segment distribution.
     *
     * @param exoPlayer The [ExoPlayer] instance to be associated with `P2PMediaLoader`.
     *
     */
    fun attachPlayer(exoPlayer: ExoPlayer) {
        ensureStarted()

        playbackCalculator.setExoPlayer(exoPlayer)
    }

    /**
     * Retrieves the server-localized manifest URL corresponding to the provided external manifest URL.
     *
     * @param manifestUrl The external manifest URL (e.g., an HLS `.m3u8` URL) to be localized.
     * @return A [String] representing the server-localized manifest URL.
     */
    suspend fun getManifestUrl(manifestUrl: String): String {
        ensureStarted()

        webViewLoadCompletion?.await()

        val encodedManifestURL = manifestUrl.encodeURLQueryComponent()
        return Utils.getUrl(serverPort, "$MANIFEST$encodedManifestURL")
    }

    private fun ensureStarted() {
        if (appState != AppState.STARTED) {
            throw IllegalStateException("Operation not allowed in state: $appState")
        }
    }

    /**
     * Cleans up associated resources.
     *
     * **Note:** After calling this method, P2P streaming functionalities will be unavailable until re-initialized.
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

    private suspend fun onWebViewLoaded() {
        webViewManager?.initCoreEngine(coreConfigJson)
        webViewLoadCompletion?.complete(Unit)
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

        /**
         * @property coreConfigJson Optional JSON string containing core P2P configurations.
         *   Refer to the [CoreConfig Documentation](https://novage.github.io/p2p-media-loader/docs/v2.1.0/types/p2p_media_loader_core.CoreConfig.html)
         *   for the expected structure and available configurations.
         *   Defaults to an empty string, implying default configurations.
         */
        fun setCoreConfig(coreConfigJson: String) = apply { this.coreConfig = coreConfigJson }

        /**
         * Sets the server port.
         *
         * @param port The port number on which the internal server will run.
         */
        fun setServerPort(port: Int) = apply { this.serverPort = port }

        /**
         * Sets the custom engine implementation path.
         *
         * @param path Relative path to a custom resource folder within `src/main/resources`.
         *
         * **Note on Custom P2PML Implementation:**
         *
         * The path should be relative to your application's resources directory (`src/main/resources`).
         * This enables you to provide a custom `index.html` file with a custom P2PML engine implementation.
         */
        fun setCustomImplementationPath(path: String?) = apply { this.customEngineImplementationPath = path }

        /**
         * @return A new [P2PMediaLoader] instance.
         */
        fun build(): P2PMediaLoader =
            P2PMediaLoader(
                coreConfig,
                serverPort,
                customEngineImplementationPath,
            )
    }
}
