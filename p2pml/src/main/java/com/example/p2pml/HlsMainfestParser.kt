package com.example.p2pml

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import io.ktor.http.encodeURLQueryComponent
import io.ktor.server.application.ApplicationCall

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@UnstableApi
class HlsManifestParser(private val okHttpClient: OkHttpClient = OkHttpClient(), private val serverPort: Int = 8080) {
    private val parser = HlsPlaylistParser()
    private var masterManifest: HlsMultivariantPlaylist? = null

    private val streams = mutableListOf<Stream>()
    private val streamSegments = mutableMapOf<String, MutableList<Segment>>()
    private val updateStreamParams = mutableMapOf<String, UpdateStreamParams>()

    suspend fun getModifiedMasterManifest(call: ApplicationCall, manifestUrl: String): String {
        val originalManifest = fetchManifest(call, manifestUrl)
        return parseMasterManifest(manifestUrl, originalManifest)
    }

    suspend fun getModifiedVariantManifest(call: ApplicationCall, variantUrl: String): String {
        val originalManifest = fetchManifest(call, variantUrl)
        return parseVariantManifest(variantUrl, originalManifest)
    }

    //TODO: Implement correct live segments support
    //TODO: Remove Initial segments from the processing
    private fun parseVariantManifest(variantUrl: String, manifest: String): String
    {
        val mediaPlaylist = parser.parse(variantUrl.toUri(), manifest.byteInputStream())

        if(mediaPlaylist !is HlsMediaPlaylist) {
            throw IOException("The provided URL does not point to a media playlist.")
        }

        var updatedVariantManifest = manifest

        val segmentsInPlaylist = mutableListOf<Segment>()
        var startTime = 0.0
        mediaPlaylist.segments.forEachIndexed { index, segment ->
            val segmentUri = segment.url
            val segmentUriInManifest = findUrlInManifest(manifest, segmentUri, variantUrl)
                ?: throw IllegalStateException("Segment URL not found in the manifest: $segmentUri")
            val absoluteSegmentUrl =
                getAbsoluteUrl(variantUrl, segmentUri).encodeURLQueryComponent()
            val newUrl = "http://127.0.0.1:$serverPort/?segment=$absoluteSegmentUrl"


            if(segment.initializationSegment == segment) {
                updatedVariantManifest = updatedVariantManifest.replace(segmentUriInManifest, absoluteSegmentUrl)
                return@forEachIndexed
            }

            val startRange = segment.byteRangeOffset
            val endRange = startRange + segment.byteRangeLength

            val byteRange = if (startRange != 0L && endRange != -1L)
                ByteRange(startRange, endRange)
            else
                null

            val endTime = startTime + segment.durationUs.toDouble() / 1_000_000
            val mediaSegment = Segment(
                runtimeId = absoluteSegmentUrl,
                externalId = index,
                url = absoluteSegmentUrl,
                byteRange = byteRange,
                startTime = startTime,
                endTime = endTime
            )
            segmentsInPlaylist.add(mediaSegment)
            startTime = endTime

            updatedVariantManifest = updatedVariantManifest.replace(segmentUriInManifest, newUrl)
        }

        val previousSegments = streamSegments.getOrDefault(variantUrl, null)

        val updateStream = if(previousSegments != null) {
            val newSegments = segmentsInPlaylist.filter { segment ->
                previousSegments.none { it.runtimeId == segment.runtimeId }
            }

            val removedSegments = previousSegments.filter { segment ->
                segmentsInPlaylist.none { it.runtimeId == segment.runtimeId }
            }

            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = newSegments,
                removeSegmentsIds = removedSegments.map { it.runtimeId }
            )
        } else {
            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = segmentsInPlaylist,
                removeSegmentsIds = emptyList()
            )
        }

        streamSegments[variantUrl] = segmentsInPlaylist
        updateStreamParams[variantUrl] = updateStream

        return updatedVariantManifest
    }

    fun getUpdateStreamParamsJSON(variantUrl: String): String {
        val updateStream = updateStreamParams[variantUrl]
            ?: throw IllegalStateException("Update stream params not found for variant: $variantUrl")
        return Json.encodeToString(updateStream)
    }

    fun getStreamsJSON(): String {
        return Json.encodeToString(streams)
    }

    private fun parseMasterManifest(
        manifestUrl: String,
        manifest: String
    ): String {
        val hlsPlaylist = parser.parse(manifestUrl.toUri(), manifest.byteInputStream())

        if (hlsPlaylist !is HlsMultivariantPlaylist) {
            throw IOException("The provided URL does not point to a master playlist.")
        }

        masterManifest = hlsPlaylist

        var updatedManifest = manifest

        hlsPlaylist.variants.forEachIndexed { index, variant ->
            val streamUrl = variant.url.toString()
            val stream = Stream(
                runtimeId = streamUrl,
                type = "main",
                index = index
            )
            streams.add(stream)

            val mediaUri = variant.url.toString()
            val mediaUriInManifest = findUrlInManifest(manifest, mediaUri, manifestUrl)
                ?: throw IllegalStateException("Variant URL not found in the manifest: $mediaUri")

            val absoluteVariantUrl = getAbsoluteUrl(manifestUrl, mediaUri).encodeURLQueryComponent()
            val newUrl = "http://127.0.0.1:$serverPort/?variantPlaylist=$absoluteVariantUrl"

            updatedManifest = updatedManifest.replace(mediaUriInManifest, newUrl)
        }

        return updatedManifest
    }

    private fun findUrlInManifest(manifest: String, urlToFind: String, manifestUrl: String): String? {
        val baseManifestURL = manifestUrl.substringBeforeLast("/") + "/"
        val relativeUrlToFind = urlToFind.removePrefix(baseManifestURL)

        return when {
            manifest.contains(urlToFind) -> urlToFind
            manifest.contains(relativeUrlToFind) -> relativeUrlToFind
            else -> null
        }
    }

    private fun getAbsoluteUrl(baseManifestUrl: String, mediaUri: String): String {
        if(mediaUri.startsWith("http://") || mediaUri.startsWith("https://")) {
            return mediaUri
        }

        var baseUrl = baseManifestUrl.substringBeforeLast("/")
        if(!baseUrl.endsWith("/")) {
            baseUrl += "/"
        }

        return "$baseUrl$mediaUri"
    }

    private suspend fun fetchManifest(call: ApplicationCall, manifestUrl: String): String {
        val requestBuilder = Request.Builder()
            .url(manifestUrl)

        copyHeaders(call, requestBuilder)

        val request = requestBuilder.build()

        return withContext(Dispatchers.IO) {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }
            response.body?.string() ?: throw IOException("Empty response body")
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
