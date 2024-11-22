package com.example.p2pml.server

import androidx.media3.common.util.UnstableApi
import com.example.p2pml.P2PStateManager
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.webview.WebViewManager
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing

@UnstableApi
internal class ServerModule(
    private val webViewManager: WebViewManager,
    private val manifestParser: HlsManifestParser,
    private val p2pEngineStateManager: P2PStateManager,
    private val onServerStarted: () -> Unit
) {
    private var server: ApplicationEngine? = null

    fun startServer(port: Int = 8080) {
        if (server != null) return

        val manifestHandler = ManifestHandler(manifestParser, webViewManager)
        val segmentHandler = SegmentHandler(webViewManager, manifestParser, p2pEngineStateManager)

        val routingModule = ServerRoutes(manifestHandler, segmentHandler)

        server = embeddedServer(CIO, port) {
            install(CORS) {
                anyHost()
            }

            routing {
                routingModule.setup(this)
            }

            environment.monitor.subscribe(ApplicationStarted) {
                onServerStarted()
            }
        }.start(wait = false)
    }

    fun stopServer() {
        server?.stop(1000, 1000)
        server = null
    }

}