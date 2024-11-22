package com.example.p2pml.server

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.p2pml.P2PStateManager
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(UnstableApi::class)
internal class SegmentHandler(
    private val webViewManager: WebViewManager,
    private val parser: HlsManifestParser,
    private val p2pEngineStateManager: P2PStateManager
) {
    private val okHttpClient: OkHttpClient = OkHttpClient()

    suspend fun handleSegmentRequest(call: ApplicationCall) {
        val segmentUrlParam = call.request.queryParameters["segment"]
            ?: return call.respondText(
                "Missing 'segment' parameter",
                status = HttpStatusCode.BadRequest
            )
        val decodedSegmentUrl = Utils.decodeBase64Url(segmentUrlParam)
        val byteRange = call.request.headers[HttpHeaders.Range]

        Log.d("SegmentHandler",
            "Received segment request for $decodedSegmentUrl with byte range $byteRange")

        try {
            //val isCurrentSegment = parser.isCurrentSegment(decodedSegmentUrl)

            //Log.d("isCurrentSegment", "$isCurrentSegment")


            if(!p2pEngineStateManager.isP2PEngineEnabled()){
                Log.d("SegmentHandler++", "!isCurrentSegment!")
                fetchSegment(call, decodedSegmentUrl)
                return
            }

            val deferredSegmentBytes = webViewManager.requestSegmentBytes(decodedSegmentUrl)
                ?: throw IllegalStateException("Deferred segment bytes are null. P2P engine might be disabled.")
            val segmentBytes = deferredSegmentBytes.await()

            respondWithBytes(call, segmentBytes, byteRange)
        } catch (e: Exception) {
            call.respondText(
                "Error fetching segment",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    private suspend fun respondWithBytes(
        call: ApplicationCall,
        segmentBytes: ByteArray,
        byteRange: String?
    ){
        if(byteRange != null) {
            Log.d("SegmentHandler++", "range != null")
            call.respond(object : OutgoingContent.ByteArrayContent() {
                override val contentType: ContentType = ContentType.Application.OctetStream
                override val contentLength: Long = segmentBytes.size.toLong()
                override val status: HttpStatusCode = HttpStatusCode.PartialContent

                override fun bytes(): ByteArray = segmentBytes
            })
            return
        }
        Log.d("SegmentHandler++", "range == null")
        call.respondBytes(segmentBytes, ContentType.Application.OctetStream)
    }


    private suspend fun fetchSegment(
        call: ApplicationCall,
        url: String
    ) =  withContext(Dispatchers.IO)
    {
        val filteredUrl = url.substringBeforeLast("|")
        val requestBuilder = Request.Builder().url(filteredUrl)

        copyHeaders(call, requestBuilder)

        val request = requestBuilder.build()

        val response = okHttpClient.newCall(request).execute()
        val segmentBytes = response.body?.bytes() ?:  throw Exception("Empty response body")
        val byteRange = response.headers[HttpHeaders.Range]

        if(byteRange !== null) {
            call.respond(object : OutgoingContent.ByteArrayContent() {
                override val contentType: ContentType = ContentType.Application.OctetStream
                override val contentLength: Long = segmentBytes.size.toLong()
                override val status: HttpStatusCode = HttpStatusCode.PartialContent

                override fun bytes(): ByteArray = segmentBytes
            })
        } else
            call.respondBytes(segmentBytes, ContentType.Application.OctetStream)


    }

    private fun copyHeaders(call: ApplicationCall, requestBuilder: Request.Builder) {
        val excludedHeaders = setOf(
            "Host",
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