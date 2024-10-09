package com.example.p2pml

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Website.URL
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import io.ktor.http.encodeURLQueryComponent
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request


@UnstableApi
class P2PMLServer(
    context: Context,
    private val serverPort: Int = 8080
) {
    val coreWebView = CoreWebView(context)

    private var server: ApplicationEngine? = null
    private val hlsManifestParser: HlsManifestParser = HlsManifestParser()
    private val client = OkHttpClient()

    fun getServerManifestUrl(manifestUrl: String): String {
        val encodedManifestURL = manifestUrl.encodeURLQueryComponent()
        return "http://127.0.0.1:$serverPort/?manifest=$encodedManifestURL"
    }

    fun startCoreWebView() {
        coreWebView.loadCore()
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
            routing {
                get("/") {
                    val manifestParam = call.request.queryParameters["manifest"]
                    val variantPlaylistParam = call.request.queryParameters["variantPlaylist"]
                    val segmentUrlParam = call.request.queryParameters["segment"]

                    when {
                        !manifestParam.isNullOrBlank() -> {
                            handleMasterManifestRequest(call, manifestParam)
                        }

                        !variantPlaylistParam.isNullOrBlank() -> {
                            handleVariantPlaylistRequest(call, variantPlaylistParam)
                        }

                        !segmentUrlParam.isNullOrBlank() -> {
                            handleSegmentRequest(call, segmentUrlParam)
                        }

                        else -> {
                            call.respondText(
                                "Missing required query parameter",
                                status = HttpStatusCode.BadRequest
                            )
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private suspend fun handleMasterManifestRequest(call: ApplicationCall, manifestParam: String) {
        val decodedManifestUrl = manifestParam.decodeURLQueryComponent()
        Log.i(TAG, "Received request for master manifest: $decodedManifestUrl")

        try {
            val modifiedManifest = hlsManifestParser.getModifiedMasterManifest(call, decodedManifestUrl)
            val streamsJSON = hlsManifestParser.getStreamsJSON()
            runOnUiThread {
                coreWebView.sendMessage(streamsJSON)
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

    private suspend fun handleVariantPlaylistRequest(call: ApplicationCall, variantPlaylistParam: String) {
        val decodedVariantUrl = variantPlaylistParam.decodeURLQueryComponent()
        Log.i(TAG, "Received request for variant playlist: $decodedVariantUrl")

        try {
            val modifiedVariantManifest = hlsManifestParser.getModifiedVariantManifest(call, decodedVariantUrl)
            val updateStreamJSON = hlsManifestParser.getUpdateStreamParamsJSON(decodedVariantUrl)
            println("Update Stream JSON: $updateStreamJSON")
            call.respondText(modifiedVariantManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching or modifying variant manifest: ${e.message}")
            call.respondText(
                "Error fetching variant manifest",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    private suspend fun handleSegmentRequest(call: ApplicationCall, segmentUrlParam: String) {
        val decodedSegmentUrl = segmentUrlParam.decodeURLQueryComponent()

        Log.i(TAG, "Received request for segment: $decodedSegmentUrl")
        try {
            val segmentBytes = fetchSegment(call ,decodedSegmentUrl)

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
        Log.i(TAG, "P2PMLServer stopped.")
    }

    private suspend fun fetchSegment(call: ApplicationCall, url: String): ByteArray = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)

        copyHeaders(call, requestBuilder)

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Unexpected code $response")
            }
            response.body?.bytes() ?: throw Exception("Empty response body")
        }
    }

    private fun copyHeaders(call: ApplicationCall, requestBuilder: Request.Builder) {
        val excludedHeaders = setOf(
            "Host",
            "Content-Length",
            "Connection",
            "Transfer-Encoding",
            "Expect",
            "Upgrade",
            "Proxy-Connection",
            "Keep-Alive",
            "Accept-Encoding"
        )

        for (headerName in call.request.headers.names()) {
            if (headerName !in excludedHeaders) {
                val headerValues = call.request.headers.getAll(headerName)
                if (headerValues != null) {
                    for (headerValue in headerValues) {
                        requestBuilder.addHeader(headerName, headerValue)
                    }
                }
            }
        }
    }

    private fun runOnUiThread(action: () -> Unit) {
        Handler(Looper.getMainLooper()).post(action)
    }

    companion object {
        private const val TAG = "P2PMLServer"
    }
}
