package com.cosmos.stealth.server.util

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.fromFilePath
import io.ktor.server.application.call
import io.ktor.server.html.respondHtml
import io.ktor.server.plugins.swagger.SwaggerConfig
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.application
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.html.body
import kotlinx.html.div
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe

fun Route.swaggerUI(
    path: String,
    swaggerFile: String,
    title: String = "Swagger UI",
    block: SwaggerConfig.() -> Unit = {}
) {
    val config = SwaggerConfig().apply(block)

    route(path) {
        get(swaggerFile) {
            call.respond(HttpStatusCode.NotFound)
        }
        get {
            val fullPath = call.request.path().takeIf { it != "/" }.orEmpty()
            call.respondHtml {
                head {
                    title { +title }
                    link(
                        href = "${config.packageLocation}@${config.version}/swagger-ui.css",
                        rel = "stylesheet"
                    )
                }
                body {
                    div { id = "swagger-ui" }
                    script(src = "${config.packageLocation}@${config.version}/swagger-ui-bundle.js") {
                        attributes["crossorigin"] = "anonymous"
                    }

                    val src = "${config.packageLocation}@${config.version}/swagger-ui-standalone-preset.js"
                    script(src = src) {
                        attributes["crossorigin"] = "anonymous"
                    }

                    script {
                        unsafe {
                            +"""
                                window.onload = function() {
                                    window.ui = SwaggerUIBundle({
                                        url: '$fullPath/$swaggerFile',
                                        dom_id: '#swagger-ui',
                                        presets: [
                                            SwaggerUIBundle.presets.apis,
                                            SwaggerUIStandalonePreset
                                        ],
                                        layout: 'StandaloneLayout'
                                    });
                                }
                            """.trimIndent()
                        }
                    }

                    style {
                        unsafe { +"div.topbar { display: none; }" }
                    }
                }
            }
        }
    }
}
