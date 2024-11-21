package com.example.p2pml

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.p2pml.Constants.QueryParams.MANIFEST
import com.example.p2pml.Constants.CORE_FILE_PATH
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.server.ServerModule
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.CompletableDeferred

@UnstableApi
class P2PML(
    context: Context,
    coroutineScope: LifecycleCoroutineScope,
    private val serverPort: Int = Constants.DEFAULT_SERVER_PORT
) {
    private val exoPlayerPlaybackCalculator = ExoPlayerPlaybackCalculator()
    private val manifestParser: HlsManifestParser = HlsManifestParser(
        exoPlayerPlaybackCalculator, serverPort
    )

    private val webViewManager: WebViewManager = WebViewManager(
        context,
        coroutineScope,
        exoPlayerPlaybackCalculator,
    ) {
        webViewLoadCompletion.complete(Unit)
    }
    private val serverModule: ServerModule = ServerModule(webViewManager, manifestParser) {
        onServerStarted()
    }
    private val webViewLoadCompletion = CompletableDeferred<Unit>()

    init {
        startServer()
    }

    fun setExoPlayer(exoPlayer: ExoPlayer) {
        exoPlayerPlaybackCalculator.setExoPlayer(exoPlayer)
    }

    private fun startServer() {
        serverModule.startServer(serverPort)
    }

    private fun onServerStarted() {
        val urlPath = Utils.getUrl(serverPort, CORE_FILE_PATH)
        webViewManager.loadWebView(urlPath)
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

    fun setUpPlaybackInfoCallback(callback: () -> Pair<Float, Float>) {
        webViewManager.setUpPlaybackInfoCallback(callback)
    }
}