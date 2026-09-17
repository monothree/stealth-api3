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

    override suspend fun getFeed(singleFeedRequest: SingleFeedRequest): Feed {
        val feed = redditGateway.getFeed(singleFeedRequest)
        val status = feed.status.firstOrNull()

        if (feed.items.isNotEmpty() || (status != null && status.isSuccess)) {
            return feed
        }

        logger.warn("Reddit API getFeed failed (status: {}), falling back to Redlib ({})", status?.code, RedlibApi.DEFAULT_HOST)
        val fallbackService = singleFeedRequest.service.copy(
            name = ServiceName.redlib,
            instance = singleFeedRequest.service.instance ?: RedlibApi.DEFAULT_HOST
        )
        return tedditGateway.getFeed(singleFeedRequest.copy(service = fallbackService))
    }

    override suspend fun getCommunity(communityRequest: CommunityRequest): Resource<Community> {
        val result = redditGateway.getCommunity(communityRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getCommunity failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        val fallbackService = communityRequest.service.copy(
            name = ServiceName.redlib,
            instance = communityRequest.service.instance ?: RedlibApi.DEFAULT_HOST
        )
        return tedditGateway.getCommunity(communityRequest.copy(service = fallbackService))
    }

    override suspend fun getCommunityInfo(communityInfoRequest: CommunityInfoRequest): Resource<CommunityInfo> {
        val result = redditGateway.getCommunityInfo(communityInfoRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getCommunityInfo failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        val fallbackService = communityInfoRequest.service.copy(
            name = ServiceName.redlib,
            instance = communityInfoRequest.service.instance ?: RedlibApi.DEFAULT_HOST
        )
        return tedditGateway.getCommunityInfo(communityInfoRequest.copy(service = fallbackService))
    }

    override suspend fun getUser(userRequest: UserRequest): Resource<User> {
        val result = redditGateway.getUser(userRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getUser failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        val fallbackService = userRequest.service.copy(
            name = ServiceName.redlib,
            instance = userRequest.service.instance ?: RedlibApi.DEFAULT_HOST
        )
        return tedditGateway.getUser(userRequest.copy(service = fallbackService))
    }

    override suspend fun getUserInfo(userInfoRequest: UserInfoRequest): Resource<UserInfo> {
        val result = redditGateway.getUserInfo(userInfoRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getUserInfo failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        val fallbackService = userInfoRequest.service.copy(
            name = ServiceName.redlib,
            instance = userInfoRequest.service.instance ?: RedlibApi.DEFAULT_HOST
        )
        return tedditGateway.getUserInfo(userInfoRequest.copy(service = fallbackService))
    }

    override suspend fun getPost(postRequest: PostRequest): Resource<Post> {
        val result = redditGateway.getPost(postRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getPost failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        val fallbackService = postRequest.service.copy(
            name = ServiceName.redlib,
            instance = postRequest.service.instance ?: RedlibApi.DEFAULT_HOST
        )
        return tedditGateway.getPost(postRequest.copy(service = fallbackService))
    }

    override suspend fun getMoreContent(moreContentRequest: MoreContentRequest): Resource<List<Feedable>> {
        val result = redditGateway.getMoreContent(moreContentRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getMoreContent failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        val fallbackService = moreContentRequest.appendable.service.copy(
            name = ServiceName.redlib,
            instance = moreContentRequest.appendable.service.instance ?: RedlibApi.DEFAULT_HOST
        )
        val fallbackAppendable = moreContentRequest.appendable.copy(service = fallbackService)
        return tedditGateway.getMoreContent(moreContentRequest.copy(appendable = fallbackAppendable))
    }

    override suspend fun getSearchResults(searchRequest: SearchRequest): Resource<SearchResults> {
        val result = redditGateway.getSearchResults(searchRequest)
        if (result is Resource.Success) return result

        logger.warn("Reddit API getSearchResults failed, falling back to Redlib ({})", RedlibApi.DEFAULT_HOST)
        val fallbackService = searchRequest.service.copy(
            name = ServiceName.redlib,
            instance = searchRequest.service.instance ?: RedlibApi.DEFAULT_HOST
        )
        return tedditGateway.getSearchResults(searchRequest.copy(service = fallbackService))
    }
}
