package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

/**
 * TDD-спецификация профиля провайдера «по коду» (edem).
 *
 * Механизм снят с работающего JS-плеера: код — это ключ доступа, хост потока зашит
 * в профиль провайдера, канал-лист общий и кода не содержит. Ссылка канала собирается
 * как `<хост>/iptv/<код>/<id канала>/index.m3u8`.
 */
class IedemProviderTest {

    private val provider = IedemProvider()

    @Test
    fun `список каналов не зависит от кода`() {
        assertThat(provider.channelListUrl())
            .isEqualTo("https://epg.ottp.eu.org/edem/edem_epg_ico.m3u8")
    }

    @Test
    fun `ссылка канала содержит код и id канала`() {
        val url = provider.streamUrl(code = "LHS2AYB8R2MLPR", channelId = "2402")

        assertThat(url).isEqualTo(
            "http://sewv654wfcsdwfi87fwvgbngh.siauliairsavlt.pw/iptv/LHS2AYB8R2MLPR/2402/index.m3u8",
        )
    }

    @Test
    fun `id канала извлекается из шаблонной ссылки`() {
        assertThat(provider.channelIdOf("http://localhost/iptv/00000000000000/2402/index.m3u8"))
            .isEqualTo("2402")
    }

    @Test
    fun `чужая ссылка не даёт id канала`() {
        assertThat(provider.channelIdOf("http://example.com/stream.m3u8")).isNull()
    }

    @Test
    fun `архивная ссылка содержит начало передачи и текущий момент`() {
        val start = Instant.parse("2026-11-03T18:00:00Z")
        val now = Instant.parse("2026-11-03T20:00:00Z")

        val url = provider.archiveUrl(code = "ABC123", channelId = "2402", start = start, now = now)

        assertThat(url).contains("?utc=${start.epochSecond}")
        assertThat(url).contains("&lutc=${now.epochSecond}")
        assertThat(url).contains("/ABC123/2402/index.m3u8")
    }

    @Test
    fun `пустой код отвергается`() {
        assertThat(provider.validate("   ")).isInstanceOf(IedemCodeResult.Invalid::class.java)
    }

    @Test
    fun `код со слэшем отвергается`() {
        val result = provider.validate("abc/../def")

        assertThat((result as IedemCodeResult.Invalid).reason).isNotEmpty()
    }

    @Test
    fun `пробелы вокруг кода срезаются`() {
        val result = provider.validate("  abc123  ") as IedemCodeResult.Success

        assertThat(result.code).isEqualTo("abc123")
    }
}
