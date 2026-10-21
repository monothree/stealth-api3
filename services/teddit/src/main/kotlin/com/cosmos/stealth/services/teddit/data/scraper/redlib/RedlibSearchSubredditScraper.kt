package com.cosmos.stealth.services.teddit.data.scraper.redlib

import com.cosmos.stealth.services.reddit.data.model.AboutChild
import com.cosmos.stealth.services.reddit.data.model.AboutData
import com.cosmos.stealth.services.reddit.data.model.Listing
import com.cosmos.stealth.services.reddit.data.model.ListingData
import kotlinx.coroutines.CoroutineDispatcher
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class RedlibSearchSubredditScraper(
    ioDispatcher: CoroutineDispatcher,
    instanceBaseUrl: String? = null
) : RedlibBaseScraper<Listing>(ioDispatcher, instanceBaseUrl) {

    override suspend fun scrapDocument(document: Document): Listing {
        val children = document.select("a.search_subreddit")
            .toList()
            .filter { it.id() != "more_subreddits" && it.selectFirst("span.search_subreddit_name") != null }
            .map { it.toAbout() }
            .filter { (it as AboutChild).data.displayName.isNotBlank() }

        return Listing(
            KIND,
            ListingData(
                modhash = null,
                dist = null,
                children = children,
                after = extractAfter(),
                before = null
            )
        )
    }

    private fun Element.toAbout(): AboutChild {
        val href = attr("href")
        val prefixedName = selectFirst("span.search_subreddit_name")?.text().orEmpty()
        val name = stripPrefix(prefixedName)
        val description = selectFirst("p.search_subreddit_description")?.text().orEmpty()
        val membersElement = selectFirst("span.search_subreddit_members")
        val subscribers = membersElement?.attr("title")?.let { parseHumanCount(it) }
            ?: membersElement?.text()?.let { parseHumanCount(it) }
        val icon = resolve(selectFirst("div.search_subreddit_left img")?.attr("src")).orEmpty()

        val data = AboutData(
            name = if (name.isNotEmpty()) "t5_$name" else "",
            wikiEnabled = null,
            displayName = name,
            headerImg = null,
            title = name,
            primaryColor = null,
            activeUserCount = null,
            iconImg = icon.ifEmpty { null },
            subscribers = subscribers,
            quarantine = null,
            publicDescriptionHtml = description.ifEmpty { null },
            communityIcon = icon,
            bannerBackgroundImage = "",
            keyColor = null,
            bannerBackgroundColor = null,
            over18 = false,
            descriptionHtml = null,
            url = href,
            created = 0L
        )

        return AboutChild(data)
    }

    companion object {
        private const val KIND = "t5"
    }
}
