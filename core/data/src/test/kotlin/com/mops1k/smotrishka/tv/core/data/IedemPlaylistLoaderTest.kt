package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.playlist.M3uParser
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.nio.charset.Charset

/**
 * TDD-спецификация загрузки плейлиста по коду провайдера.
 *
 * Провайдер отдаёт общий канал-лист со ссылками-заглушками: загрузчик подставляет
 * в них настоящий хост и код пользователя, иначе каналы не заиграют.
 */
class IedemPlaylistLoaderTest {

    private lateinit var server: MockWebServer
    private lateinit var loader: IedemPlaylistLoader
    private lateinit var base: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        base = server.url("/").toString().trimEnd('/')
        val provider = IedemProvider(channelListBase = base, streamBase = base)
        loader = IedemPlaylistLoader(HttpTextClient(OkHttpClient()), provider, M3uParser())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `подставляет код и хост в ссылки каналов`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U x-tvg-url="http://example.com/epg.xml"
                #EXTINF:0 group-title="Общие" tvg-id="146" tvg-logo="http://example.com/1.png",Первый
                http://localhost/iptv/00000000000000/2402/index.m3u8
                """.trimIndent(),
            ),
        )

        val result = loader.load("LHS2AYB8R2MLPR") as M3uParseResult.Success
        val channel = result.playlist.channels.single()

        assertThat(server.takeRequest().path).isEqualTo("/edem/edem_epg_ico.m3u8")
        assertThat(channel.streamUrl).isEqualTo("$base/iptv/LHS2AYB8R2MLPR/2402/index.m3u8")
        assertThat(result.playlist.epgUrl).isEqualTo("http://example.com/epg.xml")
    }

    @Test
    fun `каталог из нескольких каналов переписывается целиком`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U x-tvg-url="http://example.com/epg.xml.gz"
                #EXTINF:0 group-title="новости" tvg-id="146" tvg-logo="http://example.com/146.png" tvg-rec="0",Первый
                http://localhost/iptv/00000000000000/19241/index.m3u8
                #EXTINF:0 group-title="кино" tvg-id="147",Второй
                http://localhost/iptv/00000000000000/19242/index.m3u8
                #EXTINF:0 group-title="кино" tvg-id="148",Третий
                http://localhost/iptv/00000000000000/ID-WITH-DASH/index.m3u8
                """.trimIndent(),
            ),
        )

        val channels = (loader.load("key12345") as M3uParseResult.Success).playlist.channels

        assertThat(channels.map { it.streamUrl }).containsExactly(
            "$base/iptv/key12345/19241/index.m3u8",
            "$base/iptv/key12345/19242/index.m3u8",
            "$base/iptv/key12345/ID-WITH-DASH/index.m3u8",
        ).inOrder()
    }

    @Test
    fun `канал без распознаваемого id сохраняет ссылку из списка`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U
                #EXTINF:-1,Канал без id
                http://example.com/direct.m3u8
                """.trimIndent(),
            ),
        )

        val result = loader.load("LHS2AYB8R2MLPR") as M3uParseResult.Success

        assertThat(result.playlist.channels.single().streamUrl).isEqualTo("http://example.com/direct.m3u8")
    }

    @Test
    fun `кириллица в cp1251 читается`() = runTest {
        val body = Buffer().write(
            "#EXTM3U\n#EXTINF:0 group-title=\"Основные\",Первый канал\nhttp://localhost/iptv/x/1/index.m3u8"
                .toByteArray(Charset.forName("windows-1251")),
        )
        server.enqueue(MockResponse().setBody(body))

        val result = loader.load("key12345") as M3uParseResult.Success

        assertThat(result.playlist.categories.single().name).isEqualTo("Основные")
    }

    @Test
    fun `пустой код не ходит в сеть`() = runTest {
        val result = loader.load("   ")

        assertThat(result).isInstanceOf(M3uParseResult.Failure::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `http ошибка провайдера объясняется понятным текстом`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        val result = loader.load("key12345")

        assertThat((result as M3uParseResult.Failure).reason).contains("не подошёл")
    }

    @Test
    fun `сетевая ошибка объясняется понятным текстом`() = runTest {
        server.shutdown()

        val result = loader.load("key12345")

        assertThat((result as M3uParseResult.Failure).reason).contains("сеть")
    }
}
