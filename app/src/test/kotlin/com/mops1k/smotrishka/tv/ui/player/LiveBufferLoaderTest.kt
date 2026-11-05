package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * TDD-спецификация устойчивости загрузчика эфира к сбоям сети.
 *
 * Реальное падение 04.11.2026 (crash-буфер, PID 25117): `SocketTimeoutException` из опроса
 * плейлиста вылетел из [LiveBufferLoader.load] в корутину Compose и закрыл приложение.
 * Сеть у провайдера рвётся и висит регулярно, поэтому загрузчик обязан переживать сбой,
 * а не ронять экран: после ошибки он продолжает опрос и снова принимает сегменты.
 *
 * Тест идёт под Robolectric: загрузчик пишет в logcat, а в обычном JVM-тесте `android.util.Log`
 * не заглушён (как и в остальных тестах плеера).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveBufferLoaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var server: MockWebServer

    /** Короткие таймауты: тест воспроизводит реальный обрыв сети за доли секунды. */
    private val client = OkHttpClient.Builder()
        .connectTimeout(SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SHORT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        cacheDir = folder.newFolder("segments")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `таймаут плейлиста не роняет загрузчик`() = runBlocking {
        // Ровно то, что случилось 04.11: провайдер не ответил за readTimeout.
        server.dispatcher = noAnswer()
        var polls = 0

        loader().load(
            streamUrl = server.url("/live.m3u8").toString(),
            onSegment = { _, _ -> },
            isActive = { polls++ < 3 },
        )

        assertThat(polls).isAtLeast(3)
    }

    @Test
    fun `ответ 500 не роняет загрузчик`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(500)
        }
        var polls = 0

        loader().load(
            streamUrl = server.url("/live.m3u8").toString(),
            onSegment = { _, _ -> },
            isActive = { polls++ < 3 },
        )

        assertThat(polls).isAtLeast(3)
    }

    @Test
    fun `после сбоя сети сегмент снова принимается`() = runBlocking {
        val timestamp = FIRST_TIMESTAMP
        var playlistRequests = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path.orEmpty().endsWith(".m3u8")) {
                    return MockResponse().setBody(Buffer().write(ByteArray(VALID_SEGMENT_BYTES)))
                }
                playlistRequests++
                return if (playlistRequests == 1) {
                    MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                } else {
                    MockResponse().setBody(playlistOf(timestamp))
                }
            }
        }
        val received = mutableListOf<File>()

        loader().load(
            streamUrl = server.url("/live.m3u8").toString(),
            onSegment = { file, _ -> received += file },
            isActive = { received.isEmpty() },
        )

        assertThat(received).hasSize(1)
        assertThat(received.single().exists()).isTrue()
    }

    @Test
    fun `короткий сегмент помечается разрывом и загрузка продолжается`() = runBlocking {
        val timestamp = FIRST_TIMESTAMP
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().endsWith(".m3u8")) {
                    MockResponse().setBody(playlistOf(timestamp))
                } else {
                    // Обрезанный кусок: провайдер отдал меньше MIN_SEGMENT_BYTES.
                    MockResponse().setBody(Buffer().write(ByteArray(BROKEN_SEGMENT_BYTES)))
                }
        }
        var gaps = 0
        var polls = 0

        loader(waitLimitMs = 400).load(
            streamUrl = server.url("/live.m3u8").toString(),
            onSegment = { _, _ -> },
            onGap = { gaps++ },
            isActive = { polls++ < 4 },
        )

        assertThat(gaps).isAtLeast(1)
    }

    @Test
    fun `плашка о связи появляется после двух неудач и уходит при восстановлении`() = runBlocking {
        var playlistRequests = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path.orEmpty().endsWith(".m3u8")) {
                    return MockResponse().setBody(Buffer().write(ByteArray(VALID_SEGMENT_BYTES)))
                }
                playlistRequests++
                return if (playlistRequests <= 2) {
                    MockResponse().setResponseCode(500)
                } else {
                    MockResponse().setBody(playlistOf(FIRST_TIMESTAMP))
                }
            }
        }
        val states = mutableListOf<Boolean>()
        var polls = 0

        loader().load(
            streamUrl = server.url("/live.m3u8").toString(),
            onSegment = { _, _ -> },
            isActive = { polls++ < 6 },
            onConnectivity = { states += it },
        )

        // Одна плашка на время обрыва и одно снятие, без мигания на каждой попытке.
        assertThat(states).containsExactly(false, true).inOrder()
    }

    @Test
    fun `одиночная неудача плашку не показывает`() = runBlocking {
        var playlistRequests = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (!request.path.orEmpty().endsWith(".m3u8")) {
                    return MockResponse().setBody(Buffer().write(ByteArray(VALID_SEGMENT_BYTES)))
                }
                playlistRequests++
                return if (playlistRequests == 1) {
                    MockResponse().setResponseCode(500)
                } else {
                    MockResponse().setBody(playlistOf(FIRST_TIMESTAMP))
                }
            }
        }
        val states = mutableListOf<Boolean>()
        var polls = 0

        loader().load(
            streamUrl = server.url("/live.m3u8").toString(),
            onSegment = { _, _ -> },
            isActive = { polls++ < 5 },
            onConnectivity = { states += it },
        )

        assertThat(states).isEmpty()
    }

    private fun loader(waitLimitMs: Long = 2_000) =
        LiveBufferLoader(client, cacheDir, waitLimitMs = waitLimitMs, pollDelayMs = 10)

    private fun noAnswer() = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse =
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
    }

    private fun playlistOf(timestamp: Long): String =
        "#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10.0,\n${server.url("/media/$timestamp.ts")}\n"

    private companion object {
        const val SHORT_TIMEOUT_MS = 300L
        const val VALID_SEGMENT_BYTES = 300_001
        const val BROKEN_SEGMENT_BYTES = 1_000
        const val FIRST_TIMESTAMP = 1_793_730_530_000L
    }
}
