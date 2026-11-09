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
 * TDD-спецификация проверки обновлений: релизная сборка берёт `.release.apk`,
 * отладочная — `.debug.apk`, а неудачи становятся причиной, а не исключением.
 */
class UpdateCheckerTest {

    private lateinit var server: MockWebServer
    private lateinit var checker: UpdateChecker

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        checker = UpdateChecker(
            GithubReleasesClient(
                http = HttpTextClient(OkHttpClient()),
                repository = "mops1k/smotrishka",
                baseUrl = server.url("").toString().removeSuffix("/"),
            ),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `новая стабильная версия предлагается релизной сборке`() = runTest {
        server.enqueue(MockResponse().setBody(releases()))

        val result = checker.check(
            current = SemverVersion(0, 1, 0),
            includePrereleases = false,
            debugBuild = false,
        )

        val available = result as UpdateCheckResult.Available
        assertThat(available.release.version).isEqualTo(SemverVersion(0, 2, 0))
        assertThat(available.apk.name).endsWith(".release.apk")
    }

    @Test
    fun `отладочной сборке предлагается debug apk`() = runTest {
        server.enqueue(MockResponse().setBody(releases()))

        val result = checker.check(
            current = SemverVersion(0, 1, 0),
            includePrereleases = false,
            debugBuild = true,
        )

        assertThat((result as UpdateCheckResult.Available).apk.name).endsWith(".debug.apk")
    }

    @Test
    fun `та же версия считается актуальной`() = runTest {
        server.enqueue(MockResponse().setBody(releases()))

        val result = checker.check(
            current = SemverVersion(0, 2, 0),
            includePrereleases = false,
            debugBuild = false,
        )

        assertThat(result).isInstanceOf(UpdateCheckResult.UpToDate::class.java)
    }

    @Test
    fun `релиз без ассета нужного типа даёт отказ`() = runTest {
        server.enqueue(MockResponse().setBody(releases(debugAsset = false)))

        val result = checker.check(
            current = SemverVersion(0, 1, 0),
            includePrereleases = false,
            debugBuild = true,
        )

        assertThat((result as UpdateCheckResult.Failure).reason).contains("APK")
    }

    @Test
    fun `пререлиз не предлагается при выключенном флаге`() = runTest {
        server.enqueue(MockResponse().setBody(releases(prerelease = "v0.3.0-rc.1")))

        val result = checker.check(
            current = SemverVersion(0, 2, 0),
            includePrereleases = false,
            debugBuild = false,
        )

        assertThat(result).isInstanceOf(UpdateCheckResult.UpToDate::class.java)
    }

    @Test
    fun `пререлиз предлагается при включённом флаге`() = runTest {
        server.enqueue(MockResponse().setBody(releases(prerelease = "v0.3.0-rc.1")))

        val result = checker.check(
            current = SemverVersion(0, 2, 0),
            includePrereleases = true,
            debugBuild = false,
        )

        assertThat((result as UpdateCheckResult.Available).release.version.toString())
            .isEqualTo("0.3.0-rc.1")
    }

    @Test
    fun `сетевая ошибка возвращается отказом`() = runTest {
        val url = server.url("").toString().removeSuffix("/")
        server.shutdown()
        val offline = UpdateChecker(
            GithubReleasesClient(
                http = HttpTextClient(OkHttpClient()),
                repository = "mops1k/smotrishka",
                baseUrl = url,
            ),
        )

        val result = offline.check(
            current = SemverVersion(0, 1, 0),
            includePrereleases = false,
            debugBuild = false,
        )

        assertThat(result).isInstanceOf(UpdateCheckResult.Failure::class.java)
    }

    /**
     * Ответ API с одним-двумя релизами.
     *
     * @param debugAsset приложен ли отладочный APK;
     * @param prerelease тег пререлиза, если он нужен в ответе.
     */
    private fun releases(debugAsset: Boolean = true, prerelease: String? = null): String {
        val stableAssets = buildString {
            append("""{"name":"smotrishka_0.2.0.release.apk","browser_download_url":"https://example.com/rel.apk","size":9500000}""")
            if (debugAsset) {
                append(""",{"name":"smotrishka_0.2.0.debug.apk","browser_download_url":"https://example.com/dbg.apk","size":32000000}""")
            }
        }
        val extra = prerelease?.let {
            """{"tag_name":"$it","draft":false,"prerelease":true,"assets":[{"name":"smotrishka_$it.release.apk","browser_download_url":"https://example.com/rc.apk","size":9600000}]}"""
        }
        val releases = listOfNotNull(
            """{"tag_name":"v0.2.0","draft":false,"prerelease":false,"assets":[$stableAssets]}""",
            extra,
        )
        return releases.joinToString(prefix = "[", postfix = "]", separator = ",")
    }
}
