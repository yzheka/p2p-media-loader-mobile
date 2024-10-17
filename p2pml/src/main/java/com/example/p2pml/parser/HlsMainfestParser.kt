package com.example.p2pml.parser

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.example.p2pml.ByteRange
import com.example.p2pml.Segment
import com.example.p2pml.Stream
import com.example.p2pml.UpdateStreamParams
import com.example.p2pml.utils.Utils
import io.ktor.http.encodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

@UnstableApi
class HlsManifestParser(
    private val okHttpClient: OkHttpClient = OkHttpClient(),
    private val serverPort: Int = 8080
) {
    private val parser = HlsPlaylistParser()
    private val mutex = Mutex()

    private val streams = mutableListOf<Stream>()
    private val streamSegments = mutableMapOf<String, MutableList<Segment>>()
    private val updateStreamParams = mutableMapOf<String, UpdateStreamParams>()

    suspend fun getModifiedMasterManifest(call: ApplicationCall, manifestUrl: String): String {
        val originalManifest = fetchManifest(call, manifestUrl)
        return mutex.withLock { parseMasterManifest(manifestUrl, originalManifest) }
    }

    suspend fun getModifiedVariantManifest(call: ApplicationCall, variantUrl: String): String {
        val originalManifest = fetchManifest(call, variantUrl)
        return mutex.withLock { parseVariantManifest(variantUrl, originalManifest) }
    }

    //TODO: Implement correct live segments support
    //TODO: Remove Initial segments from the processing
    private fun parseVariantManifest(variantUrl: String, manifest: String): String
    {
        val mediaPlaylist = parser.parse(variantUrl.toUri(), manifest.byteInputStream())

        require(mediaPlaylist is HlsMediaPlaylist) {
            "The provided URL does not point to a media playlist."
        }

        var startTime = 0.0
        val segmentsInPlaylist = mutableListOf<Segment>()
        val updatedVariantManifestBuilder = StringBuilder(manifest)
        mediaPlaylist.segments.forEachIndexed { index, segment ->
            val segmentUri = segment.url
            val segmentUriInManifest = findUrlInManifest(manifest, segmentUri, variantUrl)
            val absoluteSegmentUrl =
                getAbsoluteUrl(variantUrl, segmentUri).encodeURLQueryComponent()
            val newUrl = "http://127.0.0.1:$serverPort/?segment=$absoluteSegmentUrl"


            val startIndex = updatedVariantManifestBuilder.indexOf(segmentUriInManifest)
            if (startIndex == -1)
                throw IllegalStateException("Segment URL not found in the manifest: $segmentUri")

            val endIndex = startIndex + segmentUriInManifest.length
            if(segment.initializationSegment == segment) {
                updatedVariantManifestBuilder.replace(startIndex, endIndex, absoluteSegmentUrl)
                return@forEachIndexed
            }

            val byteRange = segment.byteRangeLength.takeIf { it != -1L && segment.byteRangeOffset != 0L }
                ?.let { ByteRange(segment.byteRangeOffset, segment.byteRangeOffset + it) }

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

            updatedVariantManifestBuilder.replace(startIndex, endIndex, newUrl)
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

        return updatedVariantManifestBuilder.toString()
    }

    suspend fun getUpdateStreamParamsJSON(variantUrl: String): String {
        mutex.withLock {
            val updateStream = updateStreamParams[variantUrl]
                ?: throw IllegalStateException("Update stream params not found for variant: $variantUrl")
            return Json.encodeToString(updateStream)
        }
    }

    suspend fun getStreamsJSON(): String {
        return mutex.withLock { Json.encodeToString(streams) }
    }

    private fun parseMasterManifest(
        manifestUrl: String,
        manifest: String
    ): String {
        val hlsPlaylist = parser.parse(manifestUrl.toUri(), manifest.byteInputStream())

        require(hlsPlaylist is HlsMultivariantPlaylist) {
            "The provided URL does not point to a master playlist."
        }

        val updatedManifestBuilder = StringBuilder(manifest)
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

            val absoluteVariantUrl = getAbsoluteUrl(manifestUrl, mediaUri).encodeURLQueryComponent()
            val newUrl = "http://127.0.0.1:$serverPort/?variantPlaylist=$absoluteVariantUrl"

            val startIndex = updatedManifestBuilder.indexOf(mediaUriInManifest)
            if (startIndex == -1) {
                throw IllegalStateException("Variant URL not found in the manifest: $mediaUri")
            }
            val endIndex = startIndex + mediaUriInManifest.length
            updatedManifestBuilder.replace(startIndex, endIndex, newUrl)
        }

        return updatedManifestBuilder.toString()
    }

    private fun findUrlInManifest(manifest: String, urlToFind: String, manifestUrl: String): String {
        val baseManifestURL = manifestUrl.substringBeforeLast("/") + "/"
        val relativeUrlToFind = urlToFind.removePrefix(baseManifestURL)

        return when {
            manifest.contains(urlToFind) -> urlToFind
            manifest.contains(relativeUrlToFind) -> relativeUrlToFind
            else -> throw IllegalStateException("URL not found in manifest. urlToFind:" +
                    "$urlToFind, manifestUrl: $manifestUrl")
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

    private suspend fun fetchManifest(
        call: ApplicationCall,
        manifestUrl: String
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(manifestUrl)
            .apply { Utils.copyHeaders(call, this) }
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch manifest: $manifestUrl")
            }
            response.body?.string() ?: throw IllegalStateException("Empty response body")
        }
    }
}
