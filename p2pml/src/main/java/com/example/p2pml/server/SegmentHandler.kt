package com.example.p2pml.server

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(UnstableApi::class)
internal class SegmentHandler
    (private val webViewManager: WebViewManager, private val parser: HlsManifestParser) {
    private val okHttpClient: OkHttpClient = OkHttpClient()
    suspend fun handleSegmentRequest(call: ApplicationCall) {
        val segmentUrlParam = call.request.queryParameters["segment"]
            ?: return call.respondText(
                "Missing 'segment' parameter",
                status = HttpStatusCode.BadRequest
            )
        val decodedSegmentUrl = Utils.decodeBase64Url(segmentUrlParam)
        val byteRange = call.request.headers["range"]
        Log.d("SegmentHandler", "Received segment request for $decodedSegmentUrl with byte range $byteRange")
        try {
            val isCurrentSegment = parser.isCurrentSegment(decodedSegmentUrl)
            Log.d("isCurrentSegment", "$isCurrentSegment")
            if (!isCurrentSegment) {
                Log.d("isCurrentSegment", "!isCurrentSegment Fetching segment from network")
                val segmentBytes = fetchSegment(call, decodedSegmentUrl)
                return call.respondBytes(segmentBytes, ContentType.Application.OctetStream)
            }

            val deferredSegmentBytes = webViewManager.requestSegmentBytes(decodedSegmentUrl)
            for (value in parser.lastRequestedStreamSegments.values) {
                if(value.runtimeId == decodedSegmentUrl) {
                    Log.d("SegmentHandler", "${value.externalId} - $decodedSegmentUrl - ${parser.lastMediSequence}")
                }
            }
            val segmentBytes = deferredSegmentBytes.await()

            if(byteRange != null) {
                call.respond(object : OutgoingContent.ByteArrayContent() {
                    override val contentType: ContentType = ContentType.Application.OctetStream
                    override val contentLength: Long = segmentBytes.size.toLong()
                    override val status: HttpStatusCode = HttpStatusCode.PartialContent

                    override fun bytes(): ByteArray = segmentBytes
                })
            } else
                call.respondBytes(segmentBytes, ContentType.Application.OctetStream)
        } catch (e: Exception) {
            call.respondText(
                "Error fetching segment",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    private suspend fun fetchSegment(call: ApplicationCall, url: String): ByteArray = withContext(
        Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url(url)

        copyHeaders(call, requestBuilder)

        val request = requestBuilder.build()

        okHttpClient.newCall(request).execute().use { response ->
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
}