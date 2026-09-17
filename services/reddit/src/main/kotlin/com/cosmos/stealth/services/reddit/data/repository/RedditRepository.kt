package com.cosmos.stealth.services.reddit.data.repository

import com.cosmos.stealth.core.common.di.DispatchersModule.Qualifier.DEFAULT_DISPATCHER_QUALIFIER
import com.cosmos.stealth.core.model.api.Appendable
import com.cosmos.stealth.core.model.api.CommunityInfo
import com.cosmos.stealth.core.model.api.Feed
import com.cosmos.stealth.core.model.api.Feedable
import com.cosmos.stealth.core.model.api.Post
import com.cosmos.stealth.core.model.api.SearchResults
import com.cosmos.stealth.core.model.api.UserInfo
import com.cosmos.stealth.core.model.data.Request
import com.cosmos.stealth.core.network.util.LinkValidator
import com.cosmos.stealth.core.network.util.Resource
import com.cosmos.stealth.services.reddit.data.mapper.CommentMapper
import com.cosmos.stealth.services.reddit.data.mapper.CommunityMapper
import com.cosmos.stealth.services.reddit.data.mapper.PostMapper
import com.cosmos.stealth.services.reddit.data.mapper.UserMapper
import com.cosmos.stealth.services.reddit.data.model.Sort
import com.cosmos.stealth.services.reddit.data.model.Sorting
import com.cosmos.stealth.services.reddit.data.remote.api.DataRedditApi
import com.cosmos.stealth.services.reddit.data.remote.api.RedditApi
import com.cosmos.stealth.services.reddit.data.remote.api.ScrapRedditApi
import com.cosmos.stealth.services.reddit.di.RedditModule.Qualifier.REDDIT_QUALIFIER
import com.cosmos.stealth.services.reddit.di.RedditModule.Qualifier.REDDIT_SCRAP_QUALIFIER
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Suppress("TooManyFunctions", "LongParameterList")
@Single
internal class RedditRepository(
    @Named(REDDIT_QUALIFIER) private val dataRedditApi: RedditApi,
    @Named(REDDIT_SCRAP_QUALIFIER) private val scrapRedditApi: RedditApi,
    private val credentialsRepository: CredentialsRepository,
    postMapper: PostMapper,
    communityMapper: CommunityMapper,
    userMapper: UserMapper,
    commentMapper: CommentMapper,
    @Named(DEFAULT_DISPATCHER_QUALIFIER) defaultDispatcher: CoroutineDispatcher
) : Repository(postMapper, communityMapper, userMapper, commentMapper, defaultDispatcher) {

    override suspend fun getSubreddit(
        request: Request,
        subreddit: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Feed {
        val source = getSource(request.service.instance)
        var finalSource = source

        val response = getResponseWithFallback(source) { redditApi ->
            finalSource = redditApi

            safeApiCall {
                redditApi.getSubreddit(
                    subreddit,
                    sorting.generalSorting,
                    sorting.timeSorting,
                    after,
                    limit,
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host
                )
            }
        }

        return getSubreddit(response, finalSource.getRequest(request))
    }

    override suspend fun getSubredditInfo(request: Request, subreddit: String): Resource<CommunityInfo> {
        val source = getSource(request.service.instance)

        return getResponseWithFallback(source) { redditApi ->
            getSubredditInfo(redditApi.getRequest(request)) {
                redditApi.getSubredditInfo(
                    subreddit,
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host
                )
            }
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
        val source = getSource(request.service.instance)

        return getResponseWithFallback(source) { redditApi ->
            searchInSubreddit(redditApi.getRequest(request)) {
                redditApi.searchInSubreddit(
                    subreddit,
                    query,
                    sorting.generalSorting,
                    sorting.timeSorting,
                    after,
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host
                )
            }
        }
    }

    override suspend fun getPost(request: Request, permalink: String, limit: Int?, sort: Sort): Resource<Post> {
        val source = getSource(request.service.instance)
        var finalSource = source

        val response = getResponseWithFallback(source) { redditApi ->
            finalSource = redditApi

            safeApiCall {
                redditApi.getPost(
                    permalink,
                    limit,
                    sort,
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host
                )
            }
        }

        return getPost(response, finalSource.getRequest(request))
    }

    override suspend fun getMoreChildren(
        request: Request,
        appendable: Appendable
    ): Resource<List<Feedable>> {
        val containsMoreComments = appendable.content.size > LOAD_MORE_LIMIT

        val children = appendable.content.take(LOAD_MORE_LIMIT).joinToString(",")

        var additionalContentFeedable: Appendable? = null

        if (containsMoreComments) {
            val count = appendable.count

            // Remove first 100 comments from list
            val content = appendable.content.toMutableList()
                .apply { subList(0, LOAD_MORE_LIMIT).clear() }

            additionalContentFeedable = appendable.copy(
                count = if (count > LOAD_MORE_LIMIT) count - LOAD_MORE_LIMIT else 0,
                content = content.toList()
            )
        }

        val source = getSource(request.service.instance)

        return getResponseWithFallback(source) { redditApi ->
            getMoreChildren(redditApi.getRequest(request), appendable, additionalContentFeedable) {
                redditApi.getMoreChildren(
                    children,
                    appendable.parentLinkId.orEmpty(),
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host,
                    appendable.parentId,
                    appendable.refLink,
                    appendable.depth
                )
            }
        }
    }

    override suspend fun getUserInfo(request: Request, user: String): Resource<UserInfo> {
        val source = getSource(request.service.instance)

        return getResponseWithFallback(source) { redditApi ->
            getUserInfo(redditApi.getRequest(request)) {
                redditApi.getUserInfo(user, bearer = credentialsRepository.accessToken, host = request.info.host)
            }
        }
    }

    override suspend fun getUserPosts(
        request: Request,
        user: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Feed {
        val source = getSource(request.service.instance)
        var finalSource = source

        val response = getResponseWithFallback(source) { redditApi ->
            finalSource = redditApi

            safeApiCall {
                redditApi.getUserPosts(
                    user,
                    sorting.generalSorting,
                    sorting.timeSorting,
                    after,
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host
                )
            }
        }

        return getUserPosts(response, finalSource.getRequest(request))
    }

    override suspend fun getUserComments(
        request: Request,
        user: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Feed {
        val source = getSource(request.service.instance)
        var finalSource = source

        val response = getResponseWithFallback(source) { redditApi ->
            finalSource = redditApi

            safeApiCall {
                redditApi.getUserComments(
                    user,
                    sorting.generalSorting,
                    sorting.timeSorting,
                    after,
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host
                )
            }
        }

        return getUserComments(response, finalSource.getRequest(request))
    }

    override suspend fun searchPost(
        request: Request,
        query: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Resource<SearchResults> {
        val source = getSource(request.service.instance)

        return getResponseWithFallback(source) { redditApi ->
            searchPost(redditApi.getRequest(request)) {
                redditApi.searchPost(
                    query,
                    sorting.generalSorting,
                    sorting.timeSorting,
                    after,
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host
                )
            }
        }
    }

    override suspend fun searchUser(
        request: Request,
        query: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Resource<SearchResults> {
        val source = getSource(request.service.instance)

        return getResponseWithFallback(source) { redditApi ->
            searchUser(redditApi.getRequest(request)) {
                redditApi.searchUser(
                    query,
                    sorting.generalSorting,
                    sorting.timeSorting,
                    after,
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host
                )
            }
        }
    }

    override suspend fun searchSubreddit(
        request: Request,
        query: String,
        sorting: Sorting,
        limit: Int,
        after: String?
    ): Resource<SearchResults> {
        val source = getSource(request.service.instance)

        return getResponseWithFallback(source) { redditApi ->
            searchSubreddit(redditApi.getRequest(request)) {
                redditApi.searchSubreddit(
                    query,
                    sorting.generalSorting,
                    sorting.timeSorting,
                    after,
                    bearer = credentialsRepository.accessToken,
                    host = request.info.host
                )
            }
        }
    }

    private fun getSource(instance: String?): RedditApi {
        val instanceUrl = LinkValidator(instance).validUrl

        return when (instanceUrl?.host) {
            SCRAP_URL.host -> scrapRedditApi
            else -> dataRedditApi
        }
    }

    private fun RedditApi.getRequest(request: Request): Request {
        return when (this) {
            is DataRedditApi -> request.copy(service = request.service.copy(instance = BASE_URL.host))
            is ScrapRedditApi -> request.copy(service = request.service.copy(instance = SCRAP_URL.host))
            else -> request
        }
    }

    private inline fun <T> getResponseWithFallback(
        source: RedditApi,
        apiCall: (RedditApi) -> Resource<T>
    ): Resource<T> {
        return apiCall(source)
    }

    companion object {
        private const val LOAD_MORE_LIMIT = 100

        internal val BASE_URL = RedditApi.BASE_URL.toHttpUrl()
        internal val SCRAP_URL = RedditApi.BASE_URL_OLD.toHttpUrl()
    }
}
