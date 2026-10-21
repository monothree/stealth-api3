package com.cosmos.stealth.server.data.manager

import com.cosmos.stealth.core.common.util.extension.getLogger
import com.cosmos.stealth.core.model.api.Community
import com.cosmos.stealth.core.model.api.CommunityInfo
import com.cosmos.stealth.core.model.api.Feed
import com.cosmos.stealth.core.model.api.Feedable
import com.cosmos.stealth.core.model.api.Post
import com.cosmos.stealth.core.model.api.SearchResults
import com.cosmos.stealth.core.model.api.ServiceName
import com.cosmos.stealth.core.model.api.User
import com.cosmos.stealth.core.model.api.UserInfo
import com.cosmos.stealth.core.model.data.CommunityInfoRequest
import com.cosmos.stealth.core.model.data.CommunityRequest
import com.cosmos.stealth.core.model.data.MoreContentRequest
import com.cosmos.stealth.core.model.data.PostRequest
import com.cosmos.stealth.core.model.data.SearchRequest
import com.cosmos.stealth.core.model.data.SingleFeedRequest
import com.cosmos.stealth.core.model.data.UserInfoRequest
import com.cosmos.stealth.core.model.data.UserRequest
import com.cosmos.stealth.core.network.util.Resource
import com.cosmos.stealth.services.base.data.ServiceGateway
import com.cosmos.stealth.services.base.util.extension.isSuccess
import com.cosmos.stealth.services.reddit.RedditGateway
import com.cosmos.stealth.services.reddit.util.RedditErrorLogger
import com.cosmos.stealth.services.teddit.TedditGateway
import com.cosmos.stealth.services.teddit.data.remote.api.RedlibApi
import com.cosmos.stealth.services.teddit.data.scraper.redlib.RedlibDeepReplyRegistry

