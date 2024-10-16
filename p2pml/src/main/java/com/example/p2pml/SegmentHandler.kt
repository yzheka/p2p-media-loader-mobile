package com.example.p2pml

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText

class SegmentHandler(private val webViewManager: WebViewManager) {
    suspend fun handleSegmentRequest(call: ApplicationCall) {
        val segmentUrlParam = call.request.queryParameters["segment"]!!
        val decodedSegmentUrl = segmentUrlParam.decodeURLQueryComponent()

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