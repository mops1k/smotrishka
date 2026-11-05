package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream

/**
 * TDD-спецификация XMLTV-парсера.
 *
 * EPG приходит большими файлами (часто gzip) и с разными форматами времени,
 * поэтому разбор потоковый, а некорректные записи пропускаются, не роняя весь файл.
 */
class XmltvParserTest {

    private val parser = XmltvParser()

    private val xml = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv date="20261102120000 +0300">
          <channel id="one">
            <display-name>Первый</display-name>
            <icon src="http://logo/1.png" />
          </channel>
          <programme start="20261102120000 +0300" stop="20261102130000 +0300" channel="one">
            <title>Новости</title>
            <desc>Ежедневный выпуск</desc>
            <icon src="http://img/news.png" />
            <catchup-id>arch-1</catchup-id>
          </programme>
        </tv>
    """.trimIndent()

    private fun parseOk(text: String) = (parser.parse(text) as XmltvParseResult.Success).epg

    @Test
    fun `разбирает канал с именем и иконкой`() {
        val epg = parseOk(xml)

        val channel = epg.channels.single()
        assertThat(channel.id).isEqualTo("one")
        assertThat(channel.displayName).isEqualTo("Первый")
        assertThat(channel.iconUrl).isEqualTo("http://logo/1.png")
    }

    @Test
    fun `разбирает передачу с описанием и архивным идентификатором`() {
        val epg = parseOk(xml)

        val programme = epg.programmes.single()
        assertThat(programme.channelId).isEqualTo("one")
        assertThat(programme.title).isEqualTo("Новости")
        assertThat(programme.description).isEqualTo("Ежедневный выпуск")
        assertThat(programme.catchupId).isEqualTo("arch-1")
        assertThat(programme.iconUrl).isEqualTo("http://img/news.png")
    }

    @Test
    fun `время со смещением переводится в absolute instant`() {
        val epg = parseOk(xml)

        val programme = epg.programmes.single()
        assertThat(programme.start).isEqualTo(Instant.parse("2026-11-02T09:00:00Z"))
        assertThat(programme.end).isEqualTo(Instant.parse("2026-11-02T10:00:00Z"))
    }

    @Test
    fun `время без смещения считается UTC`() {
        val epg = parseOk(
            """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102120000" stop="20261102130000" channel="one">
                <title>Новости</title>
              </programme>
            </tv>
            """.trimIndent(),
        )

        assertThat(epg.programmes.single().start).isEqualTo(Instant.parse("2026-11-02T12:00:00Z"))
    }

    @Test
    fun `время без секунд разбирается`() {
        val epg = parseOk(
            """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="202611021200" stop="202611021300" channel="one">
                <title>Новости</title>
              </programme>
            </tv>
            """.trimIndent(),
        )

        assertThat(epg.programmes.single().start).isEqualTo(Instant.parse("2026-11-02T12:00:00Z"))
    }

    @Test
    fun `передача без конца пропускается`() {
        val epg = parseOk(
            """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102120000" channel="one"><title>Без конца</title></programme>
              <programme start="20261102120000" stop="20261102130000" channel="one"><title>С концом</title></programme>
            </tv>
            """.trimIndent(),
        )

        assertThat(epg.programmes.map { it.title }).containsExactly("С концом")
    }

    @Test
    fun `передача неизвестного канала отбрасывается`() {
        val epg = parseOk(
            """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102120000" stop="20261102130000" channel="ghost">
                <title>Чужая</title>
              </programme>
            </tv>
            """.trimIndent(),
        )

        assertThat(epg.programmes).isEmpty()
    }

    @Test
    fun `битый xml даёт ошибку`() {
        val result = parser.parse("<tv><channel id=\"one\"></tv>")

        assertThat(result).isInstanceOf(XmltvParseResult.Failure::class.java)
    }

    @Test
    fun `gzip-файл разбирается`() {
        val bytes = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(xml.toByteArray(Charsets.UTF_8)) }
        }.toByteArray()

        val epg = (parser.parseBytes(bytes) as XmltvParseResult.Success).epg

        assertThat(epg.programmes.single().title).isEqualTo("Новости")
    }

    @Test
    fun `BOM в начале не мешает`() {
        val epg = parseOk("\uFEFF" + xml)

        assertThat(epg.channels.single().id).isEqualTo("one")
    }

    @Test
    fun `при нескольких display-name берётся первое непустое`() {
        val epg = parseOk(
            """
            <tv>
              <channel id="one">
                <display-name lang="en"></display-name>
                <display-name lang="ru">Первый канал</display-name>
                <display-name>Первый</display-name>
              </channel>
            </tv>
            """.trimIndent(),
        )

        assertThat(epg.channels.single().displayName).isEqualTo("Первый канал")
    }

    @Test
    fun `подзаголовок передачи читается`() {
        val epg = parseOk(
            """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
                <title>Сериал</title>
                <sub-title>Серия 5</sub-title>
              </programme>
            </tv>
            """.trimIndent(),
        )

        assertThat(epg.programmes.single().subTitle).isEqualTo("Серия 5")
    }
}
