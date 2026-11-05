package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream

/**
 * Спецификация форматов XMLTV, которые встречаются у провайдеров.
 *
 * Тесты работают на синтетических файлах: ни сеть, ни реальные выгрузки провайдеров
 * не используются, иначе тест начнёт падать при смене их данных.
 */
class XmltvParserFormatsTest {

    private val parser = XmltvParser()

    @Test
    fun `внешний DTD не скачивается и не ломает разбор`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE tv SYSTEM "xmltv.dtd">
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
            </tv>
        """.trimIndent()

        val epg = (parser.parse(xml) as XmltvParseResult.Success).epg

        assertThat(epg.channels.single().id).isEqualTo("one")
    }

    @Test
    fun `передача до объявления канала всё равно сопоставляется`() {
        val xml = """
            <tv>
              <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
                <title>Фильм</title>
              </programme>
              <channel id="one">
                <display-name>Первый</display-name>
                <icon src="http://example.com/one.png"/>
              </channel>
            </tv>
        """.trimIndent()

        val epg = (parser.parse(xml) as XmltvParseResult.Success).epg

        assertThat(epg.channels.single().id).isEqualTo("one")
        assertThat(epg.programmes.single().channelId).isEqualTo("one")
    }

    @Test
    fun `экранированные амперсанды в адресах раскрываются`() {
        val xml = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
                <title>Фильм</title>
                <icon src="http://example.com/poster.png?w=200&amp;h=300"/>
              </programme>
            </tv>
        """.trimIndent()

        val epg = (parser.parse(xml) as XmltvParseResult.Success).epg
        val icon = epg.programmes.single().iconUrl

        assertThat(icon).isEqualTo("http://example.com/poster.png?w=200&h=300")
    }

    @Test
    fun `описание в CDATA читается целиком`() {
        val xml = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
                <title>Фильм</title>
                <desc><![CDATA[Продолжение <b>саги</b> о героях & союзниках]]></desc>
              </programme>
            </tv>
        """.trimIndent()

        val epg = (parser.parse(xml) as XmltvParseResult.Success).epg

        assertThat(epg.programmes.single().description).contains("о героях & союзниках")
    }

    @Test
    fun `передачи вне окна отбрасываются при разборе потока`() {
        val xml = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102100000 +0000" stop="20261102110000 +0000" channel="one">
                <title>Прошлая</title>
              </programme>
              <programme start="20261102200000 +0000" stop="20261102210000 +0000" channel="one">
                <title>Текущая</title>
              </programme>
              <programme start="20261103050000 +0000" stop="20261103060000 +0000" channel="one">
                <title>Далёкая</title>
              </programme>
            </tv>
        """.trimIndent()
        val from = Instant.parse("2026-11-02T19:00:00Z")
        val to = Instant.parse("2026-11-02T23:00:00Z")

        val epg = (
            parser.parseStream(
                input = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
                from = from,
                to = to,
            ) as XmltvParseResult.Success
            ).epg

        assertThat(epg.programmes.map { it.title }).containsExactly("Текущая")
    }

    @Test
    fun `передача на границе окна сохраняется`() {
        val xml = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102183000 +0000" stop="20261102193000 +0000" channel="one">
                <title>Началась до окна</title>
              </programme>
              <programme start="20261102213000 +0000" stop="20261102223000 +0000" channel="one">
                <title>Закончится после окна</title>
              </programme>
            </tv>
        """.trimIndent()

        val epg = (
            parser.parseStream(
                input = ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
                from = Instant.parse("2026-11-02T19:00:00Z"),
                to = Instant.parse("2026-11-02T22:00:00Z"),
            ) as XmltvParseResult.Success
            ).epg

        assertThat(epg.programmes).hasSize(2)
    }

    @Test
    fun `gzip определяется по магическим байтам`() {
        val xml = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
            </tv>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        val packed = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(xml) }
        }.toByteArray()

        val epg = (parser.parseBytes(packed) as XmltvParseResult.Success).epg

        assertThat(epg.channels.single().id).isEqualTo("one")
    }

    @Test
    fun `незнакомые теги не ломают разбор`() {
        val xml = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
                <title>Фильм</title>
                <sub-title>Серия 3</sub-title>
                <episode-num system="onscreen">S01E03</episode-num>
                <category>Кино</category>
                <date>20200101</date>
                <live/>
              </programme>
            </tv>
        """.trimIndent()

        val programme = (parser.parse(xml) as XmltvParseResult.Success).epg.programmes.single()

        assertThat(programme.title).isEqualTo("Фильм")
        assertThat(programme.subTitle).isEqualTo("Серия 3")
    }

    @Test
    fun `передача без времени или канала пропускается`() {
        val xml = """
            <tv>
              <channel id="one"><display-name>Первый</display-name></channel>
              <programme start="20261102120000 +0000" channel="one"><title>Без конца</title></programme>
              <programme start="20261102120000 +0000" stop="20261102130000 +0000"><title>Без канала</title></programme>
              <programme start="20261102120000 +0000" stop="20261102130000 +0000" channel="one">
                <title>Полная</title>
              </programme>
            </tv>
        """.trimIndent()

        val programmes = (parser.parse(xml) as XmltvParseResult.Success).epg.programmes

        assertThat(programmes.map { it.title }).containsExactly("Полная")
    }
}
