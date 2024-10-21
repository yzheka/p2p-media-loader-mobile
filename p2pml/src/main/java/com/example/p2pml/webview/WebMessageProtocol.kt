package com.example.p2pml.webview

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebMessageCompat
import com.example.p2pml.SegmentRequest
import com.example.p2pml.utils.Utils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

internal class WebMessageProtocol(private val webView: WebView, private val coroutineScope: CoroutineScope) {
    @SuppressLint("RequiresFeature")
    private val channels: Array<WebMessagePortCompat> = WebViewCompat.createWebMessageChannel(webView)
    private val segmentResponseCallbacks = mutableMapOf<String, CompletableDeferred<ByteArray>>()
    private val mutex = Mutex()
    private var incomingSegmentRequest: String? = null


    init {
        setupWebMessageCallback()
    }

    @SuppressLint("RequiresFeature")
    private fun setupWebMessageCallback() {
        channels[0].setWebMessageCallback(
            object : WebMessagePortCompat.WebMessageCallbackCompat() {
                override fun onMessage(port: WebMessagePortCompat, message: WebMessageCompat?) {
                    when (message?.type) {
                        WebMessageCompat.TYPE_ARRAY_BUFFER -> {
                            handleSegmentIdBytes(message.arrayBuffer)
                        }
                        WebMessageCompat.TYPE_STRING -> {
                            handleSegmentIdMessage(message.data!!)
                        }
                    }
                }
            },
        )
    }

    private fun handleSegmentIdBytes(arrayBuffer: ByteArray) {
        if(incomingSegmentRequest == null) {
            throw IllegalStateException("Received segment bytes without a segment ID")
        }

        coroutineScope.launch {
            val deferred = getSegmentResponseCallback(incomingSegmentRequest!!)
            if (deferred != null) {
                deferred.complete(arrayBuffer)
                Log.d("CoreWebView", "Completed deferred for segment ID: $incomingSegmentRequest")
            } else {
                Log.d("CoreWebView", "Error: No deferred found for segment ID: $incomingSegmentRequest")
            }

            removeSegmentResponseCallback(incomingSegmentRequest!!)
            incomingSegmentRequest = null
        }
    }

    private fun handleSegmentIdMessage(segmentId: String) {
        Log.d("CoreWebView", "Received segment ID from  JS: $segmentId")
        incomingSegmentRequest = segmentId
    }

    @SuppressLint("RequiresFeature")
    fun sendInitialMessage() {
        val initialMessage = WebMessageCompat("", arrayOf(channels[1]))
        WebViewCompat.postWebMessage(
            webView,
            initialMessage,
            Uri.parse("*")
        )
    }

    suspend fun requestSegmentBytes(segmentUrl: String, currentPlayPosition: Float, currentPlaySpeed: Float): CompletableDeferred<ByteArray>{
        val deferred = CompletableDeferred<ByteArray>()
        val segmentRequest = SegmentRequest(segmentUrl, currentPlayPosition, currentPlaySpeed)
        val jsonRequest = Json.encodeToString(segmentRequest)

        addSegmentResponseCallback(segmentUrl, deferred)

        withContext(Dispatchers.Main) {
            sendSegmentRequest(jsonRequest)
        }

        return deferred
    }

    private fun sendSegmentRequest(segmentUrl: String){
        Utils.runOnUiThread {
            webView.evaluateJavascript("javascript:window.p2p.requestSegment('$segmentUrl');", null)
        }
    }

    private suspend fun addSegmentResponseCallback(segmentId: String, deferred: CompletableDeferred<ByteArray>) {
        mutex.withLock {
            segmentResponseCallbacks[segmentId] = deferred
        }
    }

    private suspend fun getSegmentResponseCallback(segmentId: String): CompletableDeferred<ByteArray>? {
        return mutex.withLock {
            segmentResponseCallbacks[segmentId]
        }
    }

    private suspend fun removeSegmentResponseCallback(segmentId: String) {
        mutex.withLock {
            segmentResponseCallbacks.remove(segmentId)
        }
    }
}
