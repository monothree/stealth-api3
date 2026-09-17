package com.cosmos.stealth.server

import com.cosmos.stealth.server.data.config.configureAccessLogging
import com.cosmos.stealth.server.data.config.configureCaching
import com.cosmos.stealth.server.data.config.configureContentNegotiation
import com.cosmos.stealth.server.data.config.configureDependencyInjection
import com.cosmos.stealth.server.data.config.configureForwardedHeaders
import com.cosmos.stealth.server.data.config.configureStatusPage
import com.cosmos.stealth.server.data.route.configureRouting
import io.ktor.server.application.Application
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main(args: Array<String>) {
    embeddedServer(Netty, commandLineEnvironment(args)).start(wait = true)
}

fun Application.main() {
    configureDependencyInjection()
    configureAccessLogging()
    configureCaching()
    configureContentNegotiation()
    configureForwardedHeaders()
    configureStatusPage()
    configureRouting()
}
