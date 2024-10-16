package com.example.p2pml


import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLQueryComponent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

@OptIn(UnstableApi::class)
class ManifestHandler(
    private val manifestParser: HlsManifestParser,
    private val webViewManager: WebViewManager
) {

    suspend fun handleMasterManifestRequest(call: ApplicationCall) {
        val manifestParam = call.request.queryParameters["manifest"]!!
        val decodedManifestUrl = manifestParam.decodeURLQueryComponent()

        try {
            val modifiedManifest = manifestParser.getModifiedMasterManifest(call, decodedManifestUrl)
            val streamsJSON = manifestParser.getStreamsJSON()

            Utils.runOnUiThread {
                webViewManager.sendInitialMessage()
                webViewManager.setManifestUrl(decodedManifestUrl)
                webViewManager.sendAllStreams(streamsJSON)
            }

            call.respondText(modifiedManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (e: Exception) {
            call.respondText(
                "Error fetching master manifest",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

    suspend fun handleVariantPlaylistRequest(call: ApplicationCall) {
        val variantPlaylistParam = call.request.queryParameters["variantPlaylist"]!!
        val decodedVariantUrl = variantPlaylistParam.decodeURLQueryComponent()

        try {
            val modifiedVariantManifest = manifestParser.getModifiedVariantManifest(call, decodedVariantUrl)
            val updateStreamJSON = manifestParser.getUpdateStreamParamsJSON(decodedVariantUrl)

            Utils.runOnUiThread {
                webViewManager.sendStream(updateStreamJSON)
            }

            call.respondText(modifiedVariantManifest, ContentType.parse("application/vnd.apple.mpegurl"))
        } catch (e: Exception) {
            call.respondText(
                "Error fetching variant manifest",
                status = HttpStatusCode.InternalServerError
            )
        }
    }

}