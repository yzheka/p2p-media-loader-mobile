package com.novage.p2pml.webview

import android.webkit.JavascriptInterface
import com.novage.p2pml.ChunkDownloadedDetails
import com.novage.p2pml.CoreEventMap
import com.novage.p2pml.utils.EventEmitter

internal class JavaScriptInterface(
    private val onFullyLoadedCallback: () -> Unit,
    private val eventEmitter: EventEmitter,
) {
    @JavascriptInterface
    fun onWebViewLoaded() {
        onFullyLoadedCallback()
    }

    @JavascriptInterface
    fun onChunkDownloaded(
        bytesLength: Int,
        downloadSource: String,
        peerId: String,
    ) {
        val peerIdentifier = if (peerId == "undefined") null else peerId

        val details =
            ChunkDownloadedDetails(
                bytesLength = bytesLength,
                downloadSource = downloadSource,
                peerId = peerIdentifier,
            )

        eventEmitter.emit(CoreEventMap.OnChunkDownloaded, details)
    }
}
