package com.example.p2pml.server

import com.example.p2pml.Constants.CORE_FILE_PATH
import com.example.p2pml.Constants.CUSTOM_FILE_PATH
import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

internal class ServerRoutes(
    private val manifestHandler: ManifestHandler,
    private val segmentHandler: SegmentHandler,
    private val customP2pmlImplementationPath: String? = null,
) {
    fun setup(routing: Routing) {
        routing {
            get("/") {
                when {
                    call.parameters["manifest"] != null ->
                        manifestHandler.handleManifestRequest(call)
                    call.parameters["segment"] != null -> segmentHandler.handleSegmentRequest(call)

                    else ->
                        call.respondText(
                            "Missing required parameter",
                            status = io.ktor.http.HttpStatusCode.BadRequest,
                        )
                }
            }

            staticResources("/static", CORE_FILE_PATH) {
                default("index.html")
            }

            if (customP2pmlImplementationPath != null) {
                staticResources(CUSTOM_FILE_PATH, customP2pmlImplementationPath) {
                    default("index.html")
                }
            }
        }
    }
}
