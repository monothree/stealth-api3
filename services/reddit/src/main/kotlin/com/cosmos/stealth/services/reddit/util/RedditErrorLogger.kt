package com.cosmos.stealth.services.reddit.util

import com.cosmos.stealth.core.common.util.extension.getLogger
import org.jsoup.Jsoup
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object RedditErrorLogger {

    private val logger = getLogger()

    // Deduplication tracking
    private val lastStatusCode = AtomicReference<Int?>(null)
    private val lastReason = AtomicReference<String?>(null)
    private val lastOperation = AtomicReference<String?>(null)
    private val repeatCount = AtomicInteger(0)
    private val lastLogTimeMs = AtomicReference<Long>(0L)

    // Configuration
    val isVerbose: Boolean
        get() {
            val env = System.getenv("REDDIT_VERBOSE_ERRORS")
                ?: System.getenv("STEALTH_LOG_VERBOSE_ERRORS")
                ?: System.getProperty("reddit.verbose.errors")
                ?: System.getProperty("stealth.log.verbose.errors")
            return env.equals("true", ignoreCase = true) || env.equals("1", ignoreCase = true)
        }

    val isDeduplicationEnabled: Boolean
        get() {
            val env = System.getenv("REDDIT_HIDE_REPEAT_ERRORS")
                ?: System.getenv("REDDIT_DEDUPLICATE_LOGS")
                ?: System.getProperty("reddit.hide.repeat.errors")
                ?: System.getProperty("reddit.deduplicate.logs")
            return env?.let { it.equals("true", ignoreCase = true) || it.equals("1", ignoreCase = true) } ?: true
        }

    val maxSnippetLength: Int
        get() {
            val env = System.getenv("REDDIT_LOG_BODY_MAX_LEN") ?: System.getProperty("reddit.log.body.max.len")
            return env?.toIntOrNull() ?: 300
        }

    private const val REPEAT_INTERVAL_MS = 300_000L // 5 minutes
    private val REPEAT_MILESTONES = intArrayOf(10, 50, 100, 250, 500, 1000)

    /**
     * Extracts a neat, human-readable reason from a raw HTTP error string,
     * Ktor ClientRequestException message, HTML page, or JSON payload.
     */
    fun extractReason(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown error"

        // 1. Check if wrapped in Ktor's ClientRequestException ("... Text: \"...\"")
        val (statusSnippet, textBody) = if (raw.contains(". Text: \"")) {
            val statusPart = raw.substringBefore(". Text: \"").substringAfter("invalid: ").trim()
            val afterText = raw.substringAfter(". Text: \"")
            val body = if (afterText.endsWith("\"")) afterText.dropLast(1) else afterText
            statusPart to body
        } else {
            "" to raw
        }

        val trimmedBody = textBody.trim()
        if (trimmedBody.isBlank()) {
            return if (statusSnippet.isNotBlank()) statusSnippet else sanitize(raw)
        }

        // 2. Unescape escaped characters if present from Ktor's formatting
        val unescaped = trimmedBody
            .replace("\\\"", "\"")
            .replace("\\n", " ")
            .replace("\\r", " ")
            .replace("\\t", " ")
            .trim()

        // 3. Try parsing as HTML if HTML markup is present
        if (unescaped.contains("<html", ignoreCase = true) ||
            unescaped.contains("<!DOCTYPE", ignoreCase = true) ||
            unescaped.contains("<body", ignoreCase = true) ||
            unescaped.contains("<title", ignoreCase = true) ||
            unescaped.contains("</", ignoreCase = true)
        ) {
            try {
                val doc = Jsoup.parse(unescaped)
                val rawTitle = doc.title().trim()
                val title = if (rawTitle.equals("Reddit", ignoreCase = true)) "" else rawTitle

                // Look for prominent headings or error blocks
                val heading = doc.selectFirst("h1, h2, h3, .error, #error, .message, .header")?.text()?.trim()
                val paragraph = doc.selectFirst("p")?.text()?.trim()

                val parts = mutableListOf<String>()
                if (title.isNotBlank()) {
                    parts.add(title)
                }
                if (!heading.isNullOrBlank() && !parts.any { it.contains(heading, ignoreCase = true) }) {
                    parts.add(heading)
                }
                if (!paragraph.isNullOrBlank() && !parts.any { it.contains(paragraph, ignoreCase = true) || paragraph.contains(it, ignoreCase = true) }) {
                    parts.add(paragraph)
                }

                if (parts.isNotEmpty()) {
                    return sanitize(parts.joinToString(": "))
                }

                val bodyText = doc.body().text().trim()
                if (bodyText.isNotBlank()) {
                    return sanitize(bodyText)
                }
            } catch (_: Exception) {
                // Ignore parse failures
            }
        }

        // 4. Try parsing as JSON if JSON structure is present
        if (unescaped.startsWith("{") && unescaped.endsWith("}")) {
            val jsonReason = parseJsonReason(unescaped)
            if (jsonReason != null) {
                return sanitize(jsonReason)
            }
        }

        // 5. Fallback to sanitized plain text
        return sanitize(unescaped)
    }

    private fun parseJsonReason(json: String): String? {
        return try {
            val message = extractJsonField(json, "message")
            val reason = extractJsonField(json, "reason")
            val explanation = extractJsonField(json, "explanation")
            val error = extractJsonField(json, "error")

            when {
                !reason.isNullOrBlank() && !explanation.isNullOrBlank() -> "$reason: $explanation"
                !reason.isNullOrBlank() && !message.isNullOrBlank() -> "$reason: $message"
                !message.isNullOrBlank() && !error.isNullOrBlank() -> "$message (error: $error)"
                !message.isNullOrBlank() -> message
                !reason.isNullOrBlank() -> reason
                !explanation.isNullOrBlank() -> explanation
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        val pattern = "\"$field\"\\s*:\\s*(?:\"([^\"]+)\"|(\\d+))".toRegex()
        val match = pattern.find(json) ?: return null
        return match.groupValues[1].ifEmpty { match.groupValues[2] }
    }

    private fun sanitize(text: String): String {
        val singleLine = text.replace("\\s+".toRegex(), " ").trim()
        val maxLen = if (isVerbose) singleLine.length else maxSnippetLength
        return if (singleLine.length > maxLen) {
            singleLine.take(maxLen) + "..."
        } else {
            singleLine
        }
    }

    /**
     * Log a Reddit failure with smart deduplication.
     */
    fun logFallback(
        operation: String,
        statusCode: Int?,
        rawError: String?,
        fallbackTarget: String
    ) {
        val cleanReason = extractReason(rawError)
        val now = System.currentTimeMillis()

        if (!isDeduplicationEnabled || isVerbose) {
            if (isVerbose && !rawError.isNullOrBlank()) {
                logger.warn(
                    "Reddit API {} failed (status: {}, reason: '{}', raw: '{}'), falling back to Redlib ({})",
                    operation, statusCode, cleanReason, sanitize(rawError), fallbackTarget
                )
            } else {
                logger.warn(
                    "Reddit API {} failed (status: {}, reason: '{}'), falling back to Redlib ({})",
                    operation, statusCode, cleanReason, fallbackTarget
                )
            }
            return
        }

        val prevStatus = lastStatusCode.get()
        val prevReason = lastReason.get()

        val isSameError = (statusCode == prevStatus && cleanReason == prevReason)

        if (isSameError) {
            val count = repeatCount.incrementAndGet()
            val lastLog = lastLogTimeMs.get()
            val timePassed = (now - lastLog) >= REPEAT_INTERVAL_MS
            val isMilestone = REPEAT_MILESTONES.contains(count)

            if (timePassed || isMilestone) {
                lastLogTimeMs.set(now)
                logger.warn(
                    "Reddit API still failing (status: {}, repeated {}x, reason: '{}'), falling back to Redlib ({})",
                    statusCode, count, cleanReason, fallbackTarget
                )
            }
        } else {
            val prevCount = repeatCount.getAndSet(0)
            if (prevCount > 0 && prevStatus != null) {
                logger.info(
                    "Reddit API previous error (status: {}) repeated {} times before changing.",
                    prevStatus, prevCount
                )
            }

            lastStatusCode.set(statusCode)
            lastReason.set(cleanReason)
            lastOperation.set(operation)
            lastLogTimeMs.set(now)

            logger.warn(
                "Reddit API {} failed (status: {}, reason: '{}'), falling back to Redlib ({})",
                operation, statusCode, cleanReason, fallbackTarget
            )
        }
    }

    /**
     * Called when a Reddit API request succeeds, allowing repeat counters to reset
     * and logging recovery when coming out of a failure streak.
     */
    fun recordSuccess() {
        val count = repeatCount.getAndSet(0)
        val prevStatus = lastStatusCode.getAndSet(null)
        lastReason.set(null)
        lastOperation.set(null)

        if (count > 0 && prevStatus != null) {
            logger.info(
                "Reddit API connection recovered to healthy state after {} failed requests (previous status: {}).",
                count, prevStatus
            )
        }
    }

    /**
     * Reset tracker state (mainly for testing).
     */
    fun resetForTest() {
        lastStatusCode.set(null)
        lastReason.set(null)
        lastOperation.set(null)
        repeatCount.set(0)
        lastLogTimeMs.set(0L)
    }
}
