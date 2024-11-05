package com.example.p2pml.server

import android.util.Log
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText

internal class SegmentHandler(private val webViewManager: WebViewManager) {
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
            val deferredSegmentBytes = webViewManager.requestSegmentBytes(decodedSegmentUrl)
            val segmentBytes = deferredSegmentBytes.await()

            if (byteRange != null) {
                call.respond(
                    ByteArrayContent(
                        bytes = segmentBytes,
                        contentType = ContentType.Application.OctetStream,
                        status = HttpStatusCode.PartialContent
                    )
                )
            }
            else
                call.respondBytes(segmentBytes, ContentType.Application.OctetStream)
        } catch (e: Exception) {
            call.respondText(
                "Error fetching segment",
                status = HttpStatusCode.InternalServerError
            )
        }
    }
}