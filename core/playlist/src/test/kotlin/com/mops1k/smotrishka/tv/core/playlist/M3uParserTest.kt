package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Playlist
import org.junit.Test

/**
 * TDD-спецификация парсера M3U: каждый тест фиксирует правило разбора,
 * на которое опирается каталог каналов.
 */
class M3uParserTest {

    private val parser = M3uParser()

    private fun parseOk(text: String): Playlist =
        (parser.parse(text) as M3uParseResult.Success).playlist

    @Test
    fun `разбирает минимальный плейлист`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Первый
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.categories).isEmpty()
        val channel = playlist.channels.single()
        assertThat(channel.name).isEqualTo("Первый")
        assertThat(channel.streamUrl).isEqualTo("http://example.com/1.m3u8")
        assertThat(channel.categoryId).isNull()
        assertThat(channel.logoUrl).isNull()
        assertThat(channel.epgId).isNull()
        assertThat(channel.archive).isNull()
    }

    @Test
    fun `разбирает атрибуты в кавычках`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="one" tvg-name="Первый" tvg-logo="http://logo/1.png" group-title="Основные",Первый канал
            http://example.com/one.m3u8
            """.trimIndent(),
        )

        val channel = playlist.channels.single()
        assertThat(channel.id).isEqualTo(ChannelId("one"))
        assertThat(channel.epgId).isEqualTo("one")
        assertThat(channel.name).isEqualTo("Первый канал")
        assertThat(channel.logoUrl).isEqualTo("http://logo/1.png")
        assertThat(channel.categoryId).isEqualTo("основные")
        assertThat(playlist.categories.single().name).isEqualTo("Основные")
    }

    @Test
    fun `разбирает атрибуты без кавычек`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id=two group-title=Кино,Канал кино
            http://example.com/two.m3u8
            """.trimIndent(),
        )

        val channel = playlist.channels.single()
        assertThat(channel.id).isEqualTo(ChannelId("two"))
        assertThat(channel.categoryId).isEqualTo("кино")
        assertThat(channel.name).isEqualTo("Канал кино")
    }

    @Test
    fun `берёт категорию из EXTGRP если group-title нет`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Канал без группы
            #EXTGRP:Спорт
            http://example.com/2.m3u8
            """.trimIndent(),
        )

        val channel = playlist.channels.single()
        assertThat(channel.categoryId).isEqualTo("спорт")
        assertThat(playlist.categories.map { it.name }).containsExactly("Спорт")
    }

    @Test
    fun `категории идут в порядке появления и без дублей`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 group-title="A",Первый
            http://example.com/1.m3u8
            #EXTINF:-1 group-title="B",Второй
            http://example.com/2.m3u8
            #EXTINF:-1 group-title="A",Третий
            http://example.com/3.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.categories.map { it.name }).containsExactly("A", "B").inOrder()
        assertThat(playlist.channels.map { it.name }).containsExactly("Первый", "Второй", "Третий").inOrder()
    }

    @Test
    fun `id канала без tvg-id строится из url`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Первый
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().id).isEqualTo(ChannelId("url:http://example.com/1.m3u8"))
    }

    @Test
    fun `разбирает теги архива`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 catchup="default" catchup-source="http://example.com/arch/{utc}" catchup-days="7" group-title="A",Архивный
            http://example.com/arch.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().archive).isEqualTo(
            ArchiveInfo(
                mode = CatchupMode.DEFAULT,
                source = "http://example.com/arch/{utc}",
                days = 7,
            ),
        )
    }

    @Test
    fun `неизвестный режим архива помечается как UNKNOWN`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 catchup="weird",Канал
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().archive?.mode).isEqualTo(CatchupMode.UNKNOWN)
    }

    @Test
    fun `tvg-rec нулевой глубины не даёт архива`() {
        // Провайдер ставит tvg-rec="0" каналам без архива: глубина ноль — архива нет.
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="a" tvg-rec="0",Канал A
            http://host/a.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().archive).isNull()
    }

    @Test
    fun `передача без catchup-тегов не получает архив`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Канал
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().archive).isNull()
    }

    @Test
    fun `мусорные строки вне EXTINF пропускаются`() {
        val playlist = parseOk(
            """
            #EXTM3U
            Это не тег и не ссылка
            #EXTINF:-1,Первый
            http://example.com/1.m3u8
            ещё мусор
            """.trimIndent(),
        )

        assertThat(playlist.channels).hasSize(1)
        assertThat(playlist.channels.single().name).isEqualTo("Первый")
    }

    @Test
    fun `EXTINF без ссылки пропускается, остальные разбираются`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Без потока
            #EXTINF:-1,С потоком
            http://example.com/ok.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.map { it.name }).containsExactly("С потоком")
    }

    @Test
    fun `пустой текст даёт пустой плейлист`() {
        val playlist = parseOk("")

        assertThat(playlist.channels).isEmpty()
        assertThat(playlist.categories).isEmpty()
    }

    @Test
    fun `ответ не похожий на плейлист даёт ошибку`() {
        val result = parser.parse("<html><body>403 Forbidden</body></html>")

        assertThat(result).isInstanceOf(M3uParseResult.Failure::class.java)
    }

    @Test
    fun `CRLF и BOM не ломают разбор`() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:-1 tvg-id=\"one\",Первый\r\nhttp://example.com/1.m3u8\r\n"

        val playlist = parseOk(text)

        assertThat(playlist.channels.single().name).isEqualTo("Первый")
        assertThat(playlist.channels.single().id).isEqualTo(ChannelId("one"))
    }

    @Test
    fun `url с query-параметрами сохраняется целиком`() {
        val url = "http://example.com/live?username=u&password=p&id=1"

        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Канал
            $url
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().streamUrl).isEqualTo(url)
    }

    @Test
    fun `имя канала может содержать запятую`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 group-title="A",Канал, HD
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().name).isEqualTo("Канал, HD")
    }

    @Test
    fun `разбирает заголовки потока из EXTVLCOPT`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Канал
            #EXTVLCOPT:http-user-agent=SuperPlayer/1.0
            #EXTVLCOPT:http-referrer=http://ref.example.com/
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        val headers = playlist.channels.single().headers
        assertThat(headers?.userAgent).isEqualTo("SuperPlayer/1.0")
        assertThat(headers?.referrer).isEqualTo("http://ref.example.com/")
    }

    @Test
    fun `разбирает KODIPROP в заголовки`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Канал
            #KODIPROP:inputstream.adaptive.stream_headers=User-Agent=Mozilla
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().headers?.headers)
            .containsEntry("inputstream.adaptive.stream_headers", "User-Agent=Mozilla")
    }

    @Test
    fun `относительный url разрешается относительно baseUrl`() {
        val result = parser.parse(
            """
            #EXTM3U
            #EXTINF:-1,Канал
            channels/one.m3u8
            """.trimIndent(),
            baseUrl = "http://example.com/playlist/list.m3u",
        )

        val playlist = (result as M3uParseResult.Success).playlist
        assertThat(playlist.channels.single().streamUrl)
            .isEqualTo("http://example.com/playlist/channels/one.m3u8")
    }

    @Test
    fun `относительный url без baseUrl пропускается`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Канал
            channels/one.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels).isEmpty()
    }

    @Test
    fun `архив из tvg-rec когда catchup нет`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 tvg-rec="7",Канал
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().archive).isEqualTo(ArchiveInfo(CatchupMode.DEFAULT, days = 7))
    }

    @Test
    fun `архив из tv_archive с длительностью`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 tv_archive="1" tv_archive_duration="3",Канал
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().archive).isEqualTo(ArchiveInfo(CatchupMode.DEFAULT, days = 3))
    }

    @Test
    fun `catchup имеет приоритет над tvg-rec`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 catchup="append" tvg-rec="7",Канал
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().archive?.mode).isEqualTo(CatchupMode.APPEND)
    }

    @Test
    fun `читает url-tvg из заголовка плейлиста`() {
        val playlist = parseOk(
            """
            #EXTM3U url-tvg="http://example.com/epg.xml.gz"
            #EXTINF:-1,Канал
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.epgUrl).isEqualTo("http://example.com/epg.xml.gz")
    }

    @Test
    fun `читает x-tvg-url из заголовка плейлиста`() {
        val playlist = parseOk(
            """
            #EXTM3U x-tvg-url="http://example.com/epg2.xml"
            #EXTINF:-1,Канал
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.epgUrl).isEqualTo("http://example.com/epg2.xml")
    }

    @Test
    fun `без url-tvg адрес программы передач отсутствует`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1,Канал
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.epgUrl).isNull()
    }

    @Test
    fun `относительный url-tvg разрешается относительно baseUrl`() {
        val result = parser.parse(
            """
            #EXTM3U url-tvg="epg.xml"
            #EXTINF:-1,Канал
            http://example.com/1.m3u8
            """.trimIndent(),
            baseUrl = "http://example.com/playlist/list.m3u",
        )

        val playlist = (result as M3uParseResult.Success).playlist
        assertThat(playlist.epgUrl).isEqualTo("http://example.com/playlist/epg.xml")
    }

    @Test
    fun `имя канала берётся и без запятой перед ним`() {
        val playlist = parseOk(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="one" group-title="Новости" Первый канал
            http://example.com/1.m3u8
            """.trimIndent(),
        )

        assertThat(playlist.channels.single().name).isEqualTo("Первый канал")
    }
}
