package com.cosmos.stealth.services.teddit.data.repository

import com.cosmos.stealth.core.common.di.DispatchersModule.Qualifier.DEFAULT_DISPATCHER_QUALIFIER
import com.cosmos.stealth.core.common.di.DispatchersModule.Qualifier.IO_DISPATCHER_QUALIFIER
import com.cosmos.stealth.core.model.api.Appendable
import com.cosmos.stealth.core.model.api.CommunityInfo
import com.cosmos.stealth.core.model.api.Feed
import com.cosmos.stealth.core.model.api.Feedable
import com.cosmos.stealth.core.model.api.Post
import com.cosmos.stealth.core.model.api.SearchResults
import com.cosmos.stealth.core.model.api.UserInfo
import com.cosmos.stealth.core.model.data.Request
import com.cosmos.stealth.core.network.util.Resource
import com.cosmos.stealth.services.reddit.data.mapper.CommentMapper
import com.cosmos.stealth.services.reddit.data.mapper.CommunityMapper
import com.cosmos.stealth.services.reddit.data.mapper.PostMapper
import com.cosmos.stealth.services.reddit.data.mapper.UserMapper
import com.cosmos.stealth.services.reddit.data.model.Data
import com.cosmos.stealth.services.reddit.data.model.JsonMore
import com.cosmos.stealth.services.reddit.data.model.Listing
import com.cosmos.stealth.services.reddit.data.model.ListingData
import com.cosmos.stealth.services.reddit.data.model.MoreChildren
import com.cosmos.stealth.services.reddit.data.model.Sort
import com.cosmos.stealth.services.reddit.data.model.Sorting
import com.cosmos.stealth.services.reddit.data.repository.Repository
import com.cosmos.stealth.services.teddit.data.remote.api.RedlibApi
import com.cosmos.stealth.services.teddit.data.scraper.redlib.RedlibCommentScraper
import com.cosmos.stealth.services.teddit.data.scraper.redlib.RedlibDeepReplyRegistry
import com.cosmos.stealth.services.teddit.data.scraper.redlib.RedlibPostScraper
import com.cosmos.stealth.services.teddit.data.scraper.redlib.RedlibSearchSubredditScraper
import com.cosmos.stealth.services.teddit.data.scraper.redlib.RedlibSubredditScraper
import com.cosmos.stealth.services.teddit.data.scraper.redlib.RedlibUserScraper
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Suppress("TooManyFunctions")
@Single
internal class TedditRepository(
    private val redlibApi: RedlibApi,
    private val deepReplyRegistry: RedlibDeepReplyRegistry,
    postMapper: PostMapper,
    communityMapper: CommunityMapper,
    userMapper: UserMapper,
    commentMapper: CommentMapper,
    @Named(IO_DISPATCHER_QUALIFIER) private val ioDispatcher: CoroutineDispatcher,
    @Named(DEFAULT_DISPATCHER_QUALIFIER) defaultDispatcher: CoroutineDispatcher
) : Repository(postMapper, communityMapper, userMapper, commentMapper, defaultDispatcher) {

    override suspend fun getSubreddit(
        request: Request,
        subreddit: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Feed {
        val newRequest = request.getRequest()
        val instance = newRequest.getInstance()

        val response = safeApiCall {
            val res = redlibApi.getSubreddit(
                instance,
                subreddit,
                sorting.generalSorting,
                sorting.timeSorting,
                after,
                request.info.host
            )
            RedlibPostScraper(ioDispatcher, instance).scrap(res.bodyAsText())
        }

        return getSubreddit(response, newRequest)
    }

    override suspend fun getSubredditInfo(request: Request, subreddit: String): Resource<CommunityInfo> {
        val newRequest = request.getRequest()
        val instance = newRequest.getInstance()

        return getSubredditInfo(newRequest) {
            val res = redlibApi.getSubreddit(instance, subreddit, Sort.HOT, null, null, request.info.host)
            RedlibSubredditScraper(ioDispatcher, instance).scrap(res.bodyAsText())
        }
    }

    override suspend fun searchInSubreddit(
        request: Request,
        subreddit: String,
        query: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Resource<SearchResults> {
        val newRequest = request.getRequest()
        val instance = newRequest.getInstance()

        return searchInSubreddit(newRequest) {
            val res = redlibApi.searchInSubreddit(
                instance,
                subreddit,
                query,
                sorting.generalSorting,
                sorting.timeSorting,
                after,
                request.info.host
            )
            RedlibPostScraper(ioDispatcher, instance).scrap(res.bodyAsText())
        }
    }

    override suspend fun getPost(request: Request, permalink: String, limit: Int?, sort: Sort): Resource<Post> {
        val newRequest = request.getRequest()
        val instance = newRequest.getInstance()

        val response = safeApiCall {
            val res = redlibApi.getPost(instance, permalink, sort, request.info.host)
            val body = res.bodyAsText()

            coroutineScope {
                val post = async { RedlibPostScraper(ioDispatcher, instance).scrap(body) }
                val comments = async { RedlibCommentScraper(ioDispatcher, instance, deepReplyRegistry).scrap(body) }
                listOf(post.await(), comments.await())
            }
        }

        return getPost(response, newRequest)
    }

    override suspend fun getMoreChildren(
        request: Request,
        appendable: Appendable
    ): Resource<List<Feedable>> {
        val newRequest = request.getRequest()
        val token = appendable.content.firstOrNull()?.trim() ?: return Resource.Success(emptyList())
        val url = if (deepReplyRegistry.isToken(token)) deepReplyRegistry.consume(token) else null

        if (url.isNullOrBlank()) return Resource.Success(emptyList())

        return getMoreChildren(newRequest, appendable, null) {
            val res = redlibApi.getDeeperReplies(url, request.info.host)
            val body = res.bodyAsText()
            val listing = RedlibCommentScraper(ioDispatcher, newRequest.getInstance(), deepReplyRegistry, appendable.parentLinkId.orEmpty()).scrap(body)
            MoreChildren(JsonMore(Data(listing.data.children)))
        }
    }

    override suspend fun getUserInfo(request: Request, user: String): Resource<UserInfo> {
        val newRequest = request.getRequest()
        val instance = newRequest.getInstance()

        return getUserInfo(newRequest) {
            val res = redlibApi.getUserOverview(instance, user, request.info.host)
            RedlibUserScraper(ioDispatcher, instance).scrap(res.bodyAsText())
        }
    }

    override suspend fun getUserPosts(
        request: Request,
        user: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Feed {
        val newRequest = request.getRequest()
        val instance = newRequest.getInstance()

        val response = safeApiCall {
            val res = redlibApi.getUserPosts(
                instance,
                user,
                sorting.generalSorting,
                sorting.timeSorting,
                after,
                request.info.host
            )
            RedlibPostScraper(ioDispatcher, instance).scrap(res.bodyAsText())
        }

        return getUserPosts(response, newRequest)
    }

    override suspend fun getUserComments(
        request: Request,
        user: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Feed {
        val newRequest = request.getRequest()
        val instance = newRequest.getInstance()

        val response = safeApiCall {
            val res = redlibApi.getUserComments(
                instance,
                user,
                sorting.generalSorting,
                sorting.timeSorting,
                after,
                request.info.host
            )
            RedlibCommentScraper(ioDispatcher, instance, deepReplyRegistry, linkIdHint = "").scrap(res.bodyAsText())
        }

        return getUserComments(response, newRequest)
    }

    override suspend fun searchPost(
        request: Request,
        query: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Resource<SearchResults> {
        val newRequest = request.getRequest()
        val instance = newRequest.getInstance()

        return searchPost(newRequest) {
            val res = redlibApi.searchPost(
                instance,
                query,
                sorting.generalSorting,
                sorting.timeSorting,
                after,
                request.info.host
            )
            RedlibPostScraper(ioDispatcher, instance).scrap(res.bodyAsText())
        }
    }

    override suspend fun searchUser(
        request: Request,
        query: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Resource<SearchResults> {
        val newRequest = request.getRequest()
        return searchUser(newRequest) {
            Listing("t2", ListingData(null, null, emptyList(), null, null))
        }
    }

    override suspend fun searchSubreddit(
        request: Request,
        query: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Resource<SearchResults> {
        val newRequest = request.getRequest()
        val instance = newRequest.getInstance()

        return searchSubreddit(newRequest) {
            val res = redlibApi.searchSubreddit(
                instance,
                query,
                sorting.generalSorting,
                sorting.timeSorting,
                after,
                request.info.host
            )
            RedlibSearchSubredditScraper(ioDispatcher, instance).scrap(res.bodyAsText())
        }
    }

    private fun Request.getInstance(): String = service.instance ?: BASE_URL.host

    private fun Request.getRequest(): Request {
        return this.copy(service = service.copy(instance = service.instance ?: BASE_URL.host))
    }

    companion object {
        private val BASE_URL = RedlibApi.BASE_URL.toHttpUrl()
    }
}
