package com.cosmos.stealth.core.network.util.extension

import org.slf4j.LoggerFactory
import com.cosmos.stealth.core.network.util.ForwardProxyConfig
import com.cosmos.stealth.core.network.util.ProxyAuthenticatorRegistry
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.Route
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.nio.file.Paths

private const val MAX_SIZE = 50L * 1024L * 1024L // 50MiB
private val logger = LoggerFactory.getLogger("OkHttpExt")

fun OkHttpClient.Builder.cache(module: String, maxSize: Long = MAX_SIZE): OkHttpClient.Builder {
    return cache(Cache(Files.createDirectories(Paths.get("cache/$module")).toFile(), maxSize))
}

fun OkHttpClient.Builder.forwardProxy(serviceName: String? = null): OkHttpClient.Builder {
    val config = ForwardProxyConfig.resolve(serviceName) ?: return this

    logger.info("Configuring forward proxy for '{}': {}", serviceName ?: "global", config.toMaskedString())

    proxy(Proxy(config.proxyType, InetSocketAddress(config.host, config.port)))

    if (!config.username.isNullOrEmpty() && !config.password.isNullOrEmpty()) {
        ForwardProxyConfig.ensureAuthenticatorRegistered()
        ProxyAuthenticatorRegistry.register(config.host, config.port, config.username, config.password)

        if (config.proxyType == Proxy.Type.HTTP) {
            proxyAuthenticator { _: Route?, response: Response ->
                if (response.request.header("Proxy-Authorization") != null) {
                    // Give up if already attempted to authenticate
                    return@proxyAuthenticator null
                }
                val credential = Credentials.basic(config.username, config.password)
                response.request.newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
        }
    }

    return this
}