class FallbackRedditGateway(
    private val redditGateway: RedditGateway,
    private val tedditGateway: TedditGateway
) : ServiceGateway {

    private val logger = getLogger()

    override val name: ServiceName = ServiceName.reddit

    private fun isOldReddit(service: com.cosmos.stealth.core.model.api.Service): Boolean {
        val instance = service.instance
        return instance != null && instance.contains("old.reddit.com", ignoreCase = true)
    }

    private val isForceRedlib: Boolean
        get() {
            val env = System.getenv("FORCE_REDLIB")
                ?: System.getenv("REDLIB_ONLY")
                ?: System.getProperty("force.redlib")
                ?: System.getProperty("redlib.only")
            return env?.trim()?.equals("true", ignoreCase = true) == true ||
                env?.trim() == "1"
        }

    private fun shouldRouteDirectlyToRedlib(service: com.cosmos.stealth.core.model.api.Service): Boolean {
        return isForceRedlib || isOldReddit(service)
    }

    private fun logDirectRoute(action: String) {
        if (isForceRedlib) {
            logger.info("FORCE_REDLIB enabled; routing {} directly to Redlib ({})", action, RedlibApi.DEFAULT_HOST)
        } else {
            logger.info("Reddit Web Scraping (old.reddit.com) requested; routing {} directly to Redlib ({})", action, RedlibApi.DEFAULT_HOST)
        }
    }

    private fun getRedlibService(service: com.cosmos.stealth.core.model.api.Service): com.cosmos.stealth.core.model.api.Service {
        val instance = service.instance
        val targetInstance = if (instance.isNullOrBlank() || instance.contains("reddit.com", ignoreCase = true)) {
            RedlibApi.DEFAULT_HOST
        } else {
            instance
        }
        return service.copy(
            name = service.name,
            instance = targetInstance
        )
    }

    private fun Resource<*>.extractCodeAndMessage(): Pair<Int?, String?> {
        return when (this) {
            is Resource.Error -> code to message
            is Resource.Exception -> 500 to throwable.message
            else -> null to null
        }
    }

    override suspend fun getFeed(singleFeedRequest: SingleFeedRequest): Feed {
        if (shouldRouteDirectlyToRedlib(singleFeedRequest.service)) {
            logDirectRoute("getFeed")
            return tedditGateway.getFeed(singleFeedRequest.copy(service = getRedlibService(singleFeedRequest.service)))
        }

        val feed = redditGateway.getFeed(singleFeedRequest)
        val status = feed.status.firstOrNull()

        if (feed.items.isNotEmpty() || (status != null && status.isSuccess)) {
            RedditErrorLogger.recordSuccess()
            return feed
        }

        RedditErrorLogger.logFallback("getFeed", status?.code, status?.message, RedlibApi.DEFAULT_HOST)
        return tedditGateway.getFeed(singleFeedRequest.copy(service = getRedlibService(singleFeedRequest.service)))
    }

    override suspend fun getCommunity(communityRequest: CommunityRequest): Resource<Community> {
        if (shouldRouteDirectlyToRedlib(communityRequest.service)) {
            logDirectRoute("getCommunity")
            return tedditGateway.getCommunity(communityRequest.copy(service = getRedlibService(communityRequest.service)))
        }

        val result = redditGateway.getCommunity(communityRequest)
        if (result is Resource.Success) {
            RedditErrorLogger.recordSuccess()
            return result
        }

        val (code, message) = result.extractCodeAndMessage()
        RedditErrorLogger.logFallback("getCommunity", code, message, RedlibApi.DEFAULT_HOST)
        return tedditGateway.getCommunity(communityRequest.copy(service = getRedlibService(communityRequest.service)))
    }

    override suspend fun getCommunityInfo(communityInfoRequest: CommunityInfoRequest): Resource<CommunityInfo> {
        if (shouldRouteDirectlyToRedlib(communityInfoRequest.service)) {
            logDirectRoute("getCommunityInfo")
            return tedditGateway.getCommunityInfo(communityInfoRequest.copy(service = getRedlibService(communityInfoRequest.service)))
        }

        val result = redditGateway.getCommunityInfo(communityInfoRequest)
        if (result is Resource.Success) {
            RedditErrorLogger.recordSuccess()
            return result
        }

        val (code, message) = result.extractCodeAndMessage()
        RedditErrorLogger.logFallback("getCommunityInfo", code, message, RedlibApi.DEFAULT_HOST)
        return tedditGateway.getCommunityInfo(communityInfoRequest.copy(service = getRedlibService(communityInfoRequest.service)))
    }

    override suspend fun getUser(userRequest: UserRequest): Resource<User> {
        if (shouldRouteDirectlyToRedlib(userRequest.service)) {
            logDirectRoute("getUser")
            return tedditGateway.getUser(userRequest.copy(service = getRedlibService(userRequest.service)))
        }

        val result = redditGateway.getUser(userRequest)
        if (result is Resource.Success) {
            RedditErrorLogger.recordSuccess()
            return result
        }

        val (code, message) = result.extractCodeAndMessage()
        RedditErrorLogger.logFallback("getUser", code, message, RedlibApi.DEFAULT_HOST)
        return tedditGateway.getUser(userRequest.copy(service = getRedlibService(userRequest.service)))
    }

    override suspend fun getUserInfo(userInfoRequest: UserInfoRequest): Resource<UserInfo> {
        if (shouldRouteDirectlyToRedlib(userInfoRequest.service)) {
            logDirectRoute("getUserInfo")
            return tedditGateway.getUserInfo(userInfoRequest.copy(service = getRedlibService(userInfoRequest.service)))
        }

        val result = redditGateway.getUserInfo(userInfoRequest)
        if (result is Resource.Success) {
            RedditErrorLogger.recordSuccess()
            return result
        }

        val (code, message) = result.extractCodeAndMessage()
        RedditErrorLogger.logFallback("getUserInfo", code, message, RedlibApi.DEFAULT_HOST)
        return tedditGateway.getUserInfo(userInfoRequest.copy(service = getRedlibService(userInfoRequest.service)))
    }

    override suspend fun getPost(postRequest: PostRequest): Resource<Post> {
        if (shouldRouteDirectlyToRedlib(postRequest.service)) {
            logDirectRoute("getPost")
            return tedditGateway.getPost(postRequest.copy(service = getRedlibService(postRequest.service)))
        }

        val result = redditGateway.getPost(postRequest)
        if (result is Resource.Success) {
            RedditErrorLogger.recordSuccess()
            return result
        }

        val (code, message) = result.extractCodeAndMessage()
        RedditErrorLogger.logFallback("getPost", code, message, RedlibApi.DEFAULT_HOST)
        return tedditGateway.getPost(postRequest.copy(service = getRedlibService(postRequest.service)))
    }

    override suspend fun getMoreContent(moreContentRequest: MoreContentRequest): Resource<List<Feedable>> {
        val firstToken = moreContentRequest.appendable.content.firstOrNull()?.trim()
        val isRedlibToken = firstToken?.startsWith(RedlibDeepReplyRegistry.PREFIX) == true
        if (isRedlibToken || shouldRouteDirectlyToRedlib(moreContentRequest.appendable.service)) {
            logDirectRoute("getMoreContent")
            val fallbackAppendable = moreContentRequest.appendable.copy(service = getRedlibService(moreContentRequest.appendable.service))
            return tedditGateway.getMoreContent(moreContentRequest.copy(appendable = fallbackAppendable))
        }

        val result = redditGateway.getMoreContent(moreContentRequest)
        if (result is Resource.Success) {
            RedditErrorLogger.recordSuccess()
            return result
        }

        val (code, message) = result.extractCodeAndMessage()
        RedditErrorLogger.logFallback("getMoreContent", code, message, RedlibApi.DEFAULT_HOST)
        val fallbackAppendable = moreContentRequest.appendable.copy(service = getRedlibService(moreContentRequest.appendable.service))
        return tedditGateway.getMoreContent(moreContentRequest.copy(appendable = fallbackAppendable))
    }

    override suspend fun getSearchResults(searchRequest: SearchRequest): Resource<SearchResults> {
        if (shouldRouteDirectlyToRedlib(searchRequest.service)) {
            logDirectRoute("getSearchResults")
            return tedditGateway.getSearchResults(searchRequest.copy(service = getRedlibService(searchRequest.service)))
        }

        val result = redditGateway.getSearchResults(searchRequest)
        if (result is Resource.Success) {
            RedditErrorLogger.recordSuccess()
            return result
        }

        val (code, message) = result.extractCodeAndMessage()
        RedditErrorLogger.logFallback("getSearchResults", code, message, RedlibApi.DEFAULT_HOST)
        return tedditGateway.getSearchResults(searchRequest.copy(service = getRedlibService(searchRequest.service)))
    }
}
