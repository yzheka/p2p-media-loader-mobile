package com.example.p2pml.server

import androidx.media3.common.util.UnstableApi
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.utils.P2PStateManager
import com.example.p2pml.webview.WebViewManager
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.cors.routing.CORS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

@UnstableApi
internal class ServerModule(
    private val webViewManager: WebViewManager,
    private val manifestParser: HlsManifestParser,
    private val p2pEngineStateManager: P2PStateManager,
    private val customEngineImplementationPath: String? = null,
    private val onServerStarted: () -> Unit,
) {
    private var httpClient: OkHttpClient? = null
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start(port: Int = 8080) {
        if (server != null) return

        server =
            embeddedServer(CIO, port) {
                configureCORS(this)
                configureRouting(this)
                subscribeToServerStarted(this)
            }.start(wait = false)
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    suspend fun stop() {
        destroyHttpClient()
        stopServer()
    }

    private fun subscribeToServerStarted(application: Application) {
        application.monitor.subscribe(ApplicationStarted) {
            onServerStarted()
        }
    }

    private fun configureCORS(application: Application) {
        application.install(CORS) {
            anyHost()
        }
    }

    private fun configureRouting(application: Application) {
        httpClient = OkHttpClient()
        val manifestHandler = ManifestHandler(httpClient!!, manifestParser, webViewManager)
        val segmentHandler =
            SegmentHandler(
                httpClient!!,
                webViewManager,
                manifestParser,
                p2pEngineStateManager,
            )
        val routingModule =
            ServerRoutes(
                manifestHandler,
                segmentHandler,
                customEngineImplementationPath,
            )

        routingModule.setup(application)
    }

    private suspend fun destroyHttpClient() {
        withContext(Dispatchers.IO) {
            httpClient?.dispatcher?.executorService?.shutdown()
            httpClient?.connectionPool?.evictAll()
            httpClient = null
        }
    }
}
