package com.example.p2pml.webview

import android.content.Context
import kotlinx.coroutines.CoroutineScope

class WebViewManager(
    context: Context,
    coroutineScope: CoroutineScope,
    onPageLoadFinished: () -> Unit
) {
    private val coreWebView = CoreWebView(context, coroutineScope, onPageLoadFinished)

    fun loadWebView(url: String) {
        coreWebView.loadUrl(url)
    }

    fun setUpPlaybackInfoCallback(callback: () -> Pair<Float, Float>) {
        coreWebView.setUpPlaybackInfoCallback(callback)
    }

    suspend fun requestSegmentBytes(segmentUrl: String): kotlinx.coroutines.Deferred<ByteArray> {
        return coreWebView.requestSegmentBytes(segmentUrl)
    }

    fun sendInitialMessage() {
        coreWebView.sendInitialMessage()
    }

    fun sendAllStreams(streamsJSON: String) {
        coreWebView.sendAllStreams(streamsJSON)
    }

    fun sendStream(streamJSON: String) {
        coreWebView.sendStream(streamJSON)
    }

    fun setManifestUrl(manifestUrl: String) {
        coreWebView.setManifestUrl(manifestUrl)
    }

    fun destroy() {
        coreWebView.destroy()
    }
}