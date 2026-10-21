package com.cosmos.stealth.services.teddit.data.scraper.redlib

import com.cosmos.stealth.services.reddit.data.model.AboutChild
import com.cosmos.stealth.services.reddit.data.model.AboutData
import com.cosmos.stealth.services.reddit.data.model.Child
import kotlinx.coroutines.CoroutineDispatcher
import org.jsoup.nodes.Document

class RedlibSubredditScraper(
    ioDispatcher: CoroutineDispatcher,
    instanceBaseUrl: String? = null
) : RedlibBaseScraper<Child>(ioDispatcher, instanceBaseUrl) {

    override suspend fun scrapDocument(document: Document): Child {
        val title = stripPrefix(document.selectFirst("#sub_title")?.text().orEmpty())
        val prefixedName = document.selectFirst("#sub_name")?.text().orEmpty()
        val name = stripPrefix(prefixedName).ifEmpty { title }
        val description = document.selectFirst("#sub_description")?.text().orEmpty()

        val icon = resolve(document.selectFirst("img#sub_icon")?.attr("src")).orEmpty()

        val detailValues = document.select("#sub_details > div")
        val subscribers = detailValues.getOrNull(0)?.attr("title")?.let { parseHumanCount(it) }
            ?: detailValues.getOrNull(0)?.text()?.let { parseHumanCount(it) }
        val activeUsers = detailValues.getOrNull(1)?.attr("title")?.let { parseHumanCount(it) }
            ?: detailValues.getOrNull(1)?.text()?.let { parseHumanCount(it) }

        val sidebarHtml = document.selectFirst("#sidebar_contents")
            ?.selectFirst("div.md")
            ?.outerHtml()

        val data = AboutData(
            name = if (name.isNotEmpty()) "t5_$name" else "",
            wikiEnabled = null,
            displayName = name,
            headerImg = null,
            title = title.ifEmpty { name },
            primaryColor = null,
            activeUserCount = activeUsers,
            iconImg = icon.ifEmpty { null },
            subscribers = subscribers,
            quarantine = null,
            publicDescriptionHtml = description.ifEmpty { null },
            communityIcon = icon,
            bannerBackgroundImage = "",
            keyColor = null,
            bannerBackgroundColor = null,
            over18 = null,
            descriptionHtml = sidebarHtml,
            url = "/r/$name/",
            created = 0L
        )

        return AboutChild(data)
    }
}
