package com.example.p2pml.server

import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.example.p2pml.Constants.MPEGURL_CONTENT_TYPE
import com.example.p2pml.parser.HlsManifestParser
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@OptIn(UnstableApi::class)
internal class ManifestHandler(
    private val httpClient: OkHttpClient,
    private val manifestParser: HlsManifestParser,
    private val webViewManager: WebViewManager
) {
    private var isMasterManifestProcessed = false
    private val mutex = Mutex()

    suspend fun handleManifestRequest(call: ApplicationCall) {
        val manifestParam = call.request.queryParameters["manifest"]
            ?: return call.respondText(
                "Missing 'manifest' parameter",
                status = HttpStatusCode.BadRequest
            )
        val decodedManifestUrl = manifestParam.decodeURLQueryComponent()

        try {
            val originalManifest = fetchManifest(call, decodedManifestUrl)
            val modifiedManifest = manifestParser.getModifiedManifest(
                originalManifest,
                decodedManifestUrl
            )
            val needsInitialSetup = checkAndSetInitialProcessing()

            handleUpdate(decodedManifestUrl, needsInitialSetup)
            call.respondText(modifiedManifest, ContentType.parse(MPEGURL_CONTENT_TYPE))
        } catch (e: Exception) {
            call.respondText(
                "Error fetching master manifest",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    private suspend fun handleUpdate(manifestUrl: String, needsInitialSetup: Boolean) {
        try {
            val updateStreamJSON = manifestParser.getUpdateStreamParamsJSON(manifestUrl)

            if (needsInitialSetup) {
                val streamsJSON = manifestParser.getStreamsJSON()
                webViewManager.sendInitialMessage()
                webViewManager.setManifestUrl(manifestUrl)
                webViewManager.sendAllStreams(streamsJSON)
                updateStreamJSON?.let { webViewManager.sendStream(it) }
            } else {
                updateStreamJSON?.let { json ->
                    webViewManager.sendStream(json)
                } ?: throw IOException("updateStreamJSON is null")
            }
        } catch (e: IOException) {
            Log.e("handleUpdate", "Unexpected error occurred: ${e.message}")
        }
    }

    private suspend fun checkAndSetInitialProcessing(): Boolean = mutex.withLock {
        if (isMasterManifestProcessed) return false

        isMasterManifestProcessed = true
        return true
    }

    private suspend fun fetchManifest(
        call: ApplicationCall,
        manifestUrl: String
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(manifestUrl)
            .apply { Utils.copyHeaders(call, this) }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Failed to fetch manifest: $manifestUrl")
            }
            response.body?.string() ?: throw IllegalStateException("Empty response body")
        }
    }

}