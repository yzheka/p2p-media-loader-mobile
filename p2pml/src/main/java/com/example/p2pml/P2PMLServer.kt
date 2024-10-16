package com.example.p2pml

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.util.UnstableApi
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.static
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import okhttp3.OkHttpClient
import io.ktor.server.plugins.cors.routing.CORS

@UnstableApi
class P2PMLServer(
    context: Context,
    coroutineScope: CoroutineScope,
    private val serverPort: Int = 8080
) {
    private var coreWebView : CoreWebView
    private var server: ApplicationEngine? = null
    private val hlsManifestParser: HlsManifestParser = HlsManifestParser()
    private val client = OkHttpClient()

    fun getServerManifestUrl(manifestUrl: String): String {
        val encodedManifestURL = manifestUrl.encodeURLQueryComponent()
        return "http://127.0.0.1:$serverPort/?manifest=$encodedManifestURL"
    }
    init {
        startServer()
        coreWebView = CoreWebView(context, coroutineScope)
    }

    /**
     * Starts the Ktor server on the specified port asynchronously.
     */
    fun startServer() {
        if (server != null) {
            Log.w(TAG, "Server is already running.")
            return
        }

        server = embeddedServer(CIO, serverPort) {
            install(CORS) {
                anyHost()
            }
            routing {
                get("/") {
                    when {
                        call.parameters["manifest"] != null -> handleMasterManifestRequest(call)
                        call.parameters["variantPlaylist"] != null -> handleVariantPlaylistRequest(call)
                        call.parameters["segment"] != null -> handleSegmentRequest(call)
                        else -> call.respondText("Missing required parameter", status = HttpStatusCode.BadRequest)
                    }
                }

                staticResources("/p2pml/static", "p2pml/static")
            }
        }.start(wait = false)
    }

    fun setUpPlaybackInfoCallback(callback: () -> Pair<Float, Float>) {
        coreWebView.setUpPlaybackInfoCallback(callback)
    }


    @SuppressLint("UnsafeOptInUsageError")
    private suspend fun handleMasterManifestRequest(call: ApplicationCall) {
        val manifestParam = call.request.queryParameters["manifest"]!!
        val decodedManifestUrl = manifestParam.decodeURLQueryComponent()
        Log.i(TAG, "Received request for master manifest: $decodedManifestUrl")

        try {
            val modifiedManifest = hlsManifestParser.getModifiedMasterManifest(call, decodedManifestUrl)
            val streamsJSON = hlsManifestParser.getStreamsJSON()

            runOnUiThread {
                coreWebView.sendInitialMessage()
                coreWebView.setManifestUrl(decodedManifestUrl)
                coreWebView.sendAllStreams(streamsJSON)
            }

            call.respondText(modifiedManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching or modifying master manifest: ${e.message}")
            call.respondText(
                "Error fetching master manifest",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    private suspend fun handleVariantPlaylistRequest(call: ApplicationCall) {
        val variantPlaylistParam = call.request.queryParameters["variantPlaylist"]!!
        val decodedVariantUrl = variantPlaylistParam.decodeURLQueryComponent()
        Log.i(TAG, "Received request for variant playlist: $decodedVariantUrl")

        try {
            val modifiedVariantManifest = hlsManifestParser.getModifiedVariantManifest(call, decodedVariantUrl)
            val updateStreamJSON = hlsManifestParser.getUpdateStreamParamsJSON(decodedVariantUrl)

            runOnUiThread {
                coreWebView.sendStream(updateStreamJSON)
            }

            call.respondText(modifiedVariantManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching or modifying variant manifest: ${e.message}")
            call.respondText(
                "Error fetching variant manifest",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    private suspend fun handleSegmentRequest(call: ApplicationCall) {
        val segmentUrlParam = call.request.queryParameters["segment"]!!
        val decodedSegmentUrl = segmentUrlParam.decodeURLQueryComponent()

        Log.i(TAG, "Received request for segment: $decodedSegmentUrl")
        try {
            val deferredSegmentBytes = coreWebView.requestSegmentBytes(decodedSegmentUrl)
            val segmentBytes = deferredSegmentBytes.await()

            call.respondBytes(segmentBytes, ContentType.Application.OctetStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching segment: ${e.message}")
            call.respondText(
                "Error fetching segment",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    /**
     * Stops the embedded Ktor server.
     */
    fun stopServer() {
        server?.stop(1000, 2000)
        client.dispatcher.executorService.shutdown()
        coreWebView.destroy()
        Log.i(TAG, "P2PMLServer stopped.")
    }

    private fun runOnUiThread(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post(action)
    }

    companion object {
        private const val TAG = "P2PMLServer"
    }
}
