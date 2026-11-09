package com.mops1k.smotrishka.tv.core.data.update

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.HttpTextClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * TDD-спецификация разбора релизов GitHub: черновики и нечисловые теги отбрасываются,
 * пререлизы появляются только по флагу, а любой сбой возвращается причиной.
 */
class GithubReleasesClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GithubReleasesClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = GithubReleasesClient(
            http = HttpTextClient(OkHttpClient()),
            repository = "mops1k/smotrishka",
            baseUrl = server.url("").toString().removeSuffix("/"),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `стабильные релизы разбираются и идут от свежего`() = runTest {
        server.enqueue(MockResponse().setBody(JSON_RELEASES))

        val releases = (client.fetch(includePrereleases = false) as ReleasesResult.Success).releases

        assertThat(releases.map { it.version.toString() }).containsExactly("0.2.0", "0.1.0").inOrder()
        assertThat(releases.first().tag).isEqualTo("v0.2.0")
        assertThat(releases.first().assets.first().sizeBytes).isEqualTo(9_500_000L)
    }

    @Test
    fun `пререлиз не попадает в список без флага`() = runTest {
        server.enqueue(MockResponse().setBody(JSON_RELEASES))

        val releases = (client.fetch(includePrereleases = false) as ReleasesResult.Success).releases

        assertThat(releases.map { it.version.toString() }).doesNotContain("0.3.0-rc.1")
    }

    @Test
    fun `с флагом пререлиз становится первым`() = runTest {
        server.enqueue(MockResponse().setBody(JSON_RELEASES))

        val releases = (client.fetch(includePrereleases = true) as ReleasesResult.Success).releases

        assertThat(releases.first().version.toString()).isEqualTo("0.3.0-rc.1")
    }

    @Test
    fun `черновик и тег без версии не попадают в список`() = runTest {
        server.enqueue(MockResponse().setBody(JSON_RELEASES))

        val releases = (client.fetch(includePrereleases = true) as ReleasesResult.Success).releases

        assertThat(releases.map { it.tag }).doesNotContain("nightly")
        assertThat(releases.map { it.tag }).doesNotContain("v9.9.9")
    }

    @Test
    fun `запрос идёт в releases с заголовком GitHub`() = runTest {
        server.enqueue(MockResponse().setBody(JSON_RELEASES))

        client.fetch(includePrereleases = false)

        val request = server.takeRequest()
        assertThat(request.path).contains("/repos/mops1k/smotrishka/releases")
        assertThat(request.getHeader("Accept")).contains("github")
    }

    @Test
    fun `http ошибка возвращается причиной`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("rate limit"))

        val result = client.fetch(includePrereleases = false)

        assertThat((result as ReleasesResult.Failure).reason).contains("403")
    }

    @Test
    fun `пустое тело возвращается причиной`() = runTest {
        server.enqueue(MockResponse().setBody(""))

        val result = client.fetch(includePrereleases = false)

        assertThat(result).isInstanceOf(ReleasesResult.Failure::class.java)
    }

    @Test
    fun `неразборчивый ответ возвращается причиной, а не падением`() = runTest {
        server.enqueue(MockResponse().setBody("это не json"))

        val result = client.fetch(includePrereleases = false)

        assertThat((result as ReleasesResult.Failure).reason).contains("разобрать")
    }

    @Test
    fun `недоступный сервер возвращается причиной`() = runTest {
        val url = server.url("").toString().removeSuffix("/")
        server.shutdown()
        val offline = GithubReleasesClient(
            http = HttpTextClient(OkHttpClient()),
            repository = "mops1k/smotrishka",
            baseUrl = url,
        )

        val result = offline.fetch(includePrereleases = false)

        assertThat(result).isInstanceOf(ReleasesResult.Failure::class.java)
    }

    private companion object {
        val JSON_RELEASES = """
            [
              {
                "tag_name": "v0.1.0",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "smotrishka_0.1.0.release.apk",
                    "browser_download_url": "https://example.com/0.1.0.release.apk",
                    "size": 9000000
                  }
                ]
              },
              {
                "tag_name": "v0.2.0",
                "draft": false,
                "prerelease": false,
                "assets": [
                  {
                    "name": "smotrishka_0.2.0.release.apk",
                    "browser_download_url": "https://example.com/0.2.0.release.apk",
                    "size": 9500000
                  }
                ]
              },
              {
                "tag_name": "v0.3.0-rc.1",
                "draft": false,
                "prerelease": true,
                "assets": [
                  {
                    "name": "smotrishka_0.3.0-rc.1.release.apk",
                    "browser_download_url": "https://example.com/0.3.0-rc.1.release.apk",
                    "size": 9600000
                  }
                ]
              },
              {
                "tag_name": "v9.9.9",
                "draft": true,
                "prerelease": false,
                "assets": []
              },
              {
                "tag_name": "nightly",
                "draft": false,
                "prerelease": false,
                "assets": []
              }
            ]
        """.trimIndent()
    }
}
