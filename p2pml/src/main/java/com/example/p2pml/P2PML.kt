package com.example.p2pml

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.p2pml.Constants.CORE_FILE_PATH
import com.example.p2pml.Constants.QueryParams.MANIFEST
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.server.ServerModule
import com.example.p2pml.utils.ExoPlayerPlaybackCalculator
import com.example.p2pml.utils.P2PStateManager
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.CompletableDeferred

/**
 * `P2PML` is a manager class that facilitates peer-to-peer media streaming.
 *
 * @constructor Creates an instance of `P2PML` with optional core configuration and server port.
 *
 * @param coreConfigJson Optional JSON string containing core P2P configurations.
 *   Refer to the [CoreConfig Documentation](https://novage.github.io/p2p-media-loader/docs/v2.1.0/types/p2p_media_loader_core.CoreConfig.html)
 *   for the expected structure and available configurations.
 *   Defaults to an empty string, which implies default configurations.
 *
 * @param serverPort The port number on which the internal server will run.
 *   Defaults to `8080`.
 *
 * **Example Usage:**
 * ```kotlin
 * val p2pml = P2PML(
 *     coreConfigJson = "{\"exampleKey\":\"exampleValue\"}",
 *     serverPort = 8080
 * )
 */
@UnstableApi
class P2PML(
    private val coreConfigJson: String = "",
    private val serverPort: Int = Constants.DEFAULT_SERVER_PORT
) {
    private val p2pEngineStateManager = P2PStateManager()
    private val exoPlayerPlaybackCalculator = ExoPlayerPlaybackCalculator()
    private val manifestParser = HlsManifestParser(
        exoPlayerPlaybackCalculator, serverPort
    )

    private lateinit var webViewManager: WebViewManager
    private lateinit var serverModule: ServerModule

    private val webViewLoadCompletion = CompletableDeferred<Unit>()

    /**
     * Initializes the `P2PML` manager by setting up the WebView and starting the internal server.
     *
     * This method must be called before using other functionalities of `P2PML`.
     *
     * @param context The Android [Context] used to initialize the WebView.
     * @param coroutineScope The [LifecycleCoroutineScope] for managing coroutines within the lifecycle.
     *
     * **Example:**
     * ```kotlin
     * p2pml.initialize(context, lifecycleScope)
     * ```
     */
    fun initialize(context: Context, coroutineScope: LifecycleCoroutineScope) {
        webViewManager = WebViewManager(
            context,
            coroutineScope,
            p2pEngineStateManager,
            exoPlayerPlaybackCalculator
        ) {
            onWebViewLoaded()
        }

        serverModule = ServerModule(
            webViewManager,
            manifestParser,
            p2pEngineStateManager
        ) {
            onServerStarted()
        }

        serverModule.startServer(serverPort)
    }

    fun applyP2PDynamicCoreConfig(dynamicP2PCoreConfigJson: String) {
        webViewManager.applyDynamicP2PCoreConfig(dynamicP2PCoreConfigJson)
    }

    /**
     * Associates an [ExoPlayer] instance with the `P2PML` manager.
     *
     * This allows `P2PML` to monitor and calculate playback positions and speeds,
     * facilitating synchronized P2P streaming and segment distribution.
     *
     * @param exoPlayer The [ExoPlayer] instance to be associated with `P2PML`.
     *
     * **Example:**
     * ```kotlin
     * p2pml.setExoPlayer(exoPlayerInstance)
     * ```
     */
    fun setExoPlayer(exoPlayer: ExoPlayer) {
        exoPlayerPlaybackCalculator.setExoPlayer(exoPlayer)
    }

    /**
     * Retrieves the server-localized manifest URL corresponding to the provided external manifest URL.
     *
     * @param manifestUrl The external manifest URL (e.g., an HLS `.m3u8` URL) to be localized.
     * @return A [String] representing the server-localized manifest URL.
     *
     * **Example:**
     * ```kotlin
     * val serverManifestUrl = p2pml.getServerManifestUrl("https://example.com/manifest.m3u8")
     * exoPlayer.prepare(MediaItem.fromUri(serverManifestUrl))
     * exoPlayer.play()
     * ```
     */
    suspend fun getServerManifestUrl(manifestUrl: String): String {
        webViewLoadCompletion.await()

        val encodedManifestURL = manifestUrl.encodeURLQueryComponent()
        return Utils.getUrl(serverPort, "$MANIFEST$encodedManifestURL")
    }

    /**
     * Stops the internal P2P server and cleans up associated resources.
     *
     * **Note:** After calling this method, P2P streaming functionalities will be unavailable until re-initialized.
     *
     * **Example:**
     * ```kotlin
     * p2pml.stopServer()
     * ```
     */
    fun stopServer() {
        serverModule.stopServer()
        webViewManager.destroy()
    }

    private suspend fun onWebViewLoaded() {
        webViewManager.initP2P(coreConfigJson)
        webViewLoadCompletion.complete(Unit)
    }

    private fun onServerStarted() {
        val urlPath = Utils.getUrl(serverPort, CORE_FILE_PATH)
        webViewManager.loadWebView(urlPath)
    }
}