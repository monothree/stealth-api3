package com.cosmos.stealth.services.reddit.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.Cookie

@JsonClass(generateAdapter = true)
data class StoredCookie(
    @Json(name = "name")
    val name: String,

    @Json(name = "value")
    val value: String,

    @Json(name = "domain")
    val domain: String = "reddit.com",

    @Json(name = "path")
    val path: String = "/",

    @Json(name = "expiresAt")
    val expiresAt: Long = 253402300799999L,

    @Json(name = "secure")
    val secure: Boolean = true,

    @Json(name = "httpOnly")
    val httpOnly: Boolean = false,

    @Json(name = "hostOnly")
    val hostOnly: Boolean = false
) {
    fun toOkHttpCookie(): Cookie {
        val cleanDomain = domain.removePrefix(".")
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
        if (hostOnly) {
            builder.hostOnlyDomain(cleanDomain)
        } else {
            builder.domain(cleanDomain)
        }
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }

    companion object {
        fun fromOkHttpCookie(cookie: Cookie): StoredCookie {
            return StoredCookie(
                name = cookie.name,
                value = cookie.value,
                domain = cookie.domain,
                path = cookie.path,
                expiresAt = cookie.expiresAt,
                secure = cookie.secure,
                httpOnly = cookie.httpOnly,
                hostOnly = cookie.hostOnly
            )
        }
    }
}
