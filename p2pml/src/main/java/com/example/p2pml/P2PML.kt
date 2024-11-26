package com.example.p2pml

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.p2pml.Constants.CORE_FILE_PATH
import com.example.p2pml.Constants.QueryParams.MANIFEST
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.server.ServerModule
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.CompletableDeferred

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

    fun setExoPlayer(exoPlayer: ExoPlayer) {
        exoPlayerPlaybackCalculator.setExoPlayer(exoPlayer)
    }

    suspend fun getServerManifestUrl(manifestUrl: String): String {
        webViewLoadCompletion.await()

        val encodedManifestURL = manifestUrl.encodeURLQueryComponent()
        return Utils.getUrl(serverPort, "$MANIFEST$encodedManifestURL")
    }

    fun stopServer() {
        serverModule.stopServer()
        webViewManager.destroy()
    }

    private fun onWebViewLoaded() {
        webViewManager.initP2P(coreConfigJson)
        webViewLoadCompletion.complete(Unit)
    }

    private fun onServerStarted() {
        val urlPath = Utils.getUrl(serverPort, CORE_FILE_PATH)
        webViewManager.loadWebView(urlPath)
    }
}