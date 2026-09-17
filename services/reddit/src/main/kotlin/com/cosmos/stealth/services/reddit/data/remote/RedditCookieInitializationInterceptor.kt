package com.cosmos.stealth.services.reddit.data.remote

import com.cosmos.stealth.core.common.util.extension.getLogger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

// https://gitlab.com/cosmosapps/stealth/-/work_items/141#note_3430084325
class RedditCookieInitializationInterceptor(private val cookieJar: RedditCookieJar) : Interceptor {

    private val logger = getLogger()

    @Volatile
    private var cooldownUntilMs: Long = 0L

    private fun hasRequiredCookies(): Boolean {
        return cookieJar.hasRequiredCookies()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!hasRequiredCookies()) {
            if (System.currentTimeMillis() >= cooldownUntilMs) {
                synchronized(this) {
                    if (!hasRequiredCookies() && System.currentTimeMillis() >= cooldownUntilMs) {
                        initializeCookies(chain)
                    }
                }
            }
        }

        return chain.proceed(chain.request())
    }

    private fun initializeCookies(chain: Interceptor.Chain) {
        try {
            logger.info("Initializing Reddit JSON cookie circumvention (solving JS challenge)...")
            val jsChallengeUrl = "https://www.reddit.com/"

            val initRequest = Request.Builder()
                .url(jsChallengeUrl)
                .header("User-Agent", RedditBrowserHeadersInterceptor.BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Linux\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .build()

            var response = chain.proceed(initRequest)
            val initCode = response.code
            val htmlBody = response.body?.string() ?: ""
            response.close()

            if (htmlBody.contains("recaptcha") || htmlBody.contains("Prove your humanity")) {
                logger.warn("Reddit served interactive reCAPTCHA ('Prove your humanity'). Cloud IP is rate-limited. Backing off circumvention for 15 minutes.")
                cooldownUntilMs = System.currentTimeMillis() + (15 * 60 * 1000L)
                return
            }

            val document = Jsoup.parse(htmlBody)
            val scriptTag = document.selectFirst("script[nonce]")
            val scriptContent = scriptTag?.html() ?: ""
            val solutionValue = extractAndExecuteJavaScriptLogic(scriptContent)
            val tokenValue = extractToken(document, htmlBody)

            if (solutionValue.isBlank() || tokenValue.isBlank()) {
                logger.warn("Reddit JSON cookie circumvention: Could not resolve challenge (solution='{}', token='{}', HTTP {})", solutionValue, tokenValue, initCode)
                val sanitizedSnippet = htmlBody.replace("\\s+".toRegex(), " ").take(1000)
                logger.warn("Reddit challenge response HTML snippet (first 1000 chars): {}", sanitizedSnippet)
                return
            }

            logger.info("Reddit JS challenge solved successfully (token length: {}), requesting session cookies...", tokenValue.length)
            val rdtCookieUrl = "https://www.reddit.com/?solution=${solutionValue}&js_challenge=1&token=${tokenValue}&jsc_orig_r="

            val rdtCookieRequest = Request.Builder()
                .url(rdtCookieUrl)
                .header("User-Agent", RedditBrowserHeadersInterceptor.BROWSER_USER_AGENT)
                .header("Referer", "https://www.reddit.com/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Sec-Ch-Ua", "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Linux\"")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "same-origin")
                .header("Sec-Fetch-User", "?1")
                .build()

            response = chain.proceed(rdtCookieRequest)
            val statusCode = response.code
            response.close()

            if (hasRequiredCookies()) {
                logger.info("Reddit JSON cookie circumvention status: SUCCESS (token_v2 acquired, HTTP {})", statusCode)
            } else {
                logger.warn("Reddit JSON cookie circumvention status: FAILED (token_v2 not present in cookie jar, HTTP {})", statusCode)
            }

        } catch (e: Exception) {
            logger.error("Reddit JSON cookie circumvention status: ERROR - {}", e.message, e)
        }
    }

    private fun extractAndExecuteJavaScriptLogic(scriptContent: String): String {
        try {
            val asyncPattern = """await\(async\s+(\w+)=>(.+?)\)\("([^"]+)"\)""".toRegex()
            val matchResult = asyncPattern.find(scriptContent)

            if (matchResult != null) {
                val (paramName, operation, baseValue) = matchResult.destructured
                val result = executeOperation(paramName, operation, baseValue)
                return result
            } else {
                logger.warn("Could not find async pattern in script")
                return ""
            }
        } catch (e: Exception) {
            logger.error("Failed to extract and execute JS logic", e)
            return ""
        }
    }

    private fun executeOperation(paramName: String, operation: String, baseValue: String): String {
        return when {
            operation.contains("+") && !operation.contains("\"") && !operation.contains("'") -> {
                val parts = operation.split("+").map { it.trim() }
                if (parts.all { it == paramName }) {
                    baseValue + baseValue
                } else {
                    baseValue
                }
            }
            else -> baseValue
        }
    }

    private fun extractToken(document: org.jsoup.nodes.Document, htmlBody: String): String {
        // 1. Try common input tag selectors
        val inputSelectors = listOf(
            "input[name=token]",
            "input[name=csrf_token]",
            "input[name=r_token]",
            "input[name*=token]",
            "input#token"
        )
        for (selector in inputSelectors) {
            val element = document.selectFirst(selector)
            val value = element?.attr("value")?.trim()
            if (!value.isNullOrBlank()) {
                logger.debug("Extracted token using selector '{}'", selector)
                return value
            }
        }

        // 2. Try meta tags
        val metaSelectors = listOf(
            "meta[name=csrf-token]",
            "meta[name=token]",
            "meta[name*=token]"
        )
        for (selector in metaSelectors) {
            val element = document.selectFirst(selector)
            val value = element?.attr("content")?.trim()
            if (!value.isNullOrBlank()) {
                logger.debug("Extracted token using meta selector '{}'", selector)
                return value
            }
        }

        // 3. Regex on raw HTML for input tags (handles non-standard HTML / dynamic templates)
        val inputRegexes = listOf(
            """name=["']token["'][^>]*value=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """value=["']([^"']+)["'][^>]*name=["']token["']""".toRegex(RegexOption.IGNORE_CASE),
            """name=["']csrf_token["'][^>]*value=["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE),
            """value=["']([^"']+)["'][^>]*name=["']csrf_token["']""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (regex in inputRegexes) {
            val match = regex.find(htmlBody)
            if (match != null && match.groupValues[1].isNotBlank()) {
                logger.debug("Extracted token using input regex")
                return match.groupValues[1].trim()
            }
        }

        // 4. Regex for JavaScript variables in script tags or inline JS
        val jsRegexes = listOf(
            """["']?token["']?\s*[:=]\s*["']([a-zA-Z0-9_\-\.]{10,})["']""".toRegex(),
            """["']?csrf_token["']?\s*[:=]\s*["']([a-zA-Z0-9_\-\.]{10,})["']""".toRegex(),
            """token=([a-zA-Z0-9_\-\.]{10,})""".toRegex()
        )
        for (regex in jsRegexes) {
            val match = regex.find(htmlBody)
            if (match != null && match.groupValues[1].isNotBlank()) {
                logger.debug("Extracted token using JS variable regex")
                return match.groupValues[1].trim()
            }
        }

        return ""
    }
}
