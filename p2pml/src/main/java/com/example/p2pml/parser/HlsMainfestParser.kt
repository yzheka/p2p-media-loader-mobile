package com.example.p2pml.parser

import android.util.Log
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
import com.example.p2pml.ExoPlayerPlaybackCalculator

@UnstableApi
internal class HlsManifestParser(
    private val exoPlayerPlaybackCalculator: ExoPlayerPlaybackCalculator,
    private val serverPort: Int
) {
    private var isLastRequestedStreamLive = false
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

    suspend fun isLastRequestedStreamLive(): Boolean {
        return mutex.withLock {
            isLastRequestedStreamLive
        }
    }

    private suspend fun parseHlsManifest(manifestUrl: String, manifest: String): String {
        val hlsPlaylist = parser.parse(manifestUrl.toUri(), manifest.byteInputStream())

        return when (hlsPlaylist) {
            is HlsMediaPlaylist -> parseMediaPlaylist(manifestUrl, hlsPlaylist, manifest)
            is HlsMultivariantPlaylist -> parseMultivariantPlaylist(manifestUrl, hlsPlaylist, manifest)
            else -> throw IllegalStateException("Unsupported playlist type")
        }
    }

    //TODO: Implement correct live segments support
    private suspend fun parseMediaPlaylist(
        manifestUrl: String,
        mediaPlaylist: HlsMediaPlaylist,
        originalManifest: String
    ): String
    {
        isLastRequestedStreamLive = !mediaPlaylist.hasEndTag
        val newMediaSequence = mediaPlaylist.mediaSequence
        val updatedManifestBuilder = StringBuilder(originalManifest)

        val previousSegments = streamSegments[manifestUrl]
        previousSegments?.removeAll { it.externalId < newMediaSequence }
        val newSegmentsInPlaylist = mutableListOf<Segment>()

       Log.d("CurrentPlayPosition", "MediaPlaylist: ${exoPlayerPlaybackCalculator.getAbsolutePlaybackPosition(
           manifestUrl,
           originalManifest
       )}")
        var startTime = if(isLastRequestedStreamLive) {
            exoPlayerPlaybackCalculator.getAbsolutePlaybackPosition(
                manifestUrl,
                originalManifest
            ).toDouble()
        } else {
            0.0
        }


        val initializationSegments = mutableSetOf<HlsMediaPlaylist.Segment>()
        var startSegmentIndex = newMediaSequence
        mediaPlaylist.segments.forEach{ segment ->
            if (segment.initializationSegment != null) {
                initializationSegments.add(segment.initializationSegment!!)
            }
            if(previousSegments != null && startSegmentIndex <= previousSegments.lastOrNull()!!.externalId) {
                return@forEach
            }

            val newSegment = processSegment(
                segment,
                manifestUrl,
                startSegmentIndex,
                startTime,
                updatedManifestBuilder,
                isLastRequestedStreamLive
            )
            newSegmentsInPlaylist.add(newSegment)
            startSegmentIndex++
            startTime += segment.durationUs / 1000.0
        }

        initializationSegments.forEach { initializationSegment ->
            replaceUrlInManifest(
                originalManifest,
                manifestUrl,
                initializationSegment.url,
                updatedManifestBuilder
            )
        }


        updateStreamData(manifestUrl, newSegmentsInPlaylist)

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
            processVariant(
                variant,
                index,
                originalManifest,
                manifestUrl,
                updatedManifestBuilder
            )
        }

        hlsPlaylist.audios.forEachIndexed { index, audio ->
            processRendition(
                audio,
                index,
                originalManifest,
                manifestUrl,
                updatedManifestBuilder
            )
        }

        hlsPlaylist.subtitles.forEach{ subtitle ->
            replaceUrlInManifest(
                originalManifest,
                manifestUrl,
                subtitle.url.toString(),
                updatedManifestBuilder
            )
        }

        hlsPlaylist.closedCaptions.forEach{ caption ->
            replaceUrlInManifest(
                originalManifest,
                manifestUrl,
                caption.url.toString(),
                updatedManifestBuilder
            )
        }
        return updatedManifestBuilder.toString()
    }

    private fun updateStreamData(variantUrl: String, allSegmentInPlaylist: List<Segment>) {
        val previousSegments = streamSegments[variantUrl]

        val updateStream = if (previousSegments !== null) {
            previousSegments.addAll(allSegmentInPlaylist)
            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = previousSegments,
                removeSegmentsIds = emptyList()
            )
        } else
            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = allSegmentInPlaylist,
                removeSegmentsIds = emptyList()
            )

        /*val updateStream = if (previousSegments != null) {
            val addedSegments = allSegmentInPlaylist.filter { newSegment ->
                previousSegments.none { it.runtimeId == newSegment.runtimeId }
            }
            val removedSegments = previousSegments.filter { oldSegment ->
                allSegmentInPlaylist.none { it.runtimeId == oldSegment.runtimeId }
            }
            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = addedSegments,
                removeSegmentsIds = removedSegments.map { it.runtimeId }
            )
        } else {
            UpdateStreamParams(
                streamRuntimeId = variantUrl,
                addSegments = allSegmentInPlaylist,
                removeSegmentsIds = emptyList()
            )
        }*/
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
        index: Long,
        startTime: Double,
        manifestBuilder: StringBuilder,
        isLiveStream: Boolean
    ): Segment {
        val segmentUri = segment.url
        val segmentUriInManifest = findUrlInManifest(
            manifestBuilder.toString(),
            segmentUri,
            variantUrl
        )
        val absoluteSegmentUrl = getAbsoluteUrl(variantUrl, segmentUri)
        val encodedAbsoluteSegmentUrl = Utils.encodeUrlToBase64(absoluteSegmentUrl)
        val newUrl = Utils.getUrl(
            serverPort,
            "${QueryParams.SEGMENT}$encodedAbsoluteSegmentUrl"
        )

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

        val segmentDurationInMs = segment.durationUs / 1_000.0
        val endTime = startTime + segmentDurationInMs
        if(!isLiveStream){
            return Segment(
                runtimeId = absoluteSegmentUrl,
                externalId = index,
                url = absoluteSegmentUrl,
                byteRange = byteRange,
                startTime = startTime / 1_000.0,
                endTime = endTime / 1_000.0
            )
        } else {
            return Segment(
                runtimeId = absoluteSegmentUrl,
                externalId = index,
                url = absoluteSegmentUrl,
                byteRange = byteRange,
                startTime = startTime.toDouble(),
                endTime = endTime.toDouble()
            )
        }

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
