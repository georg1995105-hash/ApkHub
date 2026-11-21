package com.apkhub.app.data

import com.apkhub.app.data.remote.ApiErrorKind
import com.apkhub.app.data.remote.GitHubApiClient
import com.apkhub.app.data.remote.GitHubResult
import com.apkhub.app.domain.model.ReleaseChannel
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GitHubApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GitHubApiClient(
            httpClient = OkHttpClient(),
            baseUrl = server.url("/").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesReleasesFiltersDraftsAndExtractsEtagAndRateLimit() = runTest {
        val body = """
            [
              {
                "id": 101,
                "tag_name": "v1.4.0",
                "name": "Version 1.4.0",
                "body": "Bug fixes",
                "html_url": "https://github.com/acme/demo/releases/tag/v1.4.0",
                "published_at": "2026-11-10T10:00:00Z",
                "prerelease": false,
                "draft": false,
                "assets": [
                  {
                    "id": 500,
                    "name": "demo-arm64-v8a.apk",
                    "browser_download_url": "https://github.com/acme/demo/releases/download/v1.4.0/demo-arm64-v8a.apk",
                    "size": 12345678
                  }
                ]
              },
              {
                "id": 102,
                "tag_name": "v1.5.0-draft",
                "draft": true,
                "prerelease": true,
                "assets": []
              }
            ]
        """.trimIndent()

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("ETag", "\"abc123\"")
                .setHeader("X-RateLimit-Limit", "60")
                .setHeader("X-RateLimit-Remaining", "58")
                .setHeader("X-RateLimit-Reset", "1800000000")
                .setBody(body)
        )

        val res = client.fetchReleases("acme", "demo")
        assertTrue(res is GitHubResult.Success)
        val success = res as GitHubResult.Success
        assertEquals(1, success.data.size)
        assertEquals("v1.4.0", success.data.first().tagName)
        assertEquals(ReleaseChannel.STABLE, success.data.first().channel)
        assertEquals("\"abc123\"", success.etag)
        assertEquals(58, success.rateLimit?.remaining)
    }

    @Test
    fun handles304NotModifiedWithEtagHeader() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(304)
                .setHeader("ETag", "\"cached-etag\"")
        )

        val res = client.fetchReleases("acme", "demo", etag = "\"cached-etag\"")
        assertTrue(res is GitHubResult.NotModified)
        val recorded = server.takeRequest()
        assertEquals("\"cached-etag\"", recorded.getHeader("If-None-Match"))
    }

    @Test
    fun detectsRateLimitingOn403WithZeroRemaining() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader("X-RateLimit-Limit", "60")
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1800003600")
                .setBody("{\"message\":\"API rate limit exceeded\"}")
        )

        val res = client.fetchReleases("acme", "demo")
        assertTrue(res is GitHubResult.Error)
        val err = res as GitHubResult.Error
        assertEquals(ApiErrorKind.RATE_LIMITED, err.kind)
        assertEquals(true, err.rateLimit?.isRateLimited)
    }
}
