package com.example.p2pml.server

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.example.p2pml.utils.Utils
import com.example.p2pml.webview.WebViewManager
import com.example.p2pml.parser.HlsManifestParser
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(UnstableApi::class)
class ManifestHandler(
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
            val modifiedManifest = manifestParser.getModifiedManifest(call, decodedManifestUrl)

            val needsInitialSetup: Boolean
            mutex.withLock {
                needsInitialSetup = !isMasterManifestProcessed
                if (needsInitialSetup) {
                    isMasterManifestProcessed = true
                }
            }

            val updateStreamJSON = manifestParser.getUpdateStreamParamsJSON(decodedManifestUrl)

            if (needsInitialSetup) {
                val streamsJSON = manifestParser.getStreamsJSON()
                Utils.runOnUiThread {
                    webViewManager.sendInitialMessage()
                    webViewManager.setManifestUrl(decodedManifestUrl)
                    webViewManager.sendAllStreams(streamsJSON)
                    if(updateStreamJSON != null) webViewManager.sendStream(updateStreamJSON)
                }
            } else {
                if(updateStreamJSON == null){
                    throw IOException("updateStreamJSON is null")
                }
                Utils.runOnUiThread {
                   webViewManager.sendStream(updateStreamJSON)
                }
            }
            call.respondText(modifiedManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (e: Exception) {
            call.respondText(
                "Error fetching master manifest",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

}