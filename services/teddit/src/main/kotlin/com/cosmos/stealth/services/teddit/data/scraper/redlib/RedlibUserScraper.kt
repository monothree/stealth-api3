package com.cosmos.stealth.services.teddit.data.scraper.redlib

import com.cosmos.stealth.core.network.data.scraper.Scraper
import com.cosmos.stealth.services.reddit.data.model.AboutUserChild
import com.cosmos.stealth.services.reddit.data.model.AboutUserData
import com.cosmos.stealth.services.reddit.data.model.Child
import kotlinx.coroutines.CoroutineDispatcher
import org.jsoup.nodes.Document

class RedlibUserScraper(
    ioDispatcher: CoroutineDispatcher,
    instanceBaseUrl: String? = null
) : RedlibBaseScraper<Child>(ioDispatcher, instanceBaseUrl) {

    override suspend fun scrapDocument(document: Document): Child {
        val title = stripPrefix(document.selectFirst("#user_title")?.text().orEmpty())
        val displayName = document.selectFirst("#user_name")?.text()
            ?.let { stripPrefix(it) }
            .orEmpty()
            .ifEmpty { title }

        val icon = resolve(document.selectFirst("img#user_icon")?.attr(Scraper.Selector.Attr.SRC))

        val detailValues = document.select("#user_details > div")
        val karma = detailValues.getOrNull(0)?.text()?.let { parseHumanCount(it) }
            ?: -1

        val created = parseRedlibTime(detailValues.getOrNull(1))

        val data = AboutUserData(
            isSuspended = false,
            isEmployee = false,
            subreddit = null,
            id = null,
            iconImg = icon,
            linkKarma = karma,
            totalKarma = karma,
            name = displayName,
            created = created,
            snoovatarImg = null,
            commentKarma = -1
        )

        return AboutUserChild(data)
    }
}
