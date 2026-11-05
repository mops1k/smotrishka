package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
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
 * TDD-спецификация HTTP-слоя: любая сетевая неприятность должна превращаться
 * в значение результата, а не в исключение, и тело должно читаться в верной кодировке.
 */
class HttpTextClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HttpTextClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = HttpTextClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `возвращает текст ответа`() = runTest {
        server.enqueue(MockResponse().setBody("#EXTM3U\n#EXTINF:-1,Первый"))

        val result = client.fetch(server.url("/list.m3u").toString())

        assertThat((result as HttpTextResult.Success).text).contains("Первый")
    }

    @Test
    fun `передаёт заголовки и свой user agent`() = runTest {
        server.enqueue(MockResponse().setBody("ok"))

        client.fetch(
            server.url("/list.m3u").toString(),
            headers = mapOf("Referer" to "http://ref.example.com/"),
        )

        val request = server.takeRequest()
        assertThat(request.getHeader("Referer")).isEqualTo("http://ref.example.com/")
        assertThat(request.getHeader("User-Agent")).isNotEmpty()
    }

    @Test
    fun `cp1251 ответ декодируется в кириллицу`() = runTest {
        val text = "#EXTM3U\n#EXTINF:-1,Первый канал"
        val body = Buffer().write(text.toByteArray(Charset.forName("windows-1251")))
        server.enqueue(MockResponse().setBody(body))

        val result = client.fetch(server.url("/list.m3u").toString())

        assertThat((result as HttpTextResult.Success).text).contains("Первый канал")
    }

    @Test
    fun `http ошибка возвращается кодом`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Forbidden"))

        val result = client.fetch(server.url("/list.m3u").toString())

        assertThat((result as HttpTextResult.HttpError).code).isEqualTo(403)
    }

    @Test
    fun `пустое тело отличается от ошибки`() = runTest {
        server.enqueue(MockResponse().setBody(""))

        val result = client.fetch(server.url("/list.m3u").toString())

        assertThat(result).isEqualTo(HttpTextResult.EmptyBody)
    }

    @Test
    fun `недоступный сервер даёт сетевую ошибку`() = runTest {
        val url = server.url("/list.m3u").toString()
        server.shutdown()

        val result = client.fetch(url)

        assertThat(result).isInstanceOf(HttpTextResult.NetworkError::class.java)
    }
}
