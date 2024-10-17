package com.example.p2pml

object Constants {
    const val DEFAULT_SERVER_PORT = 8080
    const val LOCALHOST = "127.0.0.1"
    const val HTTP_PREFIX = "http://"
    const val HTTPS_PREFIX = "https://"
    const val CORE_FILE_PATH = "p2pml/static/core.html"

    object QueryParams {
        const val SEGMENT = "?segment="
        const val MANIFEST = "?manifest="
        const val VARIANT_PLAYLIST = "?variantPlaylist="
    }

    object ManifestErrorMessages {
        const val MASTER_PLAYLIST_ERROR = "The provided URL does not point to a master playlist."
        const val MEDIA_PLAYLIST_ERROR = "The provided URL does not point to a media playlist."
    }

    object StreamTypes {
        const val MAIN = "main"
        const val SECONDARY = "secondary"
    }
}