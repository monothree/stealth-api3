package com.cosmos.stealth.server.data.config

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallSetup
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory

private val START_TIME_KEY = AttributeKey<Long>("AccessLogStartTime")

val AccessLogging = createApplicationPlugin(name = "AccessLogging") {
    val logger = LoggerFactory.getLogger("com.cosmos.stealth.server.AccessLog")

    on(CallSetup) { call ->
        call.attributes.put(START_TIME_KEY, System.currentTimeMillis())
    }

    on(ResponseSent) { call ->
        val status = call.response.status() ?: return@on
        val path = call.request.path()

        // Only log access to VALID endpoints.
        // Do not log 404s, web crawlers/scanners, or forced-404 stealth-api.yaml.
        if (status != HttpStatusCode.NotFound && isValidEndpoint(path)) {
            val startTime = call.attributes.getOrNull(START_TIME_KEY)
            val durationStr = if (startTime != null) " in ${System.currentTimeMillis() - startTime}ms" else ""
            val method = call.request.httpMethod.value
            val uri = call.request.uri
            val clientIp = call.request.origin.remoteHost

            logger.info("[{}] {} {} from {}{}", status, method, uri, clientIp, durationStr)
        }
    }
}

private fun isValidEndpoint(path: String): Boolean {
    if (path == "/stealth-api.yaml") return false
    return path == "/" ||
        path.startsWith("/api/v1/") ||
        path.startsWith("/en/") ||
        path == "/favicon.ico"
}

fun Application.configureAccessLogging() {
    install(AccessLogging)
}
