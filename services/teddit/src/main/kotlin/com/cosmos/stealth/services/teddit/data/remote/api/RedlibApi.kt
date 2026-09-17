package com.cosmos.stealth.services.teddit.data.remote.api

import com.cosmos.stealth.core.network.util.extension.forward
import com.cosmos.stealth.services.reddit.data.model.Sort
import com.cosmos.stealth.services.reddit.data.model.TimeSorting
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

class RedlibApi(
    private val client: HttpClient
) {
    suspend fun getSubreddit(
        instance: String,
        subreddit: String,
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?,
        host: String?
    ): HttpResponse {
        val baseUrl = getBaseUrl(instance)
        val url = "$baseUrl/r/$subreddit/${sort.type}"
        return client.get(url) {
            forward(host, host == null)
            parameter("t", timeSorting?.type)
            parameter("after", after)
        }
    }

    suspend fun searchInSubreddit(
        instance: String,
        subreddit: String,
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?,
        host: String?
    ): HttpResponse {
        val baseUrl = getBaseUrl(instance)
        val url = "$baseUrl/r/$subreddit/search"
        return client.get(url) {
            forward(host, host == null)
            parameter("q", query)
            parameter("restrict_sr", "on")
            parameter("sort", sort?.type)
            parameter("t", timeSorting?.type)
            parameter("after", after)
        }
    }

    suspend fun getPost(
        instance: String,
        permalink: String,
        sort: Sort,
        host: String?
    ): HttpResponse {
        val baseUrl = getBaseUrl(instance)
        val cleanPermalink = permalink.removePrefix("/")
        val url = "$baseUrl/$cleanPermalink"
        return client.get(url) {
            forward(host, host == null)
            parameter("sort", sort.type)
        }
    }

    suspend fun getDeeperReplies(
        url: String,
        host: String?
    ): HttpResponse {
        return client.get(url) {
            forward(host, host == null)
        }
    }

    suspend fun getUserOverview(
        instance: String,
        user: String,
        host: String?
    ): HttpResponse {
        val baseUrl = getBaseUrl(instance)
        val url = "$baseUrl/user/$user"
        return client.get(url) {
            forward(host, host == null)
        }
    }

    suspend fun getUserPosts(
        instance: String,
        user: String,
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?,
        host: String?
    ): HttpResponse {
        val baseUrl = getBaseUrl(instance)
        val url = "$baseUrl/user/$user/submitted"
        return client.get(url) {
            forward(host, host == null)
            parameter("sort", sort.type)
            parameter("t", timeSorting?.type)
            parameter("after", after)
        }
    }

    suspend fun getUserComments(
        instance: String,
        user: String,
        sort: Sort,
        timeSorting: TimeSorting?,
        after: String?,
        host: String?
    ): HttpResponse {
        val baseUrl = getBaseUrl(instance)
        val url = "$baseUrl/user/$user/comments"
        return client.get(url) {
            forward(host, host == null)
            parameter("sort", sort.type)
            parameter("t", timeSorting?.type)
            parameter("after", after)
        }
    }

    suspend fun searchPost(
        instance: String,
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?,
        host: String?
    ): HttpResponse {
        val baseUrl = getBaseUrl(instance)
        val url = "$baseUrl/search"
        return client.get(url) {
            forward(host, host == null)
            parameter("q", query)
            parameter("sort", sort?.type)
            parameter("t", timeSorting?.type)
            parameter("after", after)
        }
    }

    suspend fun searchSubreddit(
        instance: String,
        query: String,
        sort: Sort?,
        timeSorting: TimeSorting?,
        after: String?,
        host: String?
    ): HttpResponse {
        val baseUrl = getBaseUrl(instance)
        val url = "$baseUrl/search"
        return client.get(url) {
            forward(host, host == null)
            parameter("q", query)
            parameter("type", "sr")
            parameter("sort", sort?.type)
            parameter("t", timeSorting?.type)
            parameter("after", after)
        }
    }

    private fun getBaseUrl(instance: String?): String {
        val target = instance?.takeIf { it.isNotBlank() } ?: DEFAULT_HOST
        return if (target.startsWith("http://") || target.startsWith("https://")) {
            target.removeSuffix("/")
        } else {
            "https://$target"
        }
    }

    companion object {
        val DEFAULT_HOST: String = System.getenv("REDLIB_INSTANCE")
            ?: System.getProperty("redlib.instance")
            ?: "redredred.065536.xyz"

        val BASE_URL: String = "https://$DEFAULT_HOST"
    }
}
