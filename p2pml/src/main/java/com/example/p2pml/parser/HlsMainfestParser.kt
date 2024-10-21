package com.example.p2pml.parser

import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.example.p2pml.ByteRange
import com.example.p2pml.Constants.StreamTypes
import com.example.p2pml.Constants.HTTPS_PREFIX
import com.example.p2pml.Constants.HTTP_PREFIX
import com.example.p2pml.Constants.MICROSECONDS_IN_SECOND
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
import com.example.p2pml.Constants.QueryParams

@UnstableApi
internal class HlsManifestParser(
    private val serverPort: Int
) {
    private val okHttpClient: OkHttpClient = OkHttpClient()
    private val parser = HlsPlaylistParser()
    private val mutex = Mutex()

    private val streams = mutableListOf<Stream>()
    private val streamSegments = mutableMapOf<String, MutableList<Segment>>()
    private val updateStreamParams = mutableMapOf<String, UpdateStreamParams>()

    suspend fun getModifiedManifest(call: ApplicationCall, manifestUrl: String): String {
        val originalManifest = fetchManifest(call, manifestUrl)
        return mutex.withLock{ parseHlsManifest(manifestUrl, originalManifest) }
    }

    private fun parseHlsManifest(manifestUrl: String, manifest: String): String {
        val hlsPlaylist = parser.parse(manifestUrl.toUri(), manifest.byteInputStream())

        return when (hlsPlaylist) {
            is HlsMediaPlaylist -> parseMediaPlaylist(manifestUrl, hlsPlaylist, manifest)
            is HlsMultivariantPlaylist -> parseMultivariantPlaylist(manifestUrl, hlsPlaylist, manifest)
            else -> throw IllegalStateException("Unsupported playlist type")
        }
    }

    //TODO: Implement correct live segments support
    private fun parseMediaPlaylist(manifestUrl: String, mediaPlaylist: HlsMediaPlaylist,originalManifest: String): String
    {
        val updatedManifestBuilder = StringBuilder(originalManifest)
        val segmentsInPlaylist = mutableListOf<Segment>()
        var startTime = 0.0

        val initializationSegments = mutableSetOf<HlsMediaPlaylist.Segment>()
        mediaPlaylist.segments.forEachIndexed { index, segment ->
            if (segment.initializationSegment != null) {
                initializationSegments.add(segment.initializationSegment!!)
            }

            val newSegment = processSegment(segment, manifestUrl, index, startTime, updatedManifestBuilder)
            segmentsInPlaylist.add(newSegment)
            startTime += segment.durationUs.toDouble() / MICROSECONDS_IN_SECOND
        }

        initializationSegments.forEach { initializationSegment ->
            replaceUrlInManifest(originalManifest, manifestUrl, initializationSegment.url, updatedManifestBuilder)
        }

        updateStreamData(manifestUrl, segmentsInPlaylist)

        val stream = findStreamByRuntimeId(manifestUrl)
        // This should be fired if there is no master manifest
        if(stream == null) {
            streams.add(Stream(runtimeId = manifestUrl, type = StreamTypes.MAIN, index = 0))
        }
        return updatedManifestBuilder.toString()
    }

    suspend fun getUpdateStreamParamsJSON(variantUrl: String): String? {
        mutex.withLock {
            val updateStream = updateStreamParams[variantUrl]
                ?: return null
            return Json.encodeToString(updateStream)
        }
    }

    suspend fun getStreamsJSON(): String {
        return mutex.withLock {
            Json.encodeToString(streams)
        }
    }

    private fun parseMultivariantPlaylist(
        manifestUrl: String,
        hlsPlaylist: HlsMultivariantPlaylist,
        originalManifest: String
    ): String {
        val updatedManifestBuilder = StringBuilder(originalManifest)

        hlsPlaylist.variants.forEachIndexed { index, variant ->
            processVariant(variant, index, originalManifest, manifestUrl, updatedManifestBuilder)
        }

        hlsPlaylist.audios.forEachIndexed { index, audio ->
            processRendition(audio, index, originalManifest, manifestUrl, updatedManifestBuilder)
        }

        hlsPlaylist.subtitles.forEach{ subtitle ->
            replaceUrlInManifest(originalManifest, manifestUrl, subtitle.url.toString(), updatedManifestBuilder)
        }

        hlsPlaylist.closedCaptions.forEach{ caption ->
            replaceUrlInManifest(originalManifest, manifestUrl, caption.url.toString(), updatedManifestBuilder)
        }
        return updatedManifestBuilder.toString()
    }

    private fun updateStreamData(variantUrl: String, newSegments: List<Segment>) {
        val previousSegments = streamSegments[variantUrl]
        val updateStream = if (previousSegments != null) {
            val addedSegments = newSegments.filter { newSegment ->
                previousSegments.none { it.runtimeId == newSegment.runtimeId }
            }
            val removedSegments = previousSegments.filter { oldSegment ->
                newSegments.none { it.runtimeId == oldSegment.runtimeId }
            }
            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = addedSegments,
                removeSegmentsIds = removedSegments.map { it.runtimeId }
            )
        } else {
            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = newSegments,
                removeSegmentsIds = emptyList()
            )
        }

        streamSegments[variantUrl] = newSegments.toMutableList()
        updateStreamParams[variantUrl] = updateStream
    }

    private fun findStreamByRuntimeId(runtimeId: String): Stream? {
        return streams.find { it.runtimeId == runtimeId }
    }

    private fun processVariant(
        variant: HlsMultivariantPlaylist.Variant,
        index: Int,
        manifest: String,
        manifestUrl: String,
        updatedManifestBuilder: StringBuilder
    ) {
        val streamUrl = variant.url.toString()
        streams.add(Stream(runtimeId = streamUrl, type = StreamTypes.MAIN, index = index))
        replaceUrlInManifest(manifest, manifestUrl, streamUrl, updatedManifestBuilder, QueryParams.MANIFEST)
    }

    private fun processRendition(
        rendition: HlsMultivariantPlaylist.Rendition,
        index: Int,
        manifest: String,
        manifestUrl: String,
        updatedManifestBuilder: StringBuilder
    ) {
        val streamUrl = rendition.url.toString()
        streams.add(Stream(runtimeId = streamUrl, type = StreamTypes.SECONDARY, index = index))
        replaceUrlInManifest(manifest, manifestUrl, streamUrl, updatedManifestBuilder, QueryParams.MANIFEST)
    }

    private fun processSegment(
        segment: HlsMediaPlaylist.Segment,
        variantUrl: String,
        index: Int,
        startTime: Double,
        manifestBuilder: StringBuilder
    ): Segment {
        val segmentUri = segment.url
        val segmentUriInManifest = findUrlInManifest(manifestBuilder.toString(), segmentUri, variantUrl)
        val absoluteSegmentUrl = getAbsoluteUrl(variantUrl, segmentUri)
        val encodedAbsoluteSegmentUrl = Utils.encodeUrlToBase64(absoluteSegmentUrl)
        val newUrl = Utils.getUrl(serverPort, "${QueryParams.SEGMENT}$encodedAbsoluteSegmentUrl")

        val startIndex = manifestBuilder.indexOf(segmentUriInManifest)
            .takeIf { it != -1 }
            ?: throw IllegalStateException("URL not found in manifest: $segmentUri")
        val endIndex = startIndex + segmentUriInManifest.length
        manifestBuilder.replace(
            startIndex,
            endIndex,
            newUrl
        )

        val byteRange = segment.byteRangeLength.takeIf { it != -1L  }
            ?.let { ByteRange(segment.byteRangeOffset, segment.byteRangeOffset + it) }

        val endTime = startTime + segment.durationUs.toDouble() / 1_000_000

        return Segment(
            runtimeId = absoluteSegmentUrl,
            externalId = index,
            url = absoluteSegmentUrl,
            byteRange = byteRange,
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun replaceUrlInManifest(
        manifest: String,
        baseManifestUrl: String,
        originalUrl: String,
        updatedManifestBuilder: StringBuilder,
        queryParam: String? = null
    ) {
        val urlToFind = findUrlInManifest(manifest, originalUrl, baseManifestUrl)
        val absoluteUrl = getAbsoluteUrl(baseManifestUrl, originalUrl).encodeURLQueryComponent()
        val newUrl = if(queryParam != null) {
            Utils.getUrl(serverPort, "$queryParam$absoluteUrl")
        } else {
            absoluteUrl
        }

        val startIndex = updatedManifestBuilder.indexOf(urlToFind)
            .takeIf { it != -1 }
            ?: throw IllegalStateException("URL not found in manifest: $originalUrl")
        val endIndex = startIndex + urlToFind.length
        updatedManifestBuilder.replace(startIndex, endIndex, newUrl)
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
        if(mediaUri.startsWith(HTTP_PREFIX) || mediaUri.startsWith(HTTPS_PREFIX)) {
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
