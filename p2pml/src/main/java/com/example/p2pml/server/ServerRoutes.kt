package com.example.p2pml.server

import io.ktor.server.application.call
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

internal class ServerRoutes(
    private val manifestHandler: ManifestHandler,
    private val segmentHandler: SegmentHandler
) {
    fun setup(routing: Routing) {
        routing {
            get("/") {
                when {
                    call.parameters["manifest"] != null -> manifestHandler.handleManifestRequest(
                        call
                    )

                    call.parameters["segment"] != null -> segmentHandler.handleSegmentRequest(call)

                    else -> call.respondText(
                        "Missing required parameter",
                        status = io.ktor.http.HttpStatusCode.BadRequest
                    )
                }
            }

            staticResources("/p2pml/static", "p2pml/static") {
                default("core.html")
            }
        }
    }
}