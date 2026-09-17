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
}
