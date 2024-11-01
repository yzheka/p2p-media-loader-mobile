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
    private val streamSegmentsIds = mutableMapOf<String, MutableSet<Long>>()
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

    private fun removeObsoleteSegments(variantUrl: String, removeUntilId: Long): List<String> {
        val obsoleteSegment = streamSegments[variantUrl]?.filter { it.externalId < removeUntilId }

        obsoleteSegment?.forEach { segment ->
            streamSegments[variantUrl]?.removeAll { it.externalId == segment.externalId }
            streamSegmentsIds[variantUrl]?.remove(segment.externalId)
        }

        return obsoleteSegment?.map { it.runtimeId } ?: emptyList()
    }

    private fun addNewSegment(
        variantUrl: String,
        segmentId: Long,
        initialStartTime: Double,
        segment: HlsMediaPlaylist.Segment,
        isStreamLive: Boolean
    ): Segment? {
        val previousSegmentIds = streamSegmentsIds.getOrPut(variantUrl) { mutableSetOf() }
        if(previousSegmentIds.contains(segmentId)) return null

        val prevSegment = if(previousSegmentIds.contains(segmentId - 1)) {
            streamSegments[variantUrl]?.find { it.externalId == segmentId - 1 }
        } else {
            null
        }

        val segmentDurationInMs = segment.durationUs / 1000.0
        val startTime = prevSegment?.endTime ?: initialStartTime
        val endTime = startTime + segmentDurationInMs

        val absoluteUrl = getAbsoluteUrl(variantUrl, segment.url)
        val newSegment = if (isStreamLive){
            Segment(
            runtimeId = absoluteUrl,
            externalId = segmentId,
            url = absoluteUrl,
            byteRange = segment.byteRangeLength.takeIf { it != -1L }
                ?.let { ByteRange(segment.byteRangeOffset, segment.byteRangeOffset + it) },
            startTime = startTime,
            endTime = endTime
            )
        } else {
            Segment(
                runtimeId = absoluteUrl,
                externalId = segmentId,
                url = absoluteUrl,
                byteRange = segment.byteRangeLength.takeIf { it != -1L }
                    ?.let { ByteRange(segment.byteRangeOffset, segment.byteRangeOffset + it) },
                startTime = startTime / 1_000.0,
                endTime = endTime / 1_000.0
            )
        }

        val streamSegmentsList = streamSegments[variantUrl]
        if(streamSegmentsList != null) {
            streamSegmentsList.add(newSegment)
        } else {
            streamSegments[variantUrl] = mutableListOf(newSegment)
        }
        previousSegmentIds.add(segmentId)

        return newSegment
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

        val startTime = if(isLastRequestedStreamLive) {
            exoPlayerPlaybackCalculator.getAbsolutePlaybackPosition(
                manifestUrl,
                originalManifest
            ).toDouble()
        } else {
            0.0
        }
        Log.d("+++HlsManifestParser", "startTime: $startTime")
        val segmentsToRemove = removeObsoleteSegments(manifestUrl, newMediaSequence)
        val initializationSegments = mutableSetOf<HlsMediaPlaylist.Segment>()
        val segmentsToAdd = mutableListOf<Segment>()

        mediaPlaylist.segments.forEachIndexed{ index, segment ->
            if (segment.initializationSegment != null) {
                initializationSegments.add(segment.initializationSegment!!)
            }

            val segmentIndex = index + newMediaSequence
            processSegment(segment, manifestUrl, updatedManifestBuilder)
            val newSegment = addNewSegment(manifestUrl, segmentIndex, startTime, segment, isLastRequestedStreamLive)
            if(newSegment != null)
                segmentsToAdd.add(newSegment)
        }

        initializationSegments.forEach { initializationSegment ->
            replaceUrlInManifest(
                originalManifest,
                manifestUrl,
                initializationSegment.url,
                updatedManifestBuilder
            )
        }

        updateStreamData(manifestUrl, segmentsToAdd, segmentsToRemove)

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

    private fun updateStreamData(
        variantUrl: String,
        newSegments: List<Segment>,
        segmentsToRemove: List<String>
    ) {
        val updateStream = UpdateStreamParams(
            streamRuntimeId = variantUrl,
            addSegments = newSegments,
            removeSegmentsIds = segmentsToRemove
        )

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
        manifestBuilder: StringBuilder,
    ) {
        val segmentUri = segment.url
        val segmentUriInManifest = findUrlInManifest(
            manifestBuilder.toString(),
            segmentUri,
            variantUrl
        )
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

        /*val byteRange = segment.byteRangeLength.takeIf { it != -1L  }
            ?.let { ByteRange(segment.byteRangeOffset, segment.byteRangeOffset + it) }

        val segmentDurationInMs = segment.durationUs / 1_000.0
        val endTime = startTime + segmentDurationInMs*/
        /*if(!isLiveStream){
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
        }*/

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
