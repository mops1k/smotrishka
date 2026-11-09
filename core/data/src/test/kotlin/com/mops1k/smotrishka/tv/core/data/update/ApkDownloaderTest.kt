package com.mops1k.smotrishka.tv.core.data.update

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * TDD-спецификация загрузки APK: файл пишется целиком, прогресс доходит до 100,
 * а обрыв и ошибки сервера возвращаются причиной, не роняя приложение.
 */
class ApkDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: ApkDownloader
    private lateinit var target: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = ApkDownloader(OkHttpClient())
        target = File(tempFolder.root, "update.apk")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `файл скачивается целиком и прогресс доходит до 100`() = runTest {
        val payload = ByteArray(200_000) { index -> (index % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(payload)))
        val progress = mutableListOf<Int>()

        val result = downloader.download(server.url("/apk").toString(), target) { progress += it }

        assertThat((result as ApkDownloadResult.Success).bytes).isEqualTo(200_000L)
        assertThat(target.readBytes()).isEqualTo(payload)
        assertThat(progress.last()).isEqualTo(100)
        assertThat(progress).isNotEmpty()
    }

    @Test
    fun `http ошибка возвращается причиной`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val result = downloader.download(server.url("/apk").toString(), target)

        assertThat((result as ApkDownloadResult.Failure).reason).contains("404")
    }

    @Test
    fun `обрыв посреди тела возвращается причиной, а не падением`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody(Buffer().write(ByteArray(500_000)))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        val result = downloader.download(server.url("/apk").toString(), target)

        assertThat(result).isInstanceOf(ApkDownloadResult.Failure::class.java)
    }

    @Test
    fun `недоступный сервер возвращается причиной`() = runTest {
        val url = server.url("/apk").toString()
        server.shutdown()

        val result = downloader.download(url, target)

        assertThat(result).isInstanceOf(ApkDownloadResult.Failure::class.java)
    }
}
