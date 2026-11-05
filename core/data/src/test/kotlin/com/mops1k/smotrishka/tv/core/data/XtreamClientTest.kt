package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * TDD-спецификация Xtream Codes-клиента: вход, категории, каналы, EPG и M3U-выдача.
 *
 * Ответы сервера взяты по документированным полям Xtream API; фикстуры повторяют
 * реальную форму ответов (`user_info`, `server_info`, массивы категорий и потоков).
 */
class XtreamClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: XtreamClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = XtreamClient(
            client = HttpTextClient(OkHttpClient()),
            serverUrl = server.url("/").toString().trimEnd('/'),
            username = "user",
            password = "pass",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun authJson(auth: Int, status: String) = """
        {
          "user_info": {
            "username": "user",
            "auth": $auth,
            "status": "$status",
            "exp_date": "1767225600",
            "is_trial": "0"
          },
          "server_info": { "url": "example.com", "port": "8080" }
        }
    """.trimIndent()

    @Test
    fun `успешный вход отдаёт аккаунт`() = runTest {
        server.enqueue(MockResponse().setBody(authJson(auth = 1, status = "Active")))

        val result = client.authenticate() as XtreamAuthResult.Success

        assertThat(result.account.username).isEqualTo("user")
        assertThat(result.account.expiresAt).isEqualTo("1767225600")
        assertThat(result.account.isTrial).isFalse()
    }

    @Test
    fun `auth равный нулю означает неверные данные входа`() = runTest {
        server.enqueue(MockResponse().setBody(authJson(auth = 0, status = "Active")))

        assertThat(client.authenticate()).isEqualTo(XtreamAuthResult.InvalidCredentials)
    }

    @Test
    fun `просроченный аккаунт отличается от неверного входа`() = runTest {
        server.enqueue(MockResponse().setBody(authJson(auth = 1, status = "Expired")))

        assertThat(client.authenticate()).isInstanceOf(XtreamAuthResult.Expired::class.java)
    }

    @Test
    fun `ответ не json помечается как некорректный`() = runTest {
        server.enqueue(MockResponse().setBody("<html>403</html>"))

        assertThat(client.authenticate()).isInstanceOf(XtreamAuthResult.Malformed::class.java)
    }

    @Test
    fun `http ошибка входа возвращается кодом`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        assertThat((client.authenticate() as XtreamAuthResult.HttpError).code).isEqualTo(403)
    }

    @Test
    fun `вход идёт на player_api с логином и паролем`() = runTest {
        server.enqueue(MockResponse().setBody(authJson(auth = 1, status = "Active")))

        client.authenticate()

        val request = server.takeRequest()
        assertThat(request.path).contains("player_api.php")
        assertThat(request.path).contains("username=user")
        assertThat(request.path).contains("password=pass")
    }

    @Test
    fun `категории разбираются`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  { "category_id": "1", "category_name": "Основные", "parent_id": 0 },
                  { "category_id": "2", "category_name": "Кино", "parent_id": 0 }
                ]
                """.trimIndent(),
            ),
        )

        val result = client.categories() as XtreamResult.Success

        assertThat(result.value.map { it.name }).containsExactly("Основные", "Кино").inOrder()
        assertThat(server.takeRequest().path).contains("action=get_live_categories")
    }

    @Test
    fun `каналы разбираются с архивом и ссылкой на поток`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [
                  {
                    "num": 1,
                    "name": "Первый",
                    "stream_type": "live",
                    "stream_id": 10,
                    "stream_icon": "http://logo/1.png",
                    "epg_channel_id": "one",
                    "category_id": "1",
                    "tv_archive": 1,
                    "tv_archive_duration": 5
                  }
                ]
                """.trimIndent(),
            ),
        )

        val result = client.liveStreams() as XtreamResult.Success
        val channel = result.value.single()

        assertThat(channel.name).isEqualTo("Первый")
        assertThat(channel.epgId).isEqualTo("one")
        assertThat(channel.logoUrl).isEqualTo("http://logo/1.png")
        assertThat(channel.categoryId).isEqualTo("1")
        assertThat(channel.archive?.mode).isEqualTo(CatchupMode.DEFAULT)
        assertThat(channel.archive?.days).isEqualTo(5)
        assertThat(channel.streamUrl).endsWith("/live/user/pass/10.m3u8")
    }

    @Test
    fun `канал без epg получает id из stream_id`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                [{ "name": "Без EPG", "stream_id": 11, "category_id": "1", "tv_archive": 0 }]
                """.trimIndent(),
            ),
        )

        val channel = (client.liveStreams() as XtreamResult.Success).value.single()

        assertThat(channel.id.value).isEqualTo("xtream:11")
        assertThat(channel.archive).isNull()
    }

    @Test
    fun `epg и m3u адреса строятся по API провайдера`() {
        assertThat(client.epgUrl()).endsWith("/xmltv.php?username=user&password=pass")
        assertThat(client.m3uUrl()).contains("get.php")
        assertThat(client.m3uUrl()).contains("type=m3u_plus")
        assertThat(client.m3uUrl()).contains("output=m3u8")
    }

    @Test
    fun `m3u выдача провайдера разбирается в плейлист`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U
                #EXTINF:-1 tvg-id="one" group-title="Основные",Первый
                http://example.com/live/user/pass/10.m3u8
                """.trimIndent(),
            ),
        )

        val result = client.m3uPlaylist() as M3uParseResult.Success

        assertThat(result.playlist.channels.single().name).isEqualTo("Первый")
        assertThat(server.takeRequest().path).contains("get.php")
    }
}
