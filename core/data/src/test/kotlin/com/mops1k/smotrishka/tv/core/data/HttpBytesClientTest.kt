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

/**
 * TDD-спецификация бинарного HTTP-слоя: EPG приходит gzip-ом, поэтому нужен путь
 * «байты как есть» без текстового декодирования.
 */
class HttpBytesClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: HttpBytesClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = HttpBytesClient(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `возвращает байты ответа`() = runTest {
        server.enqueue(MockResponse().setBody("<tv></tv>"))

        val result = client.fetch(server.url("/epg.xml").toString())

        assertThat((result as HttpBytesResult.Success).bytes.toString(Charsets.UTF_8)).isEqualTo("<tv></tv>")
    }

    @Test
    fun `gzip-тело передаётся без изменений`() = runTest {
        val gzipMagic = byteArrayOf(0x1F, 0x8B.toByte(), 0x08, 0x00)
        server.enqueue(MockResponse().setBody(Buffer().write(gzipMagic)))

        val result = client.fetch(server.url("/epg.xml.gz").toString())

        assertThat((result as HttpBytesResult.Success).bytes.copyOfRange(0, 2)).isEqualTo(gzipMagic.copyOfRange(0, 2))
    }

    @Test
    fun `http ошибка возвращается кодом`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        assertThat((client.fetch(server.url("/epg.xml").toString()) as HttpBytesResult.HttpError).code).isEqualTo(404)
    }

    @Test
    fun `пустое тело отличается от ошибки`() = runTest {
        server.enqueue(MockResponse().setBody(""))

        assertThat(client.fetch(server.url("/epg.xml").toString())).isEqualTo(HttpBytesResult.EmptyBody)
    }

    @Test
    fun `недоступный сервер даёт сетевую ошибку`() = runTest {
        val url = server.url("/epg.xml").toString()
        server.shutdown()

        assertThat(client.fetch(url)).isInstanceOf(HttpBytesResult.NetworkError::class.java)
    }
}
