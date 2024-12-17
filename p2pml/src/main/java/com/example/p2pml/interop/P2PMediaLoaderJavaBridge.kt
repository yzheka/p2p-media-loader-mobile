package com.example.p2pml.interop

import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import com.example.p2pml.P2PMediaLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A helper class that helps to integrate P2P Media Loader with Java code.
 */
class P2PMediaLoaderJavaBridge {
    private var coroutineScope: CoroutineScope? = null

    /**
     * Asynchronously retrieves a modified manifest URL that enables P2P functionality.
     *
     * @param p2pMediaLoader The P2P Media Loader instance that will handle the manifest transformation
     * @param manifestUrl The original HLS manifest URL to be transformed
     * @param callback A callback that will receive the modified manifest URL
     */
    @OptIn(UnstableApi::class)
    fun getManifestUrlAsync(
        p2pMediaLoader: P2PMediaLoader,
        manifestUrl: String,
        callback: ManifestUrlCallback,
    ) {
        coroutineScope = CoroutineScope(Dispatchers.Main)
        coroutineScope?.launch {
            try {
                val internalManifestUrl = p2pMediaLoader.getManifestUrl(manifestUrl)
                callback.onManifestUrlReceived(internalManifestUrl)
            } catch (e: Exception) {
                Log.d("P2PMediaLoaderHelper", "Error getting manifest URL: ${e.message}")
            }
        }
    }

    /**
     * Cancels all pending operations and releases resources.
     */
    fun destroy() {
        coroutineScope?.cancel()
        coroutineScope = null
    }
}
