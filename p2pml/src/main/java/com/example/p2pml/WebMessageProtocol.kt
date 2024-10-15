package com.example.p2pml

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebMessagePortCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebMessageCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WebMessageProtocol(private val webView: WebView) {
    @SuppressLint("RequiresFeature")
    private val channels: Array<WebMessagePortCompat> = WebViewCompat.createWebMessageChannel(webView)
    private val segmentResponseCallbacks = mutableMapOf<String, CompletableDeferred<ByteArray>>()
    private val mutex = Mutex()
    private var incomingSegmentRequest: String? = null


    init {
        setupWebMessageCallback()
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("RequiresFeature")
    private fun setupWebMessageCallback() {
        channels[0].setWebMessageCallback(
            object : WebMessagePortCompat.WebMessageCallbackCompat() {
                override fun onMessage(port: WebMessagePortCompat, message: WebMessageCompat?) {
                    if(message?.type == WebMessageCompat.TYPE_STRING) {
                        Log.d("CoreWebView", "Received segment ID from  JS: ${message.data}")

                        val segmentId = message.data

                        incomingSegmentRequest = segmentId

                    }else if(message?.type == WebMessageCompat.TYPE_ARRAY_BUFFER) {
                        val arrayBuffer = message.arrayBuffer

                        if(incomingSegmentRequest == null) {
                            Log.d("CoreWebView", "Error: Received segment bytes without a segment ID")
                        }

                        GlobalScope.launch {
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
                }
            },
        )
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

    suspend fun requestSegmentBytes(segmentUrl: String): CompletableDeferred<ByteArray>{
        val deferred = CompletableDeferred<ByteArray>()

        addSegmentResponseCallback(segmentUrl, deferred)

        withContext(Dispatchers.Main) {
            sendSegmentRequest(segmentUrl)
        }

        return deferred
    }

    private fun sendSegmentRequest(segmentUrl: String){
        webView.evaluateJavascript("javascript:window.p2p.requestSegment('$segmentUrl');", null)
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
