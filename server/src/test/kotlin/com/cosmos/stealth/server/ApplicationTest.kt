package com.cosmos.stealth.server

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
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
}
