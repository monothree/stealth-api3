package com.cosmos.stealth.services.reddit.data.remote

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class RedditCookieJar : CookieJar {

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val result = mutableListOf<Cookie>()
        cookieStore[url.host]?.let {
            synchronized(it) {
                result.addAll(it)
            }
        }
        if (url.host.contains("reddit.com")) {
            if (result.none { it.name == "over18" }) {
                result.add(OVER_18)
            }
        }
        return result
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val existing = cookieStore.computeIfAbsent(url.host) { mutableListOf() }
        synchronized(existing) {
            for (newCookie in cookies) {
                existing.removeAll { it.name == newCookie.name }
                existing.add(newCookie)
            }
        }
    }

    fun hasRequiredCookies(): Boolean {
        val cookies = cookieStore["www.reddit.com"] ?: cookieStore["reddit.com"] ?: return false
        synchronized(cookies) {
            return cookies.any { it.name == "token_v2" }
        }
    }

    companion object {
        private val OVER_18 = Cookie.Builder()
            .name("over18")
            .value("1")
            .domain("reddit.com")
            .path("/")
            .secure()
            .build()
    }
}
