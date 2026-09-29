package com.cosmos.stealth.core.network.util

import org.slf4j.LoggerFactory
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class ForwardProxyConfig(
    val scheme: String,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val proxyType: Proxy.Type = if (scheme.startsWith("socks", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
) {
    fun toMaskedString(): String {
        val authPart = when {
            username != null && password != null -> "$username:****@"
            username != null -> "$username@"
            else -> ""
        }
        return "$scheme://$authPart$host:$port"
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ForwardProxyConfig::class.java)
        private val isAuthenticatorInstalled = AtomicBoolean(false)

        fun resolve(serviceName: String? = null): ForwardProxyConfig? {
            val raw = findConfigString(serviceName) ?: return null
            return parse(raw)
        }

        private fun findConfigString(serviceName: String?): String? {
            // 1. Service-specific checks if serviceName provided (e.g. "reddit", "teddit")
            if (!serviceName.isNullOrBlank()) {
                val upper = serviceName.uppercase()
                val lower = serviceName.lowercase()

                val serviceEnv = System.getenv("${upper}_FORWARD_PROXY")
                    ?: System.getenv("${upper}_PROXY")
                if (!serviceEnv.isNullOrBlank()) return serviceEnv

                val serviceProp = System.getProperty("$lower.forward.proxy")
                    ?: System.getProperty("$lower.proxy")
                if (!serviceProp.isNullOrBlank()) return serviceProp
            }

            // 2. Global proxy configuration
            val globalEnv = System.getenv("FORWARD_PROXY")
                ?: System.getenv("HTTP_PROXY")
                ?: System.getenv("http_proxy")
                ?: System.getenv("ALL_PROXY")
                ?: System.getenv("all_proxy")
            if (!globalEnv.isNullOrBlank()) return globalEnv

            val globalProp = System.getProperty("forward.proxy")
                ?: System.getProperty("forward_proxy")
                ?: System.getProperty("http.proxy")
                ?: System.getProperty("all.proxy")
            if (!globalProp.isNullOrBlank()) return globalProp

            return null
        }

        fun parse(rawInput: String?): ForwardProxyConfig? {
            if (rawInput.isNullOrBlank()) return null
            val raw = rawInput.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            if (raw.isBlank()) return null

            try {
                val (scheme, remainder) = if (raw.contains("://")) {
                    raw.substringBefore("://").lowercase() to raw.substringAfter("://")
                } else {
                    "http" to raw
                }

                val (userInfo, hostPortPart) = if (remainder.contains("@")) {
                    remainder.substringBeforeLast("@") to remainder.substringAfterLast("@")
                } else {
                    null to remainder
                }

                var username: String? = null
                var password: String? = null
                if (userInfo != null) {
                    val rawUser = userInfo.substringBefore(":")
                    val rawPass = if (userInfo.contains(":")) userInfo.substringAfter(":") else null
                    username = URLDecoder.decode(rawUser, StandardCharsets.UTF_8.name())
                    password = rawPass?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
                }

                val cleanHostPort = hostPortPart.substringBefore("/")
                val (host, port) = if (cleanHostPort.startsWith("[") && cleanHostPort.contains("]")) {
                    val h = cleanHostPort.substring(1, cleanHostPort.indexOf("]"))
                    val p = cleanHostPort.substringAfter("]:", "").toIntOrNull()
                    h to (p ?: defaultPortFor(scheme))
                } else {
                    val h = cleanHostPort.substringBefore(":")
                    val p = cleanHostPort.substringAfter(":", "").toIntOrNull()
                    h to (p ?: defaultPortFor(scheme))
                }

                if (host.isBlank()) return null

                val proxyType = if (scheme.startsWith("socks", ignoreCase = true)) {
                    Proxy.Type.SOCKS
                } else {
                    Proxy.Type.HTTP
                }

                return ForwardProxyConfig(
                    scheme = scheme,
                    host = host,
                    port = port,
                    username = username,
                    password = password,
                    proxyType = proxyType
                )
            } catch (e: Exception) {
                logger.warn("Failed to parse FORWARD_PROXY configuration '{}': {}", rawInput, e.message)
                return null
            }
        }

        private fun defaultPortFor(scheme: String): Int {
            return when (scheme.lowercase()) {
                "socks", "socks5", "socks5h", "socks4", "socks4a" -> 1080
                "https" -> 443
                else -> 8080
            }
        }

        internal fun ensureAuthenticatorRegistered() {
            if (isAuthenticatorInstalled.compareAndSet(false, true)) {
                Authenticator.setDefault(ProxyAuthenticatorRegistry)
            }
        }
    }
}

object ProxyAuthenticatorRegistry : Authenticator() {
    private val credentials = ConcurrentHashMap<String, PasswordAuthentication>()
    private val defaultAuth = AtomicReference<PasswordAuthentication?>(null)

    fun register(host: String, port: Int, user: String, pass: String) {
        val auth = PasswordAuthentication(user, pass.toCharArray())
        credentials["$host:$port"] = auth
        credentials[host] = auth
        defaultAuth.set(auth)
    }

    fun clearForTest() {
        credentials.clear()
        defaultAuth.set(null)
    }

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        if (requestorType == RequestorType.PROXY) {
            val host = requestingHost
            val port = requestingPort
            if (host != null) {
                credentials["$host:$port"]?.let { return it }
                credentials[host]?.let { return it }
            }
            return defaultAuth.get()
        }
        return super.getPasswordAuthentication()
    }
}
