package com.example.p2pml

object Constants {
    const val MICROSECONDS_IN_SECOND = 1_000_000
    const val MPEGURL_CONTENT_TYPE = "application/vnd.apple.mpegurl"
    const val DEFAULT_SERVER_PORT = 8080
    const val LOCALHOST = "127.0.0.1"
    const val HTTP_PREFIX = "http://"
    const val HTTPS_PREFIX = "https://"
    const val CORE_FILE_PATH = "p2pml/static/"

    object QueryParams {
        const val SEGMENT = "?segment="
        const val MANIFEST = "?manifest="
    }

    object StreamTypes {
        const val MAIN = "main"
        const val SECONDARY = "secondary"
    }
}