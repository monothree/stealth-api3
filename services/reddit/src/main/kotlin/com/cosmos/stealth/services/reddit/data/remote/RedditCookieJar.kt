package com.cosmos.stealth.services.reddit.data.remote

import com.cosmos.stealth.core.common.util.extension.getLogger
import com.cosmos.stealth.services.reddit.data.model.StoredCookie
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RedditCookieJar(
    private val cookieFilePath: String? = null
) : CookieJar {

    private val logger = getLogger()
    private val moshi = Moshi.Builder().build()
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieFile: File by lazy {
        val path = cookieFilePath
            ?: System.getenv("REDDIT_COOKIE_FILE")
            ?: System.getProperty("reddit.cookie.file")
            ?: (if (File("data").isDirectory) "data/reddit_cookies.json" else "reddit_cookies.json")
        File(path)
    }

    init {
        loadFromDisk()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val result = mutableListOf<Cookie>()
        val host = url.host
        cookieStore[host]?.let {
            synchronized(it) {
                result.addAll(it)
            }
        }
        if (host.endsWith("reddit.com") && host != "reddit.com") {
            cookieStore["reddit.com"]?.let {
                synchronized(it) {
                    for (c in it) {
                        if (result.none { existing -> existing.name == c.name }) {
                            result.add(c)
                        }
                    }
                }
            }
        }
        if (host.contains("reddit.com")) {
            if (result.none { it.name == "over18" }) {
                result.add(OVER_18)
            }
        }
        return result
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host

        // Do not save cookies from old.reddit.com or non-main domains
        if (host.equals("old.reddit.com", ignoreCase = true) || !host.endsWith("reddit.com")) {
            return
        }
        if (host != "reddit.com" && host != "www.reddit.com") {
            return
        }

        val existing = cookieStore.computeIfAbsent(host) { mutableListOf() }
        var modified = false

        synchronized(existing) {
            for (newCookie in cookies) {
                val oldIndex = existing.indexOfFirst { it.name == newCookie.name }
                if (oldIndex >= 0) {
                    if (existing[oldIndex].value != newCookie.value) {
                        existing[oldIndex] = newCookie
                        modified = true
                    }
                } else {
                    existing.add(newCookie)
                    modified = true
                }
            }
        }

        if (host == "www.reddit.com" || host == "reddit.com") {
            val peerHost = if (host == "www.reddit.com") "reddit.com" else "www.reddit.com"
            val peerList = cookieStore.computeIfAbsent(peerHost) { mutableListOf() }
            synchronized(peerList) {
                for (newCookie in cookies) {
                    peerList.removeAll { it.name == newCookie.name }
                    peerList.add(newCookie)
                }
            }
        }

        if (modified) {
            saveToDisk()
        }
    }

    fun hasRequiredCookies(): Boolean {
        val cookies = cookieStore["www.reddit.com"] ?: cookieStore["reddit.com"] ?: return false
        synchronized(cookies) {
            return cookies.any { it.name == "token_v2" && it.value.isNotBlank() }
        }
    }

    private fun loadFromDisk() {
        val file = cookieFile
        if (!file.exists() || !file.isFile || file.length() == 0L) {
            logger.info("Reddit cookie file '{}' not found or empty. Fresh cookies will be requested if needed.", file.absolutePath)
            return
        }

        try {
            val content = file.readText().trim()
            val loaded = mutableListOf<Cookie>()

            if (content.startsWith("[")) {
                val listType = Types.newParameterizedType(List::class.java, StoredCookie::class.java)
                val adapter: JsonAdapter<List<StoredCookie>> = moshi.adapter(listType)
                adapter.fromJson(content)?.forEach { loaded.add(it.toOkHttpCookie()) }
            } else if (content.startsWith("{")) {
                val mapType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
                val adapter: JsonAdapter<Map<String, String>> = moshi.adapter(mapType)
                adapter.fromJson(content)?.forEach { (k, v) ->
                    loaded.add(createRedditCookie(k, v))
                }
            } else {
                content.lines().forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isNotEmpty() && !line.startsWith("#")) {
                        if (line.contains("=") && !line.contains("\t")) {
                            val key = line.substringBefore("=").trim()
                            val value = line.substringAfter("=").trim()
                            loaded.add(createRedditCookie(key, value))
                        } else if (line.contains("\t")) {
                            val parts = line.split("\t")
                            if (parts.size >= 7) {
                                val domain = parts[0]
                                val path = parts[2]
                                val secure = parts[3].equals("TRUE", ignoreCase = true)
                                val expires = parts[4].toLongOrNull()?.let { it * 1000 } ?: 253402300799999L
                                val name = parts[5]
                                val value = parts[6]
                                loaded.add(
                                    Cookie.Builder()
                                        .name(name)
                                        .value(value)
                                        .domain(domain.removePrefix("."))
                                        .path(path)
                                        .expiresAt(expires)
                                        .apply { if (secure) secure() }
                                        .build()
                                )
                            }
                        }
                    }
                }
            }

            if (loaded.isNotEmpty()) {
                for (cookie in loaded) {
                    val host = cookie.domain.removePrefix(".")
                    val list = cookieStore.computeIfAbsent(host) { mutableListOf() }
                    synchronized(list) {
                        list.removeAll { it.name == cookie.name }
                        list.add(cookie)
                    }
                    if (host == "reddit.com") {
                        val wwwList = cookieStore.computeIfAbsent("www.reddit.com") { mutableListOf() }
                        synchronized(wwwList) {
                            wwwList.removeAll { it.name == cookie.name }
                            wwwList.add(cookie)
                        }
                    }
                }
                logger.info("Loaded {} Reddit cookies from '{}' (hasRequiredCookies: {})", loaded.size, file.absolutePath, hasRequiredCookies())
            }
        } catch (e: Exception) {
            logger.error("Failed to load Reddit cookies from '{}': {}", file.absolutePath, e.message, e)
        }
    }

    private fun saveToDisk() {
        try {
            val file = cookieFile
            file.parentFile?.mkdirs()

            val allCookies = mutableListOf<StoredCookie>()
            val seen = mutableSetOf<String>()

            for ((_, cookies) in cookieStore) {
                synchronized(cookies) {
                    for (cookie in cookies) {
                        val key = "${cookie.domain}:${cookie.name}"
                        if (seen.add(key)) {
                            allCookies.add(StoredCookie.fromOkHttpCookie(cookie))
                        }
                    }
                }
            }

            val listType = Types.newParameterizedType(List::class.java, StoredCookie::class.java)
            val adapter: JsonAdapter<List<StoredCookie>> = moshi.adapter<List<StoredCookie>>(listType).indent("  ")
            val json = adapter.toJson(allCookies)

            val tempFile = File(file.parentFile ?: File("."), "${file.name}.tmp")
            tempFile.writeText(json)
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
            logger.info("Persisted {} Reddit cookies to '{}'", allCookies.size, file.absolutePath)
        } catch (e: Exception) {
            logger.error("Failed to save Reddit cookies to disk: {}", e.message, e)
        }
    }

    private fun createRedditCookie(name: String, value: String): Cookie {
        return Cookie.Builder()
            .name(name)
            .value(value)
            .domain("reddit.com")
            .path("/")
            .secure()
            .build()
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
