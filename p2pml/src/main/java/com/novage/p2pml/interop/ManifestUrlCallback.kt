package com.novage.p2pml.interop

fun interface ManifestUrlCallback {
    fun onManifestUrlReceived(internalManifestUrl: String)
}
