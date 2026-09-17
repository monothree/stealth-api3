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
import com.cosmos.stealth.services.teddit.TedditGateway
import com.cosmos.stealth.services.teddit.data.remote.api.RedlibApi

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

    override suspend fun getFeed(singleFeedRequest: SingleFeedRequest): Feed {
        if (isOldReddit(singleFeedRequest.service)) {
            logger.info("Reddit Web Scraping (old.reddit.com) requested; routing directly to Redlib ({})", RedlibApi.DEFAULT_HOST)
            return tedditGateway.getFeed(singleFeedRequest.copy(service = getRedlibService(singleFeedRequest.service)))
        }

        val feed = redditGateway.getFeed(singleFeedRequest)
        val status = feed.status.firstOrNull()

        if (feed.items.isNotEmpty() || (status != null && status.isSuccess)) {
            return feed
        }

        logger.warn("Reddit API getFeed failed (status: {}), falling back to Redlib ({})", status?.code, RedlibApi.DEFAULT_HOST)
        return tedditGateway.getFeed(singleFeedRequest.copy(service = getRedlibService(singleFeedRequest.service)))
    }

    override suspend fun getCommunity(communityRequest: CommunityRequest): Resource<Community> {
        if (isOldReddit(communityRequest.service)) {
            logger.info("Reddit Web Scraping (old.reddit.com) requested; routing directly to Redlib ({})", RedlibApi.DEFAULT_HOST)
            return tedditGateway.getCommunity(communityRequest.copy(service = getRedlibService(communityRequest.service)))
        }

        val result = redditGateway.getCommunity(communityRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getCommunity failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        return tedditGateway.getCommunity(communityRequest.copy(service = getRedlibService(communityRequest.service)))
    }

    override suspend fun getCommunityInfo(communityInfoRequest: CommunityInfoRequest): Resource<CommunityInfo> {
        if (isOldReddit(communityInfoRequest.service)) {
            logger.info("Reddit Web Scraping (old.reddit.com) requested; routing directly to Redlib ({})", RedlibApi.DEFAULT_HOST)
            return tedditGateway.getCommunityInfo(communityInfoRequest.copy(service = getRedlibService(communityInfoRequest.service)))
        }

        val result = redditGateway.getCommunityInfo(communityInfoRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getCommunityInfo failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        return tedditGateway.getCommunityInfo(communityInfoRequest.copy(service = getRedlibService(communityInfoRequest.service)))
    }

    override suspend fun getUser(userRequest: UserRequest): Resource<User> {
        if (isOldReddit(userRequest.service)) {
            logger.info("Reddit Web Scraping (old.reddit.com) requested; routing directly to Redlib ({})", RedlibApi.DEFAULT_HOST)
            return tedditGateway.getUser(userRequest.copy(service = getRedlibService(userRequest.service)))
        }

        val result = redditGateway.getUser(userRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getUser failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        return tedditGateway.getUser(userRequest.copy(service = getRedlibService(userRequest.service)))
    }

    override suspend fun getUserInfo(userInfoRequest: UserInfoRequest): Resource<UserInfo> {
        if (isOldReddit(userInfoRequest.service)) {
            logger.info("Reddit Web Scraping (old.reddit.com) requested; routing directly to Redlib ({})", RedlibApi.DEFAULT_HOST)
            return tedditGateway.getUserInfo(userInfoRequest.copy(service = getRedlibService(userInfoRequest.service)))
        }

        val result = redditGateway.getUserInfo(userInfoRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getUserInfo failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        return tedditGateway.getUserInfo(userInfoRequest.copy(service = getRedlibService(userInfoRequest.service)))
    }

    override suspend fun getPost(postRequest: PostRequest): Resource<Post> {
        if (isOldReddit(postRequest.service)) {
            logger.info("Reddit Web Scraping (old.reddit.com) requested; routing directly to Redlib ({})", RedlibApi.DEFAULT_HOST)
            return tedditGateway.getPost(postRequest.copy(service = getRedlibService(postRequest.service)))
        }

        val result = redditGateway.getPost(postRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getPost failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        return tedditGateway.getPost(postRequest.copy(service = getRedlibService(postRequest.service)))
    }

    override suspend fun getMoreContent(moreContentRequest: MoreContentRequest): Resource<List<Feedable>> {
        if (isOldReddit(moreContentRequest.appendable.service)) {
            logger.info("Reddit Web Scraping (old.reddit.com) requested; routing directly to Redlib ({})", RedlibApi.DEFAULT_HOST)
            val fallbackAppendable = moreContentRequest.appendable.copy(service = getRedlibService(moreContentRequest.appendable.service))
            return tedditGateway.getMoreContent(moreContentRequest.copy(appendable = fallbackAppendable))
        }

        val result = redditGateway.getMoreContent(moreContentRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getMoreContent failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        val fallbackAppendable = moreContentRequest.appendable.copy(service = getRedlibService(moreContentRequest.appendable.service))
        return tedditGateway.getMoreContent(moreContentRequest.copy(appendable = fallbackAppendable))
    }

    override suspend fun getSearchResults(searchRequest: SearchRequest): Resource<SearchResults> {
        if (isOldReddit(searchRequest.service)) {
            logger.info("Reddit Web Scraping (old.reddit.com) requested; routing directly to Redlib ({})", RedlibApi.DEFAULT_HOST)
            return tedditGateway.getSearchResults(searchRequest.copy(service = getRedlibService(searchRequest.service)))
        }

        val result = redditGateway.getSearchResults(searchRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getSearchResults failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        return tedditGateway.getSearchResults(searchRequest.copy(service = getRedlibService(searchRequest.service)))
    }
}
