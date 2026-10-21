package com.cosmos.stealth.services.teddit.data.repository

import com.cosmos.stealth.core.common.di.DispatchersModule.Qualifier.DEFAULT_DISPATCHER_QUALIFIER
import com.cosmos.stealth.core.common.di.DispatchersModule.Qualifier.IO_DISPATCHER_QUALIFIER
import com.cosmos.stealth.core.model.api.Appendable
import com.cosmos.stealth.core.model.api.CommunityInfo
import com.cosmos.stealth.core.model.api.Feed
import com.cosmos.stealth.core.model.api.Feedable
import com.cosmos.stealth.core.model.api.Post
import com.cosmos.stealth.core.model.api.SearchResults
import com.cosmos.stealth.core.model.api.ServiceName
import com.cosmos.stealth.core.model.api.UserInfo
import com.cosmos.stealth.core.model.data.Request
import com.cosmos.stealth.core.network.util.Resource
import com.cosmos.stealth.services.reddit.data.mapper.CommentMapper
import com.cosmos.stealth.services.reddit.data.mapper.CommunityMapper
import com.cosmos.stealth.services.reddit.data.mapper.PostMapper
import com.cosmos.stealth.services.reddit.data.mapper.UserMapper
import com.cosmos.stealth.services.reddit.data.model.Child
import com.cosmos.stealth.services.reddit.data.model.CommentChild
import com.cosmos.stealth.services.reddit.data.model.CommentData
import com.cosmos.stealth.services.reddit.data.model.Data
import com.cosmos.stealth.services.reddit.data.model.JsonMore
import com.cosmos.stealth.services.reddit.data.model.Listing
import com.cosmos.stealth.services.reddit.data.model.ListingData
import com.cosmos.stealth.services.reddit.data.model.MoreChild
import com.cosmos.stealth.services.reddit.data.model.MoreChildren
import com.cosmos.stealth.services.reddit.data.model.MoreData
import com.cosmos.stealth.services.reddit.data.model.PostChild
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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
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
            RedlibPostScraper(ioDispatcher, instance).scrap(res.bodyAsText()).enrichGalleries(instance, request.info.host)
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
            RedlibPostScraper(ioDispatcher, instance).scrap(res.bodyAsText()).enrichGalleries(instance, request.info.host)
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
            val flattened = flattenAndRebaseComments(listing.data.children, appendable.parentId, appendable.depth)
            MoreChildren(JsonMore(Data(flattened)))
        }
    }

    private fun flattenAndRebaseComments(
        children: List<Child>,
        targetParentId: String?,
        baseDepth: Int
    ): List<Child> {
        val relevantChildren = if (children.size == 1 && children.first() is CommentChild) {
            val root = children.first() as CommentChild
            val rootName = root.data.name
            val rootId = root.data.id
            val targetId = targetParentId?.removePrefix("t1_")
            if (rootName == targetParentId || (targetId != null && rootId == targetId) || root.data.depth == 0) {
                root.data.replies?.data?.children ?: emptyList()
            } else {
                children
            }
        } else {
            children
        }

        val result = mutableListOf<Child>()

        fun flatten(item: Child, currentDepth: Int) {
            when (item) {
                is CommentChild -> {
                    val copy = CommentChild(
                        data = item.data.copyWith(
                            depth = currentDepth,
                            replies = null
                        )
                    )
                    result.add(copy)
                    item.data.replies?.data?.children?.forEach { child ->
                        flatten(child, currentDepth + 1)
                    }
                }
                is MoreChild -> {
                    val copy = MoreChild(
                        data = item.data.copyWith(
                            depth = currentDepth
                        )
                    )
                    result.add(copy)
                }
                else -> result.add(item)
            }
        }

        for (child in relevantChildren) {
            flatten(child, baseDepth)
        }

        return result
    }

    private fun CommentData.copyWith(depth: Int, replies: Listing? = null): CommentData {
        return CommentData(
            totalAwards = totalAwards,
            authorFlairRichText = authorFlairRichText,
            linkId = linkId,
            replies = replies,
            author = author,
            score = score,
            awardings = awardings,
            bodyHtml = bodyHtml,
            edited = edited,
            isSubmitter = isSubmitter,
            stickied = stickied,
            scoreHidden = scoreHidden,
            permalink = permalink,
            id = id,
            name = name,
            created = created,
            controversiality = controversiality,
            flair = flair,
            depth = depth,
            distinguished = distinguished,
            subreddit = subreddit,
            linkTitle = linkTitle,
            linkPermalink = linkPermalink,
            linkAuthor = linkAuthor
        )
    }

    private fun MoreData.copyWith(depth: Int): MoreData {
        return MoreData(
            count = count,
            name = name,
            id = id,
            depth = depth,
            parentId = parentId,
            children = children.toMutableList()
        )
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
            RedlibPostScraper(ioDispatcher, instance).scrap(res.bodyAsText()).enrichGalleries(instance, request.info.host)
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
            RedlibPostScraper(ioDispatcher, instance).scrap(res.bodyAsText()).enrichGalleries(instance, request.info.host)
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

    private suspend fun Listing.enrichGalleries(instance: String?, host: String?): Listing = coroutineScope {
        val targetInstance = instance ?: BASE_URL.host
        val newChildren = data.children.map { child ->
            val postChild = child as? PostChild ?: return@map async { child }
            val post = postChild.data
            if (post.isRedditGallery == true && (post.galleryData?.items?.size ?: 0) <= 2 && post.permalink.isNotBlank()) {
                async {
                    try {
                        withTimeoutOrNull(2500L) {
                            val res = redlibApi.getPost(targetInstance, post.permalink, Sort.HOT, host)
                            val body = res.bodyAsText()
                            val singleListing = RedlibPostScraper(ioDispatcher, targetInstance).scrap(body)
                            val singlePost = (singleListing.data.children.firstOrNull() as? PostChild)?.data
                            val singleGallery = singlePost?.galleryData
                            if (singleGallery != null && singleGallery.items.size > 1) {
                                postChild.copy(
                                    data = post.copy(
                                        galleryData = singleGallery,
                                        mediaMetadata = singlePost.mediaMetadata,
                                        media = singlePost.media ?: post.media
                                    )
                                )
                            } else {
                                postChild
                            }
                        } ?: postChild
                    } catch (e: Exception) {
                        postChild
                    }
                }
            } else {
                async { child }
            }
        }.awaitAll()

        val newData = ListingData(
            modhash = data.modhash,
            dist = data.dist,
            children = newChildren,
            after = data.after,
            before = data.before
        )
        Listing(kind, newData)
    }

    private fun Request.getInstance(): String {
        val inst = service.instance
        if (inst.isNullOrBlank() || inst.contains("teddit", ignoreCase = true) || inst.contains("reddit.com", ignoreCase = true)) {
            return BASE_URL.host
        }
        return inst
    }

    private fun Request.getRequest(): Request {
        val instance = getInstance()
        return this.copy(service = service.copy(instance = instance))
    }

    companion object {
        private val BASE_URL = RedlibApi.BASE_URL.toHttpUrl()
    }
}
