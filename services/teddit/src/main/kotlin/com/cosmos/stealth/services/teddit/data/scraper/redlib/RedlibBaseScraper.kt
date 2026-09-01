package com.cosmos.stealth.services.teddit.data.scraper.redlib

import com.cosmos.stealth.core.common.util.extension.toSeconds
import com.cosmos.stealth.core.network.data.scraper.Scraper
import kotlinx.coroutines.CoroutineDispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

abstract class RedlibBaseScraper<Result>(
    ioDispatcher: CoroutineDispatcher,
    protected val instanceBaseUrl: String? = null
) : Scraper<Result>(ioDispatcher) {

    private val baseHttpUrl: HttpUrl? by lazy {
        instanceBaseUrl?.toHttpUrlOrNull()
    }

    protected fun parseScore(element: Element?): Int {
        if (element == null) return 0
        element.attr("title").trim().toIntOrNull()?.let { return it }
        return parseHumanCount(element.text()) ?: 0
    }

    protected fun parseHumanCount(text: String): Int? {
        val cleaned = text.trim().replace(",", "")
        cleaned.toIntOrNull()?.let { return it }
        val match = HUMAN_COUNT.find(cleaned) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val suffix = match.groupValues[2].lowercase()
        val multiplier = when (suffix) {
            "k" -> 1_000.0
            "m" -> 1_000_000.0
            "b" -> 1_000_000_000.0
            else -> 1.0
        }
        return (value * multiplier).toInt()
    }

    protected fun parseRedlibTime(element: Element?): Long {
        if (element == null) return 0L
        val raw = element.attr("title").trim().ifEmpty { element.text().trim() }
        if (raw.isEmpty()) return 0L
        val time = parseDate(raw, REDLIB_DATETIME_FORMAT)
            ?: parseDate(raw, REDLIB_SHORT_DATE_FORMAT)
        return time?.toSeconds() ?: 0L
    }

    private fun parseDate(input: String, pattern: String): Long? {
        return try {
            SimpleDateFormat(pattern, Locale.ENGLISH).parse(input)?.time
        } catch (e: ParseException) {
            null
        }
    }

    protected fun extractAfter(): String? {
        val anchor = document?.selectFirst("a[accesskey=N]")
            ?: document?.select("footer a")?.firstOrNull { it.text().equals("NEXT", ignoreCase = true) }
            ?: return null
        val href = anchor.attr("href")
        val parsed = href.toHttpUrlOrNull() ?: resolve(href)?.toHttpUrlOrNull()
        return parsed?.queryParameter("after")
    }

    protected fun resolve(href: String?): String? {
        if (href.isNullOrBlank()) return null
        return when {
            href.startsWith("http://", ignoreCase = true) ||
                href.startsWith("https://", ignoreCase = true) -> href
            href.startsWith("//") -> "https:$href"
            else -> {
                val base = baseHttpUrl ?: return href
                base.resolve(href)?.toString() ?: href
            }
        }
    }

    protected fun stripPrefix(value: String): String {
        return value.removePrefix("u/").removePrefix("r/").trim()
    }

    protected fun resolveAbsolute(href: String?): String? {
        return resolve(href)
    }

    protected fun canonicalRedditUrl(href: String?): String? {
        if (href.isNullOrBlank()) return null
        return when {
            href.startsWith("/r/", ignoreCase = true) ||
                href.startsWith("/u/", ignoreCase = true) ||
                href.startsWith("/user/", ignoreCase = true) ||
                href.startsWith("/comments/", ignoreCase = true) ||
                href.startsWith("/gallery/", ignoreCase = true) -> "https://www.reddit.com$href"
            else -> href
        }
    }

    protected fun isRedditMessageUrl(href: String?): Boolean {
        return href?.startsWith("/message/", ignoreCase = true) == true
    }

    companion object {
        private const val REDLIB_DATETIME_FORMAT = "MMM dd yyyy, HH:mm:ss zzz"
        private const val REDLIB_SHORT_DATE_FORMAT = "MMM dd ''yy"
        private val HUMAN_COUNT = Regex("(-?\\d+(?:\\.\\d+)?)(?:\\s*([kmb])\\b|\\b)", RegexOption.IGNORE_CASE)
    }
}
