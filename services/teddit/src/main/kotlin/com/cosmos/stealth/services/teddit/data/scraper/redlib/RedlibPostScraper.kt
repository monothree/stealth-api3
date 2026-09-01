package com.cosmos.stealth.services.teddit.data.scraper.redlib

import com.cosmos.stealth.services.reddit.data.model.Listing
import com.cosmos.stealth.services.reddit.data.model.ListingData
import com.cosmos.stealth.services.reddit.data.model.Media
import com.cosmos.stealth.services.reddit.data.model.PostChild
import com.cosmos.stealth.services.reddit.data.model.PostData
import com.cosmos.stealth.services.reddit.data.model.RedditVideoPreview
import com.cosmos.stealth.services.reddit.data.model.RichText
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class RedlibPostScraper(
    ioDispatcher: CoroutineDispatcher,
    instanceBaseUrl: String? = null
) : RedlibBaseScraper<Listing>(ioDispatcher, instanceBaseUrl) {

    override suspend fun scrapDocument(document: Document): Listing {
        val posts = document.select("div.post")
            .toList()
            .filter { it.selectFirst("h2.post_title, h1.post_title") != null }
            .map { it.toPost() }

        return Listing(
            KIND,
            ListingData(
                modhash = null,
                dist = null,
                children = posts,
                after = extractAfter(),
                before = null
            )
        )
    }

    private fun Element.toPost(): PostChild {
        val subredditLink = selectFirst("a.post_subreddit")
        val prefixedSubreddit = subredditLink?.text().orEmpty()
        val subreddit = stripPrefix(prefixedSubreddit)

        val authorLink = selectFirst("a.post_author")
        val author = authorLink?.text()?.removePrefix("u/")?.trim().orEmpty()
            .ifEmpty { "[deleted]" }
        val distinguished = when {
            authorLink == null -> "regular"
            authorLink.hasClass("moderator") -> "moderator"
            authorLink.hasClass("admin") -> "admin"
            else -> "regular"
        }

        val titleElement = selectFirst("h2.post_title, h1.post_title")
        val titleAnchor = selectFirst("h2.post_title > a:not(.post_flair)")
        val title = titleAnchor?.text() ?: titleElement?.withoutFlairText().orEmpty()
        val permalink = titleAnchor?.attr("href")
            ?: selectFirst("#post_links a[href*=/comments/]")?.attr("href")
            ?: document?.selectFirst("meta[property=og:url]")?.attr("content")
            ?: ""

        val flairAnchor = selectFirst("a.post_flair")
        val flair = flairAnchor?.text()
        val flairRichText: List<RichText> = flairAnchor?.let {
            listOf(RichText(it.text(), null))
        } ?: emptyList()

        val postId = id().ifEmpty { permalink.substringAfter("/comments/").substringBefore("/") }
        val name = if (postId.isNotEmpty()) "t3_$postId" else ""

        val scoreElement = selectFirst("div.post_score")
        val score = parseScore(scoreElement)
        val hideScore = scoreElement?.attr("title")?.equals("Hidden", ignoreCase = true) == true
        val created = parseRedlibTime(selectFirst("p.post_header span.created"))

        val commentsAnchor = selectFirst("a.post_comments")
        val commentsCount = commentsAnchor?.attr("title")?.let { parseHumanCount(it) }
            ?: commentsAnchor?.text()?.let { parseHumanCount(it) }
            ?: document?.selectFirst("#comment_count")?.text()?.let { parseHumanCount(it) }
            ?: 0

        val isOver18 = selectFirst("small.nsfw") != null
        val isSpoiler = selectFirst("small.spoiler") != null
        val isStickied = hasClass("stickied")
        val isLocked = selectFirst("small.locked") != null

        val mediaImage = selectFirst("div.post_media_content a.post_media_image")
        val mediaVideo = selectFirst("div.post_media_content video.post_media_video")
        val isVideo = mediaVideo != null
        val isImage = mediaImage != null

        val mediaUrl = when {
            isImage -> resolve(mediaImage?.attr("href")).orEmpty()
            isVideo -> resolveVideoSrc(mediaVideo).orEmpty()
            else -> ""
        }

        val media: Media? = if (isVideo) {
            val src = mediaUrl
            if (src.isNotEmpty()) {
                Media(
                    type = null,
                    embed = null,
                    redditVideoPreview = RedditVideoPreview(
                        fallbackUrl = src,
                        height = 0,
                        width = 0,
                        duration = 0,
                        isGif = false,
                        dashUrl = null
                    )
                )
            } else null
        } else null

        val thumbnailAnchor = selectFirst("a.post_thumbnail")
        val thumbnail: String? = when {
            isImage -> resolve(mediaImage?.attr("href"))
            isVideo -> resolve(mediaVideo?.attr("poster"))
            thumbnailAnchor != null && !thumbnailAnchor.hasClass("no_thumbnail") -> {
                resolve(thumbnailAnchor.selectFirst("svg image")?.attr("href"))
            }
            else -> null
        }

        val externalHref = selectFirst("a#post_url")?.attr("href")
            ?: thumbnailAnchor?.takeIf { it.hasClass("no_thumbnail") }?.attr("href")
            ?: thumbnailAnchor?.attr("href")
        val isExternal = externalHref?.startsWith("http") == true
        val domain = if (isExternal) {
            externalHref?.toHttpUrlOrNull()?.host.orEmpty()
        } else if (isImage) {
            "i.redd.it"
        } else if (isVideo) {
            "v.redd.it"
        } else {
            ""
        }

        val isSelf = !isImage && !isVideo && !isExternal

        val url = when {
            isImage -> mediaUrl
            isVideo -> mediaUrl
            isExternal -> externalHref.orEmpty()
            else -> if (permalink.startsWith("/")) "https://www.reddit.com$permalink" else permalink
        }

        val selfTextHtml = selectFirst("div.post_body")
            ?.selectFirst("div.md")
            ?.outerHtml()

        val postData = PostData(
            subreddit = subreddit,
            linkFlairRichText = flairRichText,
            authorFlairRichText = null,
            title = title,
            prefixedSubreddit = prefixedSubreddit,
            name = name,
            ratio = null,
            totalAwards = 0,
            isOC = false,
            flair = flair,
            authorFlair = null,
            galleryData = null,
            score = score,
            hint = null,
            isSelf = isSelf,
            crossposts = null,
            domain = domain,
            selfTextHtml = selfTextHtml,
            suggestedSort = null,
            isArchived = false,
            isOver18 = isOver18,
            mediaPreview = null,
            awardings = emptyList(),
            isSpoiler = isSpoiler,
            isLocked = isLocked,
            distinguished = distinguished,
            author = author,
            commentsNumber = commentsCount,
            permalink = permalink,
            isStickied = isStickied,
            url = url,
            created = created,
            media = media,
            mediaMetadata = null,
            isRedditGallery = false,
            isVideo = isVideo
        ).apply {
            this.thumbnail = thumbnail
            this.hideScore = hideScore
        }

        return PostChild(postData)
    }

    private fun resolveVideoSrc(video: Element?): String? {
        if (video == null) return null
        val direct = video.attr("src").takeIf { it.isNotBlank() }
        if (direct != null) return resolve(direct)
        val source = video.select("source").firstOrNull { it.attr("src").isNotBlank() }
            ?: return null
        return resolve(source.attr("src"))
    }

    private fun Element.withoutFlairText(): String {
        val clone = clone()
        clone.select("a.post_flair").remove()
        return clone.text().trim()
    }

    companion object {
        private const val KIND = "t3"
    }
}
