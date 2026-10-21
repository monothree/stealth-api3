package com.cosmos.stealth.services.teddit.data.scraper.redlib

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RedlibScraperTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun testScrapPostsWithMediaAndAfter() = runTest(testDispatcher) {
        val html = """
            <!DOCTYPE html>
            <html>
            <body>
            <div id="posts">
                <!-- Image Post -->
                <div class="post" id="post1">
                    <p class="post_header">
                        <a class="post_subreddit" href="/r/pics">r/pics</a>
                        <a class="post_author" href="/u/user1">u/user1</a>
                        <span class="created" title="Oct 13 2026, 08:23:32 UTC">8h ago</span>
                    </p>
                    <h2 class="post_title">
                        <a href="/r/pics/comments/post1/my_dog/">My Dog</a>
                    </h2>
                    <div class="post_media_content">
                        <a href="/img/cute_dog.jpeg" class="post_media_image short"></a>
                    </div>
                    <div class="post_score" title="1234">1.2k</div>
                    <div class="post_footer">
                        <a href="/r/pics/comments/post1/my_dog/" class="post_comments">42 comments</a>
                    </div>
                </div>

                <!-- Video Post with HLS -->
                <div class="post" id="post2">
                    <p class="post_header">
                        <a class="post_subreddit" href="/r/unexpected">r/unexpected</a>
                        <a class="post_author" href="/u/user2">u/user2</a>
                        <span class="created" title="Oct 13 2026, 08:23:32 UTC">8h ago</span>
                    </p>
                    <h2 class="post_title">
                        <a href="/r/unexpected/comments/post2/crazy_jump/">Crazy Jump</a>
                    </h2>
                    <div class="post_media_content">
                        <video class="post_media_video short" controls>
                            <source src="/hls/vid_abc123/HLSPlaylist.m3u8?token=xyz" type="application/vnd.apple.mpegurl" />
                        </video>
                    </div>
                    <div class="post_score" title="5678">5.6k</div>
                    <div class="post_footer">
                        <a href="/r/unexpected/comments/post2/crazy_jump/" class="post_comments">10 comments</a>
                    </div>
                </div>

                <!-- Gallery Post -->
                <div class="post" id="post3">
                    <p class="post_header">
                        <a class="post_subreddit" href="/r/art">r/art</a>
                        <a class="post_author" href="/u/artist">u/artist</a>
                        <span class="created" title="Oct 13 2026, 08:23:32 UTC">8h ago</span>
                    </p>
                    <h2 class="post_title">
                        <a href="/r/art/comments/post3/gallery_art/">Gallery Art</a>
                    </h2>
                    <a class="post_thumbnail" href="/r/art/comments/post3/gallery_art/">
                        <svg><image href="/preview/pre/art_thumb.jpg?width=140&height=105"/></svg>
                        <span>gallery</span>
                    </a>
                    <div class="post_score" title="999">999</div>
                    <div class="post_footer">
                        <a href="/r/art/comments/post3/gallery_art/" class="post_comments">5 comments</a>
                    </div>
                </div>

                <!-- External Link Post -->
                <div class="post" id="post4">
                    <p class="post_header">
                        <a class="post_subreddit" href="/r/news">r/news</a>
                        <a class="post_author" href="/u/reporter">u/reporter</a>
                        <span class="created" title="Oct 13 2026, 08:23:32 UTC">8h ago</span>
                    </p>
                    <h2 class="post_title">
                        <a href="/r/news/comments/post4/breaking_news/">Breaking News</a>
                    </h2>
                    <a id="post_url" href="https://www.bbc.com/news/world-12345">BBC</a>
                    <a class="post_thumbnail" href="https://www.bbc.com/news/world-12345">
                        <svg><image href="/preview/external-pre/bbc_thumb.jpeg?width=140"/></svg>
                    </a>
                    <div class="post_score" title="2500">2.5k</div>
                    <div class="post_footer">
                        <a href="/r/news/comments/post4/breaking_news/" class="post_comments">100 comments</a>
                    </div>
                </div>
            </div>
            <footer>
                <a href="?sort=hot&t=&after=t3_post4" accesskey="N">NEXT</a>
            </footer>
            </body>
            </html>
        """.trimIndent()

        val scraper = RedlibPostScraper(testDispatcher, "redredred.065536.xyz")
        val listing = scraper.scrap(html)

        assertEquals("t3_post4", listing.data.after)
        assertEquals(4, listing.data.children.size)

        // 1. Image Post
        val imagePost = (listing.data.children[0] as com.cosmos.stealth.services.reddit.data.model.PostChild).data
        assertEquals("https://i.redd.it/cute_dog.jpeg", imagePost.url)
        assertEquals("i.redd.it", imagePost.domain)
        assertEquals("https://i.redd.it/cute_dog.jpeg", imagePost.thumbnail)
        assertFalse(imagePost.isSelf)
        assertFalse(imagePost.isVideo)

        // 2. Video Post with HLS
        val videoPost = (listing.data.children[1] as com.cosmos.stealth.services.reddit.data.model.PostChild).data
        assertTrue(videoPost.isVideo)
        assertEquals("v.redd.it", videoPost.domain)
        assertEquals("https://v.redd.it/vid_abc123", videoPost.url)
        assertNotNull(videoPost.media?.redditVideoPreview)
        assertEquals(
            "https://v.redd.it/vid_abc123/HLSPlaylist.m3u8",
            videoPost.media?.redditVideoPreview?.fallbackUrl
        )
        assertEquals(
            "https://v.redd.it/vid_abc123/DASHPlaylist.mpd",
            videoPost.media?.redditVideoPreview?.dashUrl
        )

        // 3. Gallery Post
        val galleryPost = (listing.data.children[2] as com.cosmos.stealth.services.reddit.data.model.PostChild).data
        assertTrue(galleryPost.isRedditGallery == true)
        assertEquals("reddit.com", galleryPost.domain)
        assertEquals("https://www.reddit.com/gallery/post3", galleryPost.url)
        assertEquals("https://i.redd.it/art_thumb.jpg", galleryPost.thumbnail)
        assertEquals(2, galleryPost.galleryData?.items?.size)
        assertEquals("art_thumb", galleryPost.galleryData?.items?.first()?.mediaId)
        assertEquals("https://i.redd.it/art_thumb.jpg", galleryPost.mediaMetadata?.items?.first()?.image?.url)

        // 4. External Post
        val externalPost = (listing.data.children[3] as com.cosmos.stealth.services.reddit.data.model.PostChild).data
        assertEquals("https://www.bbc.com/news/world-12345", externalPost.url)
        assertEquals("www.bbc.com", externalPost.domain)
        assertEquals("https://external-preview.redd.it/bbc_thumb.jpeg?width=140", externalPost.thumbnail)
        assertFalse(externalPost.isSelf)
    }

    @Test
    fun testScrapMultiImageGallery() = runTest(testDispatcher) {
        val html = """
            <!DOCTYPE html>
            <html><body>
            <div id="posts">
                <div class="post" id="post_gal">
                    <p class="post_header">
                        <a class="post_subreddit" href="/r/pics">r/pics</a>
                        <a class="post_author" href="/u/photographer">u/photographer</a>
                        <span class="created" title="Oct 13 2026, 08:23:32 UTC">8h ago</span>
                    </p>
                    <h1 class="post_title">
                        <a href="/r/pics/comments/post_gal/my_photos/">My Photos</a>
                    </h1>
                    <div class="gallery">
                        <figure>
                            <a href="/preview/pre/img1.jpg?width=3088&format=pjpg"><img src="/preview/pre/img1.jpg"/></a>
                            <figcaption><p>First photo</p></figcaption>
                        </figure>
                        <figure>
                            <a href="/preview/pre/img2.png?width=2000"><img src="/preview/pre/img2.png"/></a>
                            <figcaption><p>Second photo</p></figcaption>
                        </figure>
                    </div>
                    <div class="post_score" title="100">100</div>
                </div>
            </div>
            </body></html>
        """.trimIndent()

        val scraper = RedlibPostScraper(testDispatcher, "redredred.065536.xyz")
        val listing = scraper.scrap(html)
        assertEquals(1, listing.data.children.size)

        val post = (listing.data.children[0] as com.cosmos.stealth.services.reddit.data.model.PostChild).data
        assertTrue(post.isRedditGallery == true)
        assertEquals("reddit.com", post.domain)
        assertEquals("https://www.reddit.com/gallery/post_gal", post.url)
        assertEquals(2, post.galleryData?.items?.size)

        val item1 = post.galleryData?.items?.get(0)
        assertEquals("img1", item1?.mediaId)
        assertEquals("First photo", item1?.caption)
        assertEquals("https://i.redd.it/img1.jpg", post.mediaMetadata?.items?.get(0)?.image?.url)

        val item2 = post.galleryData?.items?.get(1)
        assertEquals("img2", item2?.mediaId)
        assertEquals("Second photo", item2?.caption)
        assertEquals("https://i.redd.it/img2.png", post.mediaMetadata?.items?.get(1)?.image?.url)
    }

    @Test
    fun testResolveUrls() = runTest(testDispatcher) {
        val scraper = RedlibPostScraper(testDispatcher, "custom.redlib.instance")

        // /img/ -> i.redd.it
        assertEquals("https://i.redd.it/photo.png", scraper.resolve("/img/photo.png"))

        // /preview/pre/ -> preview.redd.it
        assertEquals("https://preview.redd.it/thumb.png?w=100", scraper.resolve("/preview/pre/thumb.png?w=100"))

        // /preview/external-pre/ -> external-preview.redd.it
        assertEquals("https://external-preview.redd.it/ext.jpg?w=100", scraper.resolve("/preview/external-pre/ext.jpg?w=100"))

        // /style/ -> styles.redditmedia.com
        assertEquals("https://styles.redditmedia.com/t5_123/icon.jpg", scraper.resolve("/style/t5_123/icon.jpg"))

        // Other relative paths -> instance URL
        assertEquals("https://custom.redlib.instance/hls/stream.m3u8", scraper.resolve("/hls/stream.m3u8"))

        // Absolute URLs -> unchanged
        assertEquals("https://example.com/test", scraper.resolve("https://example.com/test"))
    }

    @Test
    fun testSubredditSearchFilteringAndIcon() = runTest(testDispatcher) {
        val html = """
            <!DOCTYPE html>
            <html><body>
            <div id="search_subreddits">
                <a href="?q=kotlin&sort=relevance" class="search_subreddit" id="more_subreddits">← Back to post results</a>
                <a href="/r/Kotlin/" class="search_subreddit">
                    <div class="search_subreddit_left"><img src="/style/t5_2so2r/styles/icon.jpg"></div>
                    <div class="search_subreddit_right">
                        <p class="search_subreddit_header">
                            <span class="search_subreddit_name">r/Kotlin</span>
                            <span class="search_subreddit_members" title="100000 Members">100k Members</span>
                        </p>
                        <p class="search_subreddit_description">Kotlin language</p>
                    </div>
                </a>
            </div>
            </body></html>
        """.trimIndent()

        val scraper = RedlibSearchSubredditScraper(testDispatcher, "redredred.065536.xyz")
        val listing = scraper.scrap(html)

        assertEquals(1, listing.data.children.size)
        val about = (listing.data.children[0] as com.cosmos.stealth.services.reddit.data.model.AboutChild).data
        assertEquals("Kotlin", about.displayName)
        assertEquals("https://styles.redditmedia.com/t5_2so2r/styles/icon.jpg", about.iconImg)
        assertEquals("https://styles.redditmedia.com/t5_2so2r/styles/icon.jpg", about.communityIcon)
    }

    @Test
    fun testSubredditIconExtraction() = runTest(testDispatcher) {
        val html = """
            <!DOCTYPE html>
            <html><body>
            <div id="sub_meta">
                <img id="sub_icon" src="/style/t5_2so2r/styles/communityIcon_123.jpg">
                <h1 id="sub_title">Kotlin</h1>
                <p id="sub_name">r/Kotlin</p>
                <p id="sub_description">Discussion about Kotlin</p>
                <div id="sub_details">
                    <div title="100000 Members">100k Members</div>
                    <div title="500 Active">500 Online</div>
                </div>
            </div>
            </body></html>
        """.trimIndent()

        val scraper = RedlibSubredditScraper(testDispatcher, "redredred.065536.xyz")
        val about = (scraper.scrap(html) as com.cosmos.stealth.services.reddit.data.model.AboutChild).data

        assertEquals("Kotlin", about.displayName)
        assertEquals("https://styles.redditmedia.com/t5_2so2r/styles/communityIcon_123.jpg", about.iconImg)
        assertEquals("https://styles.redditmedia.com/t5_2so2r/styles/communityIcon_123.jpg", about.communityIcon)
    }

    @Test
    fun testScrapCmafVideo() = runTest(testDispatcher) {
        val html = """
            <!DOCTYPE html>
            <html><body>
            <div id="posts">
                <div class="post" id="post_cmaf">
                    <p class="post_header">
                        <a class="post_subreddit" href="/r/aww">r/aww</a>
                        <a class="post_author" href="/u/petlover">u/petlover</a>
                        <span class="created" title="Oct 17 2026, 15:00:00 UTC">1h ago</span>
                    </p>
                    <h2 class="post_title">
                        <a href="/r/aww/comments/post_cmaf/cute_cat/">Cute Cat</a>
                    </h2>
                    <div class="post_media_content">
                        <video class="post_media_video short" width="1080" height="1920" controls
                               src="/vid/1a2f44fpx1wh1/CMAF/1080.mp4"
                               poster="/preview/external-pre/thumb.jpg">
                            <a href="/vid/1a2f44fpx1wh1/CMAF/1080.mp4">Video</a>
                        </video>
                    </div>
                    <div class="post_score" title="500">500</div>
                </div>
            </div>
            </body></html>
        """.trimIndent()

        val scraper = RedlibPostScraper(testDispatcher, "redredred.065536.xyz")
        val listing = scraper.scrap(html)
        val post = (listing.data.children[0] as com.cosmos.stealth.services.reddit.data.model.PostChild).data

        assertTrue(post.isVideo)
        assertEquals("v.redd.it", post.domain)
        assertEquals("https://v.redd.it/1a2f44fpx1wh1", post.url)
        assertEquals("https://v.redd.it/1a2f44fpx1wh1/HLSPlaylist.m3u8", post.media?.redditVideoPreview?.fallbackUrl)
        assertEquals("https://v.redd.it/1a2f44fpx1wh1/DASHPlaylist.mpd", post.media?.redditVideoPreview?.dashUrl)
        assertFalse(post.media?.redditVideoPreview?.isGif == true)
    }

    @Test
    fun testScrapGifPost() = runTest(testDispatcher) {
        val html = """
            <!DOCTYPE html>
            <html><body>
            <div id="posts">
                <div class="post" id="post_gif">
                    <p class="post_header">
                        <a class="post_subreddit" href="/r/gifs">r/gifs</a>
                        <a class="post_author" href="/u/artist">u/artist</a>
                        <span class="created" title="Nov 03 2024, 20:12:25 UTC">Nov 03 '24</span>
                    </p>
                    <h2 class="post_title">
                        <a href="/r/gifs/comments/post_gif/cool_animation/">Cool Animation</a>
                    </h2>
                    <div class="post_media_content">
                        <video autoplay="" class="post_media_video short post_media_gif" height="400" width="640"
                               loop="" muted="" playsinline=""
                               poster="/preview/pre/5apc9mfquqyd1.gif?format=png8&s=700b"
                               src="/preview/pre/5apc9mfquqyd1.gif?format=mp4&s=df6c">
                            <a href="/preview/pre/5apc9mfquqyd1.gif?format=mp4&s=df6c">GIF</a>
                        </video>
                    </div>
                    <div class="post_score" title="1200">1.2k</div>
                </div>
            </div>
            </body></html>
        """.trimIndent()

        val scraper = RedlibPostScraper(testDispatcher, "redredred.065536.xyz")
        val listing = scraper.scrap(html)
        val post = (listing.data.children[0] as com.cosmos.stealth.services.reddit.data.model.PostChild).data

        assertFalse(post.isVideo)
        assertEquals("i.redd.it", post.domain)
        assertEquals("https://i.redd.it/5apc9mfquqyd1.gif", post.url)
        assertEquals("https://preview.redd.it/5apc9mfquqyd1.gif?format=png8&s=700b", post.thumbnail)
    }
}
