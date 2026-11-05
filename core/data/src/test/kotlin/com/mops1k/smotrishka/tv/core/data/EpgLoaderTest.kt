package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.playlist.XmltvParseResult
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream

/**
 * TDD-спецификация загрузчика программы передач: сеть отдаёт gzip, парсер получает байты.
 */
class EpgLoaderTest {

    private lateinit var server: MockWebServer
    private lateinit var loader: EpgLoader

    private val xmltv = """
        <tv>
          <channel id="one"><display-name>Первый</display-name></channel>
          <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
            <title>Новости</title>
          </programme>
        </tv>
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        loader = EpgLoader(HttpBytesClient(OkHttpClient()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun gzip(text: String): Buffer =
        Buffer().write(
            ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }.toByteArray(),
        )

    @Test
    fun `загружает и разбирает gzip-программу`() = runTest {
        server.enqueue(MockResponse().setBody(gzip(xmltv)))

        val result = loader.load(server.url("/epg.xml.gz").toString())

        val epg = (result as XmltvParseResult.Success).epg
        assertThat(epg.channels.single().displayName).isEqualTo("Первый")
        assertThat(epg.programmes.single().title).isEqualTo("Новости")
    }

    @Test
    fun `обычный xml тоже разбирается`() = runTest {
        server.enqueue(MockResponse().setBody(xmltv))

        assertThat(loader.load(server.url("/epg.xml").toString())).isInstanceOf(XmltvParseResult.Success::class.java)
    }

    @Test
    fun `окно отсекает передачи вне него`() = runTest {
        val wide = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20200101100000 +0000" stop="20200101110000 +0000" channel="one">
                <title>Старая</title>
              </programme>
              <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
                <title>Нужная</title>
              </programme>
            </tv>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(gzip(wide)))

        val result = loader.load(
            url = server.url("/epg.xml.gz").toString(),
            from = Instant.parse("2026-11-02T00:00:00Z"),
            to = Instant.parse("2026-11-03T00:00:00Z"),
        )

        val titles = (result as XmltvParseResult.Success).epg.programmes.map { it.title }
        assertThat(titles).containsExactly("Нужная")
    }

    @Test
    fun `http ошибка объясняется понятным текстом`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = loader.load(server.url("/epg.xml").toString())

        assertThat((result as XmltvParseResult.Failure).reason).contains("404")
    }

    @Test
    fun `сетевая ошибка объясняется понятным текстом`() = runTest {
        val url = server.url("/epg.xml").toString()
        server.shutdown()

        val result = loader.load(url)

        assertThat((result as XmltvParseResult.Failure).reason).contains("сеть")
    }
}
