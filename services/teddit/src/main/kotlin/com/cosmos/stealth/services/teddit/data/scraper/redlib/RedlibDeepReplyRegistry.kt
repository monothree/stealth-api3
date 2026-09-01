package com.cosmos.stealth.services.teddit.data.scraper.redlib

import java.util.concurrent.atomic.AtomicLong

class RedlibDeepReplyRegistry {

    private val counter = AtomicLong(0)
    private val map = object : LinkedHashMap<String, String>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    @Synchronized
    fun register(absoluteUrl: String): String {
        val token = "$PREFIX${counter.incrementAndGet()}"
        map[token] = absoluteUrl
        return token
    }

    @Synchronized
    fun consume(token: String): String? = map[token]

    fun isToken(value: String): Boolean = value.startsWith(PREFIX)

    companion object {
        const val PREFIX = "redlib_"
        private const val MAX_ENTRIES = 256
    }
}
