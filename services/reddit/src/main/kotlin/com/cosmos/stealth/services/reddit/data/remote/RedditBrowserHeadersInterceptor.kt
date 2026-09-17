package com.cosmos.stealth.services.reddit.data.remote

import okhttp3.Interceptor
import okhttp3.Response

class RedditBrowserHeadersInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val userAgent = original.header("User-Agent")
        if (userAgent.isNullOrBlank() || userAgent.startsWith("okhttp", ignoreCase = true)) {
            builder.header("User-Agent", BROWSER_USER_AGENT)
        }

        if (original.header("Accept-Language").isNullOrBlank()) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
        }

        if (original.header("Sec-Ch-Ua").isNullOrBlank()) {
            builder.header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
            builder.header("Sec-Ch-Ua-Mobile", "?0")
            builder.header("Sec-Ch-Ua-Platform", "\"Linux\"")
        }

        return chain.proceed(builder.build())
    }

    companion object {
        const val BROWSER_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
