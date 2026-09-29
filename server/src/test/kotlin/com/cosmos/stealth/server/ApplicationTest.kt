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

    @Test
    fun testForwardProxyConfigParseHttp() {
        val cfg = com.cosmos.stealth.core.network.util.ForwardProxyConfig.parse("http://127.0.0.1:8080")
        kotlin.test.assertNotNull(cfg)
        assertEquals("http", cfg.scheme)
        assertEquals("127.0.0.1", cfg.host)
        assertEquals(8080, cfg.port)
        kotlin.test.assertNull(cfg.username)
        kotlin.test.assertNull(cfg.password)
        assertEquals(java.net.Proxy.Type.HTTP, cfg.proxyType)
        assertEquals("http://127.0.0.1:8080", cfg.toMaskedString())
    }

    @Test
    fun testForwardProxyConfigParseSocks5WithAuth() {
        val cfg = com.cosmos.stealth.core.network.util.ForwardProxyConfig.parse("socks5://myuser:mypass123@proxy.example.com:1080")
        kotlin.test.assertNotNull(cfg)
        assertEquals("socks5", cfg.scheme)
        assertEquals("proxy.example.com", cfg.host)
        assertEquals(1080, cfg.port)
        assertEquals("myuser", cfg.username)
        assertEquals("mypass123", cfg.password)
        assertEquals(java.net.Proxy.Type.SOCKS, cfg.proxyType)
        assertEquals("socks5://myuser:****@proxy.example.com:1080", cfg.toMaskedString())
    }

    @Test
    fun testForwardProxyConfigParseSocks5hUrlEncoded() {
        val cfg = com.cosmos.stealth.core.network.util.ForwardProxyConfig.parse("socks5h://admin:p%40ssword@10.0.0.1:9050/")
        kotlin.test.assertNotNull(cfg)
        assertEquals("socks5h", cfg.scheme)
        assertEquals("10.0.0.1", cfg.host)
        assertEquals(9050, cfg.port)
        assertEquals("admin", cfg.username)
        assertEquals("p@ssword", cfg.password)
        assertEquals(java.net.Proxy.Type.SOCKS, cfg.proxyType)
    }

    @Test
    fun testForwardProxyConfigParseHostPortOnly() {
        val cfg = com.cosmos.stealth.core.network.util.ForwardProxyConfig.parse("proxy.internal:3128")
        kotlin.test.assertNotNull(cfg)
        assertEquals("http", cfg.scheme)
        assertEquals("proxy.internal", cfg.host)
        assertEquals(3128, cfg.port)
        assertEquals(java.net.Proxy.Type.HTTP, cfg.proxyType)
    }

    @Test
    fun testForwardProxyConfigParseIpv6() {
        val cfg = com.cosmos.stealth.core.network.util.ForwardProxyConfig.parse("socks5://[::1]:1080")
        kotlin.test.assertNotNull(cfg)
        assertEquals("::1", cfg.host)
        assertEquals(1080, cfg.port)
        assertEquals(java.net.Proxy.Type.SOCKS, cfg.proxyType)
    }

    @Test
    fun testForwardProxyConfigNullAndBlank() {
        kotlin.test.assertNull(com.cosmos.stealth.core.network.util.ForwardProxyConfig.parse(null))
        kotlin.test.assertNull(com.cosmos.stealth.core.network.util.ForwardProxyConfig.parse(""))
        kotlin.test.assertNull(com.cosmos.stealth.core.network.util.ForwardProxyConfig.parse("   "))
    }

    @Test
    fun testForwardProxyConfigResolutionWithSystemProperties() {
        System.setProperty("forward.proxy", "socks5://global.proxy:1080")
        try {
            val global = com.cosmos.stealth.core.network.util.ForwardProxyConfig.resolve(null)
            kotlin.test.assertNotNull(global)
            assertEquals("global.proxy", global.host)
            assertEquals(java.net.Proxy.Type.SOCKS, global.proxyType)

            // Service specific override
            System.setProperty("reddit.forward.proxy", "http://reddit.proxy:8080")
            val reddit = com.cosmos.stealth.core.network.util.ForwardProxyConfig.resolve("reddit")
            kotlin.test.assertNotNull(reddit)
            assertEquals("reddit.proxy", reddit.host)
            assertEquals(java.net.Proxy.Type.HTTP, reddit.proxyType)

            // Teddit still falls back to global
            val teddit = com.cosmos.stealth.core.network.util.ForwardProxyConfig.resolve("teddit")
            kotlin.test.assertNotNull(teddit)
            assertEquals("global.proxy", teddit.host)
        } finally {
            System.clearProperty("forward.proxy")
            System.clearProperty("reddit.forward.proxy")
        }
    }
}
