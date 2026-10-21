package com.cosmos.stealth.services.teddit.data.scraper.redlib

import com.cosmos.stealth.services.reddit.data.model.GalleryData
import com.cosmos.stealth.services.reddit.data.model.GalleryDataItem
import com.cosmos.stealth.services.reddit.data.model.GalleryImage
import com.cosmos.stealth.services.reddit.data.model.GalleryItem
import com.cosmos.stealth.services.reddit.data.model.Listing
import com.cosmos.stealth.services.reddit.data.model.ListingData
import com.cosmos.stealth.services.reddit.data.model.Media
import com.cosmos.stealth.services.reddit.data.model.MediaMetadata
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

        val galleryFigures = select("div.gallery figure").toList()
        val thumbnailAnchor = selectFirst("a.post_thumbnail")
        val isGallery = galleryFigures.isNotEmpty() ||
            thumbnailAnchor?.selectFirst("span")?.text()?.contains("gallery", ignoreCase = true) == true ||
            selectFirst(".gallery, span:contains(gallery)") != null

        val mediaImage = selectFirst("div.post_media_content a.post_media_image")
        val mediaVideo = selectFirst("div.post_media_content video.post_media_video")
        val isVideoElement = mediaVideo != null
        val rawVideoSrc = if (isVideoElement) resolveVideoSrc(mediaVideo).orEmpty() else ""

        val isGif = isVideoElement && (
            mediaVideo?.hasClass("post_media_gif") == true ||
            mediaVideo?.hasAttr("loop") == true ||
            mediaVideo?.selectFirst("a")?.text()?.contains("GIF", ignoreCase = true) == true ||
            rawVideoSrc.contains(".gif", ignoreCase = true) ||
            mediaVideo?.attr("poster")?.contains(".gif", ignoreCase = true) == true
        )

        val gifPath = rawVideoSrc.substringBefore('?').ifEmpty {
            mediaVideo?.attr("poster")?.substringBefore('?').orEmpty()
        }
        val gifFileName = gifPath.substringAfterLast('/')
        val isRedditGif = isGif && gifFileName.endsWith(".gif", ignoreCase = true)
        val gifId = if (isRedditGif) gifFileName.substringBeforeLast('.') else null
        val gifFullUrl = if (gifId != null) "https://i.redd.it/$gifId.gif" else null

        val isVideo = isVideoElement && !isRedditGif
        var isImage = (mediaImage != null || isRedditGif) && !isGallery && !isVideo

        val rawMediaUrl = when {
            isRedditGif && gifFullUrl != null -> gifFullUrl
            isImage -> resolve(mediaImage?.attr("href")).orEmpty()
            isVideoElement -> rawVideoSrc
            else -> ""
        }
        var mediaUrl = rawMediaUrl

        val videoId = if (isVideoElement) VIDEO_ID_REGEX.find(rawVideoSrc)?.groupValues?.getOrNull(1) else null

        val media: Media? = if (isVideoElement && !isRedditGif) {
            val src = rawVideoSrc
            if (src.isNotEmpty() || videoId != null) {
                val fallback = if (isGif) src else if (videoId != null) "https://v.redd.it/$videoId/HLSPlaylist.m3u8" else src
                val dash = if (isGif) null else if (videoId != null) "https://v.redd.it/$videoId/DASHPlaylist.mpd" else null
                Media(
                    type = null,
                    embed = null,
                    redditVideoPreview = RedditVideoPreview(
                        fallbackUrl = fallback,
                        height = mediaVideo?.attr("height")?.toIntOrNull() ?: 0,
                        width = mediaVideo?.attr("width")?.toIntOrNull() ?: 0,
                        duration = 0,
                        isGif = isGif,
                        dashUrl = dash
                    )
                )
            } else null
        } else null

        var galleryData: GalleryData? = null
        var mediaMetadata: MediaMetadata? = null

        if (galleryFigures.isNotEmpty()) {
            val dataItems = mutableListOf<GalleryDataItem>()
            val metaItems = mutableListOf<GalleryItem>()

            galleryFigures.forEachIndexed { index, figure ->
                val a = figure.selectFirst("a")
                val img = figure.selectFirst("img")
                val rawHref = a?.attr("href")?.takeIf { it.isNotBlank() }
                    ?: img?.attr("src")?.takeIf { it.isNotBlank() }
                    ?: return@forEachIndexed

                val caption = figure.selectFirst("figcaption p")?.text()?.trim()?.ifEmpty { null }
                val path = rawHref.substringBefore('?')
                val fileName = path.substringAfterLast('/')
                val idCandidate = fileName.substringBeforeLast('.')
                val mediaId = if (idCandidate.isNotBlank()) idCandidate else "item_$index"
                val ext = fileName.substringAfterLast('.', "jpg")
                val fullUrl = if (idCandidate.isNotBlank()) "https://i.redd.it/$mediaId.$ext" else resolve(rawHref).orEmpty()
                val width = Regex("[?&]width=([0-9]+)").find(rawHref)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val height = Regex("[?&]height=([0-9]+)").find(rawHref)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val previewUrl = resolve(rawHref).orEmpty()

                dataItems.add(GalleryDataItem(caption = caption, mediaId = mediaId))
                metaItems.add(
                    GalleryItem(
                        mimeType = "image/jpeg",
                        image = GalleryImage(height = height, width = width, url = fullUrl, mp4 = null),
                        previews = if (previewUrl.isNotBlank()) listOf(GalleryImage(height, width, previewUrl, null)) else null,
                        id = mediaId
                    )
                )
            }

            if (dataItems.isNotEmpty()) {
                galleryData = GalleryData(dataItems)
                mediaMetadata = MediaMetadata(metaItems)
            }
        } else if (isGallery) {
            val thumbSrc = thumbnailAnchor?.selectFirst("svg image")?.attr("href")
                ?: thumbnailAnchor?.selectFirst("img")?.attr("src")

            if (!thumbSrc.isNullOrBlank()) {
                val path = thumbSrc.substringBefore('?')
                val fileName = path.substringAfterLast('/')
                val mediaId = fileName.substringBeforeLast('.').ifBlank { "preview_item" }
                val ext = fileName.substringAfterLast('.', "jpg")
                val fullUrl = if (mediaId != "preview_item") "https://i.redd.it/$mediaId.$ext" else resolve(thumbSrc).orEmpty()
                val previewUrl = resolve(thumbSrc).orEmpty()

                galleryData = GalleryData(
                    listOf(
                        GalleryDataItem(caption = null, mediaId = mediaId),
                        GalleryDataItem(caption = null, mediaId = "${mediaId}_2")
                    )
                )
                mediaMetadata = MediaMetadata(
                    listOf(
                        GalleryItem(
                            mimeType = "image/jpeg",
                            image = GalleryImage(height = 0, width = 0, url = fullUrl, mp4 = null),
                            previews = if (previewUrl.isNotBlank()) listOf(GalleryImage(0, 0, previewUrl, null)) else null,
                            id = mediaId
                        ),
                        GalleryItem(
                            mimeType = "image/jpeg",
                            image = GalleryImage(height = 0, width = 0, url = fullUrl, mp4 = null),
                            previews = if (previewUrl.isNotBlank()) listOf(GalleryImage(0, 0, previewUrl, null)) else null,
                            id = "${mediaId}_2"
                        )
                    )
                )
            }
        }

        // Direct image fallback for feed items with thumbnail only
        if (!isImage && !isVideo && !isGallery && thumbnailAnchor != null && !thumbnailAnchor.hasClass("no_thumbnail")) {
            val thumbSrc = thumbnailAnchor.selectFirst("svg image")?.attr("href")
                ?: thumbnailAnchor.selectFirst("img")?.attr("src")
            if (thumbSrc?.startsWith("/preview/pre/") == true || thumbSrc?.startsWith("/img/") == true) {
                val path = thumbSrc.substringBefore('?')
                val fileName = path.substringAfterLast('/')
                val mediaId = fileName.substringBeforeLast('.')
                val ext = fileName.substringAfterLast('.', "jpg")
                if (mediaId.isNotBlank()) {
                    isImage = true
                    mediaUrl = "https://i.redd.it/$mediaId.$ext"
                }
            }
        }

        val thumbnail: String? = when {
            isRedditGif -> resolve(mediaVideo?.attr("poster")) ?: mediaUrl
            isImage && mediaUrl.isNotBlank() -> mediaUrl
            isGallery -> galleryData?.items?.firstOrNull()?.let { "https://i.redd.it/${it.mediaId}.jpg" }
                ?: thumbnailAnchor?.let { resolve(it.selectFirst("svg image")?.attr("href") ?: it.selectFirst("img")?.attr("src")) }
            isVideoElement -> {
                val poster = mediaVideo?.attr("poster")?.takeIf { it.isNotBlank() }
                if (poster != null) {
                    resolve(poster)
                } else if (thumbnailAnchor != null && !thumbnailAnchor.hasClass("no_thumbnail")) {
                    resolve(
                        thumbnailAnchor.selectFirst("svg image")?.attr("href")
                            ?: thumbnailAnchor.selectFirst("img")?.attr("src")
                    )
                } else {
                    null
                }
            }
            thumbnailAnchor != null && !thumbnailAnchor.hasClass("no_thumbnail") -> {
                resolve(
                    thumbnailAnchor.selectFirst("svg image")?.attr("href")
                        ?: thumbnailAnchor.selectFirst("img")?.attr("src")
                )
            }
            else -> null
        }

        val externalHref = selectFirst("a#post_url")?.attr("href")
            ?: thumbnailAnchor?.takeIf { it.hasClass("no_thumbnail") }?.attr("href")
            ?: thumbnailAnchor?.takeIf { !it.attr("href").startsWith("/r/") && !it.attr("href").startsWith("/comments/") }?.attr("href")
        val isExternal = externalHref?.startsWith("http", ignoreCase = true) == true

        val domain = when {
            isExternal -> externalHref?.toHttpUrlOrNull()?.host.orEmpty()
            isRedditGif -> "i.redd.it"
            isImage -> "i.redd.it"
            isVideo -> if (videoId != null) "v.redd.it" else "reddit.com"
            isGallery -> "reddit.com"
            else -> ""
        }

        val isSelf = !isImage && !isVideo && !isExternal && !isGallery && !isRedditGif
        val canonicalPermalink = canonicalRedditUrl(permalink) ?: permalink

        val urlCandidate = when {
            isRedditGif && gifFullUrl != null -> gifFullUrl
            isImage && mediaUrl.isNotBlank() -> mediaUrl
            isVideo -> {
                when {
                    isExternal && !externalHref.isNullOrBlank() -> externalHref
                    videoId != null -> "https://v.redd.it/$videoId"
                    canonicalPermalink.isNotBlank() -> canonicalPermalink
                    mediaUrl.isNotBlank() -> mediaUrl
                    else -> canonicalPermalink
                }
            }
            isGallery -> {
                if (postId.isNotBlank()) "https://www.reddit.com/gallery/$postId" else canonicalPermalink
            }
            isExternal && !externalHref.isNullOrBlank() -> externalHref
            else -> canonicalPermalink
        }
        val url = if (urlCandidate.isBlank() && postId.isNotBlank()) {
            "https://www.reddit.com/comments/$postId"
        } else {
            urlCandidate
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
            galleryData = galleryData,
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
            mediaMetadata = mediaMetadata,
            isRedditGallery = isGallery,
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
        private val VIDEO_ID_REGEX = Regex("/(?:vid|hls)/([a-zA-Z0-9_]+)")
    }
}
