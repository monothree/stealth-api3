package com.cosmos.stealth.services.teddit.data.scraper.redlib

import com.cosmos.stealth.services.reddit.data.model.Child
import com.cosmos.stealth.services.reddit.data.model.CommentChild
import com.cosmos.stealth.services.reddit.data.model.CommentData
import com.cosmos.stealth.services.reddit.data.model.Listing
import com.cosmos.stealth.services.reddit.data.model.ListingData
import com.cosmos.stealth.services.reddit.data.model.MoreChild
import com.cosmos.stealth.services.reddit.data.model.MoreData
import kotlinx.coroutines.CoroutineDispatcher
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class RedlibCommentScraper(
    ioDispatcher: CoroutineDispatcher,
    instanceBaseUrl: String? = null,
    private val registry: RedlibDeepReplyRegistry,
    private val linkIdHint: String? = null
) : RedlibBaseScraper<Listing>(ioDispatcher, instanceBaseUrl) {

    private var linkId: String = ""
    private var pageUser: String = ""

    override suspend fun scrapDocument(document: Document): Listing {
        pageUser = document.selectFirst("#user_name")?.text()?.removePrefix("u/")?.trim()
            ?: document.selectFirst("#user_title")?.text()?.removePrefix("u/")?.trim()
            ?: ""
        linkId = linkIdHint ?: run {
            val rawId = document.selectFirst("div.post")?.id().orEmpty()
            if (rawId.isNotEmpty()) "t3_$rawId" else derivePostId(document)
        }

        val roots: List<Element> = document.select("div.thread > div.comment, div.comment.user-comment").toList()
        val children: List<Child> = roots.map { root -> root.toComment(0) }

        return Listing(
            KIND,
            ListingData(
                modhash = null,
                dist = null,
                children = children,
                after = null,
                before = null
            )
        )
    }

    private fun Element.toComment(depth: Int): CommentChild {
        val commentLink = selectFirst("a.comment_link")
        val rawId = id().ifEmpty { commentLink?.attr("href")?.toCommentId().orEmpty() }
        val name = if (rawId.isNotEmpty()) "t1_$rawId" else ""

        val score = parseScore(selectFirst("p.comment_score"))
        val scoreElement = selectFirst("p.comment_score")
        val scoreHidden = scoreElement?.attr("title")?.equals("Hidden", ignoreCase = true) == true ||
            scoreElement?.text()?.trim() == "-"

        val authorAnchor = selectFirst("a.comment_author")
        val author = authorAnchor?.text()?.removePrefix("u/")?.trim().orEmpty()
            .ifEmpty { pageUser.ifEmpty { "[deleted]" } }
        val isSubmitter = authorAnchor?.hasClass("op") == true
        val distinguished = when {
            authorAnchor == null -> "regular"
            authorAnchor.hasClass("moderator") -> "moderator"
            authorAnchor.hasClass("admin") -> "admin"
            else -> "regular"
        }

        val createdElement = selectFirst("summary.comment_data > a.created")
            ?: selectFirst("summary.comment_data span.created")
        val created = parseRedlibTime(createdElement)
        val permalink = selectFirst("summary.comment_data > a.created")?.attr("href")
            ?: commentLink?.attr("href")
            ?: ""

        val edited = selectFirst("span.edited")?.let { parseRedlibTime(it) } ?: -1L

        val bodyHtml = selectFirst(".comment_body div.md, .comment_body + div.md, details.comment_right > div.md")
            ?.canonicalizeRedditLinks()
            ?.outerHtml()
            .orEmpty()

        val subreddit = stripPrefix(selectFirst("a.comment_subreddit")?.text().orEmpty())
        val linkTitle = commentLink?.attr("title")?.ifEmpty { commentLink.text() }
        val linkPermalink = commentLink?.attr("href")?.substringBefore("#")
        val linkAuthor = pageUser.ifEmpty { null }

        val repliesContainer = selectFirst("blockquote.replies")
        val replies = repliesContainer?.let { container ->
            val nested = container.children()
                .toList()
                .filter { it.tagName() == "div" && it.hasClass("comment") }
                .map<Element, Child> { it.toComment(depth + 1) }
            val more = collectMoreReplies(container, depth + 1, name)
            (nested + more)
        } ?: emptyList()

        val repliesListing: Listing? = if (replies.isNotEmpty()) {
            Listing(KIND, ListingData(null, null, replies, null, null))
        } else null

        val data = CommentData(
            totalAwards = 0,
            authorFlairRichText = null,
            linkId = linkId,
            replies = repliesListing,
            author = author,
            score = score,
            awardings = emptyList(),
            bodyHtml = bodyHtml,
            edited = edited,
            isSubmitter = isSubmitter,
            stickied = false,
            scoreHidden = scoreHidden,
            permalink = permalink,
            id = rawId,
            name = name,
            created = created,
            controversiality = 0,
            flair = null,
            depth = depth,
            distinguished = distinguished,
            subreddit = subreddit,
            linkTitle = linkTitle,
            linkPermalink = linkPermalink,
            linkAuthor = linkAuthor
        )

        return CommentChild(data)
    }

    private fun collectMoreReplies(
        container: Element,
        depth: Int,
        parentName: String?
    ): List<MoreChild> {
        val anchors = container.children().filter { child ->
            child.tagName() == "a" && child.hasClass("deeper_replies")
        }
        return anchors.map { anchor ->
            val absolute = resolveAbsolute(anchor.attr("href")).orEmpty()
            val token = registry.register(absolute)
            val text = anchor.text()
            val count = parseHumanCount(text) ?: 0
            val fakeId = token.removePrefix(RedlibDeepReplyRegistry.PREFIX)
            MoreChild(
                MoreData(
                    count = count,
                    name = "t1_$fakeId",
                    id = fakeId,
                    depth = depth,
                    parentId = parentName.orEmpty(),
                    children = mutableListOf(token)
                )
            )
        }
    }

    companion object {
        private const val KIND = "t1"
    }

    private fun String.toCommentId(): String {
        val fragment = substringAfterLast("#", "")
        if (fragment.isNotBlank()) return fragment
        return trimEnd('/').substringAfterLast("/")
    }

    private fun Element.canonicalizeRedditLinks(): Element {
        select("a[href]").forEach { anchor ->
            val href = anchor.attr("href")
            if (isRedditMessageUrl(href)) {
                anchor.tagName("u")
                anchor.removeAttr("href")
            } else {
                canonicalRedditUrl(href)?.let { anchor.attr("href", it) }
            }
        }
        return this
    }

    private fun derivePostId(document: Document): String {
        val permalink = document.selectFirst("#post_links a[href*=/comments/]")?.attr("href")
            ?: document.selectFirst("meta[property=og:url]")?.attr("content")
            ?: return ""
        val id = permalink.substringAfter("/comments/", "").substringBefore("/")
        return if (id.isNotBlank()) "t3_$id" else ""
    }
}
