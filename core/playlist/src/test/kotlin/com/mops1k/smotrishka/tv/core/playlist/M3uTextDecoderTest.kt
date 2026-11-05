package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.nio.charset.Charset

/**
 * TDD-спецификация определения кодировки плейлиста.
 *
 * Провайдеры отдают M3U и в UTF-8, и в cp1251, поэтому декодер обязан
 * не оставлять «кракозябры» в названиях каналов и не падать на битых байтах.
 */
class M3uTextDecoderTest {

    private val decoder = M3uTextDecoder()

    private val cp1251: Charset = Charset.forName("windows-1251")

    @Test
    fun `utf8 без BOM декодируется как utf8`() {
        val text = "#EXTM3U\n#EXTINF:-1,Первый канал\nhttp://example.com/1.m3u8"

        assertThat(decoder.decode(text.toByteArray(Charsets.UTF_8))).isEqualTo(text)
    }

    @Test
    fun `utf8 с BOM декодируется без BOM в начале`() {
        val text = "#EXTM3U\n#EXTINF:-1,Первый"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)

        val decoded = decoder.decode(bytes)

        assertThat(decoded).isEqualTo(text)
        assertThat(decoded.startsWith("#EXTM3U")).isTrue()
    }

    @Test
    fun `utf16 с BOM декодируется`() {
        val text = "#EXTM3U\n#EXTINF:-1,Первый"
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)

        assertThat(decoder.decode(bytes)).isEqualTo(text)
    }

    @Test
    fun `cp1251 с кириллицей распознаётся`() {
        val text = "#EXTM3U\n#EXTINF:-1,Первый канал\nhttp://example.com/1.m3u8"

        val decoded = decoder.decode(text.toByteArray(cp1251))

        assertThat(decoded).isEqualTo(text)
    }

    @Test
    fun `пустой массив даёт пустую строку`() {
        assertThat(decoder.decode(ByteArray(0))).isEmpty()
    }

    @Test
    fun `битые байты не роняют декодер`() {
        val bytes = byteArrayOf(0x23, 0x45, 0x58, 0xFF.toByte(), 0xFE.toByte(), 0x80.toByte())

        assertThat(decoder.decode(bytes)).isNotEmpty()
    }
}
