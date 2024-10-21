package com.example.p2pml.server

import android.util.Log
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode

import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText


class SegmentHandler(private val webViewManager: WebViewManager) {
    suspend fun handleSegmentRequest(call: ApplicationCall) {
        val segmentUrlParam = call.request.queryParameters["segment"]
            ?: return call.respondText(
                "Missing 'segment' parameter",
                status = HttpStatusCode.BadRequest
            )
        val decodedSegmentUrl = Utils.decodeBase64Url(segmentUrlParam)

        try {
            val deferredSegmentBytes = webViewManager.requestSegmentBytes(decodedSegmentUrl)
            val segmentBytes = deferredSegmentBytes.await()

            call.respondBytes(segmentBytes, ContentType.Application.OctetStream)
        } catch (e: Exception) {
            call.respondText(
                "Error fetching segment",
                status = HttpStatusCode.InternalServerError
            )
        }
    }
}