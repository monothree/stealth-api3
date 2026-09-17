package com.cosmos.stealth.services.reddit.data.remote

import com.cosmos.stealth.core.common.util.extension.getLogger
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

// https://gitlab.com/cosmosapps/stealth/-/work_items/141#note_3430084325
class RedditCookieInitializationInterceptor(private val cookieJar: RedditCookieJar) : Interceptor {

    private val logger = getLogger()

    private fun hasRequiredCookies(): Boolean {
        return cookieJar.hasRequiredCookies()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!hasRequiredCookies()) {
            synchronized(this) {
                if (!hasRequiredCookies()) {
                    initializeCookies(chain)
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
                .build()

            var response = chain.proceed(initRequest)
            val initCode = response.code
            val htmlBody = response.body?.string() ?: ""
            response.close()

            val document = Jsoup.parse(htmlBody)
            val scriptTag = document.selectFirst("script[nonce]")
            val scriptContent = scriptTag?.html() ?: ""
            val solutionValue = extractAndExecuteJavaScriptLogic(scriptContent)
            val tokenInput = document.selectFirst("input[name=token]")
            val tokenValue = tokenInput?.attr("value") ?: ""

            if (solutionValue.isBlank() || tokenValue.isBlank()) {
                logger.warn("Reddit JSON cookie circumvention: Could not resolve challenge (solution='{}', token='{}', HTTP {})", solutionValue, tokenValue, initCode)
                return
            }

            logger.info("Reddit JS challenge solved successfully (token length: {}), requesting session cookies...", tokenValue.length)
            val rdtCookieUrl = "https://www.reddit.com/?solution=${solutionValue}&js_challenge=1&token=${tokenValue}&jsc_orig_r="

            val rdtCookieRequest = Request.Builder()
                .url(rdtCookieUrl)
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
}
