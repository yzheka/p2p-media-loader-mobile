package com.example.p2pml

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.server.ServerModule
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.encodeURLQueryComponent
import kotlinx.coroutines.CoroutineScope

@UnstableApi
class P2PML(
    context: Context,
    coroutineScope: CoroutineScope,
    private val serverPort: Int = 8080
) {
    private val webViewManager: WebViewManager = WebViewManager(context, coroutineScope)
    private val manifestParser: HlsManifestParser = HlsManifestParser()
    private val serverModule: ServerModule = ServerModule(webViewManager, manifestParser) {
        onServerStarted()
    }

    init {
        startServer()
    }

    private fun startServer() {
        serverModule.startServer(serverPort)
    }

    private fun onServerStarted() {
        webViewManager.loadWebView("http://127.0.0.1:$serverPort/p2pml/static/core.html")
    }

    fun getServerManifestUrl(manifestUrl: String): String {
        val encodedManifestURL = manifestUrl.encodeURLQueryComponent()
        return "http://127.0.0.1:$serverPort/?manifest=$encodedManifestURL"
    }

    fun stopServer() {
        serverModule.stopServer()
        webViewManager.destroy()
    }

    fun setUpPlaybackInfoCallback(callback: () -> Pair<Float, Float>) {
        webViewManager.setUpPlaybackInfoCallback(callback)
    }
}