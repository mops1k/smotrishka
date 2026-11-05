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
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

/**
 * TDD-спецификация проверки гида перед сохранением.
 *
 * Гида нельзя записывать в базу без проверки: иначе фон будет каждые 6 часов
 * ходить в мёртвый адрес и молча получать 404 или HTML-страницу вместо XMLTV.
 */
class EpgGuideValidatorTest {

    private lateinit var server: MockWebServer
    private lateinit var validator: EpgGuideValidator

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        validator = EpgGuideValidator(HttpBytesClient(OkHttpClient()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val xmltv = """
        <tv>
          <channel id="9052"><display-name>Первый</display-name></channel>
          <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="9052">
            <title>Новости</title>
          </programme>
        </tv>
    """.trimIndent()

    private fun gzip(text: String): Buffer =
        Buffer().write(
            ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }.toByteArray(),
        )

    @Test
    fun `gzip xmltv доступен`() = runTest {
        server.enqueue(MockResponse().setBody(gzip(xmltv)))

        val check = validator.check(server.url("/epg.xml.gz").toString())

        assertThat(check).isEqualTo(EpgGuideCheck.Available)
    }

    @Test
    fun `xml без сжатия доступен`() = runTest {
        server.enqueue(MockResponse().setBody(xmltv))

        val check = validator.check(server.url("/epg.xml").toString())

        assertThat(check).isEqualTo(EpgGuideCheck.Available)
    }

    @Test
    fun `404 объясняет код ответа`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val check = validator.check(server.url("/epg.xml.gz").toString())

        assertThat(check).isInstanceOf(EpgGuideCheck.Unavailable::class.java)
        assertThat((check as EpgGuideCheck.Unavailable).reason).contains("404")
    }

    @Test
    fun `html вместо xmltv не принимается`() = runTest {
        server.enqueue(MockResponse().setBody("<!DOCTYPE html><html><body>Упс</body></html>"))

        val check = validator.check(server.url("/epg.xml").toString())

        assertThat(check).isInstanceOf(EpgGuideCheck.Unavailable::class.java)
        assertThat((check as EpgGuideCheck.Unavailable).reason).contains("XMLTV")
    }

    @Test
    fun `пустой ответ не принимается`() = runTest {
        server.enqueue(MockResponse().setBody(""))

        val check = validator.check(server.url("/epg.xml").toString())

        assertThat(check).isInstanceOf(EpgGuideCheck.Unavailable::class.java)
    }

    @Test
    fun `медленный ответ не принимается`() = runTest {
        val slowClient = HttpBytesClient(
            OkHttpClient.Builder()
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .build(),
        )
        validator = EpgGuideValidator(slowClient)
        server.enqueue(MockResponse().setBody(xmltv).setBodyDelay(3, TimeUnit.SECONDS))

        val check = validator.check(server.url("/epg.xml").toString())

        assertThat(check).isInstanceOf(EpgGuideCheck.Unavailable::class.java)
    }
}
