package com.cosmos.stealth.server

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun testStealthApiYamlReturns404() = testApplication {
        val response = client.get("/stealth-api.yaml")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun testRootEndpointReturns200() = testApplication {
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testValidServicesEndpointReturns200() = testApplication {
        val response = client.get("/api/v1/services")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun testInvalidApiEndpointReturns404() = testApplication {
        val response = client.get("/api/v1/nonexistent")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun testCrawlerEndpointReturns404() = testApplication {
        val response = client.get("/wp-admin.php")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun testRedditCookieJarDiskPersistence() {
        val tempFile = java.io.File.createTempFile("reddit_cookies_test", ".json")
        tempFile.deleteOnExit()

        // 1. Test saving cookies to disk
        val jar1 = com.cosmos.stealth.services.reddit.data.remote.RedditCookieJar(tempFile.absolutePath)
        val url = "https://www.reddit.com/".toHttpUrl()
        val cookie = okhttp3.Cookie.Builder()
            .name("token_v2")
            .value("test_token_value_12345")
            .domain("reddit.com")
            .path("/")
            .build()
        jar1.saveFromResponse(url, listOf(cookie))

        assertTrue(jar1.hasRequiredCookies())
        assertTrue(tempFile.exists() && tempFile.length() > 0)

        // 2. Test loading saved cookies in a new instance
        val jar2 = com.cosmos.stealth.services.reddit.data.remote.RedditCookieJar(tempFile.absolutePath)
        assertTrue(jar2.hasRequiredCookies())
        val loadedCookies = jar2.loadForRequest(url)
        assertTrue(loadedCookies.any { it.name == "token_v2" && it.value == "test_token_value_12345" })

        // 3. Test plaintext key=value file loading
        val textFile = java.io.File.createTempFile("reddit_cookies_text", ".txt")
        textFile.deleteOnExit()
        textFile.writeText("token_v2=seeded_from_browser_999\nover18=1\n")

        val jar3 = com.cosmos.stealth.services.reddit.data.remote.RedditCookieJar(textFile.absolutePath)
        assertTrue(jar3.hasRequiredCookies())
        val textLoaded = jar3.loadForRequest(url)
        assertTrue(textLoaded.any { it.name == "token_v2" && it.value == "seeded_from_browser_999" })
    }

    @Test
    fun testRedditErrorLoggerExtractsJsonReason() {
        val raw = """Client request(https://www.reddit.com/r/all/.json) invalid: 403 Forbidden. Text: "{\"message\": \"Forbidden\", \"error\": 403}""""
        val reason = com.cosmos.stealth.services.reddit.util.RedditErrorLogger.extractReason(raw)
        assertEquals("Forbidden (error: 403)", reason)
    }

    @Test
    fun testRedditErrorLoggerExtractsHtmlBlockReason() {
        val raw = """Client request(https://www.reddit.com/r/all/.json) invalid: 403 Forbidden. Text: "<!DOCTYPE html><html><head><title>Blocked</title></head><body><h1>You've been blocked by network security</h1><p>Abnormal traffic from your IP.</p></body></html>""""
        val reason = com.cosmos.stealth.services.reddit.util.RedditErrorLogger.extractReason(raw)
        assertTrue(reason.contains("Blocked"))
        assertTrue(reason.contains("You've been blocked by network security"))
    }

    @Test
    fun testRedditErrorLoggerExtractsRecaptchaReason() {
        val html = """<!DOCTYPE html><html><head><title>Reddit - Prove your humanity</title></head><body><h1>Prove your humanity</h1><p>Solve the challenge below</p></body></html>"""
        val reason = com.cosmos.stealth.services.reddit.util.RedditErrorLogger.extractReason(html)
        assertTrue(reason.contains("Prove your humanity"))
    }

    @Test
    fun testRedditErrorLoggerDeduplicationAndRecovery() {
        com.cosmos.stealth.services.reddit.util.RedditErrorLogger.resetForTest()
        val errorMsg = """Client request(https://www.reddit.com/r/all/.json) invalid: 403 Forbidden. Text: "{\"message\": \"Forbidden\", \"error\": 403}""""
        
        // Log consecutive failures
        com.cosmos.stealth.services.reddit.util.RedditErrorLogger.logFallback("getFeed", 403, errorMsg, "redredred.065536.xyz")
        com.cosmos.stealth.services.reddit.util.RedditErrorLogger.logFallback("getFeed", 403, errorMsg, "redredred.065536.xyz")
        com.cosmos.stealth.services.reddit.util.RedditErrorLogger.logFallback("getFeed", 403, errorMsg, "redredred.065536.xyz")
        
        // Recover
        com.cosmos.stealth.services.reddit.util.RedditErrorLogger.recordSuccess()
    }
}
