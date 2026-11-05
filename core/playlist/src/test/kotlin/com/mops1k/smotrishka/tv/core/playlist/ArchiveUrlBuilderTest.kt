package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import org.junit.Test
import java.time.Instant

/**
 * TDD-спецификация построения архивного URL.
 *
 * Семантика режимов:
 * - `default` — параметры `utc`/`utcend` в query;
 * - `append` — время начала и конца сегментами пути;
 * - `shift` — `timeshift` в минутах от текущего момента;
 * - `flussonic`/`flussonic-hls` — формат Flussonic `index-<начало>-<длительность>.m3u8`;
 * - `catchup-source` — шаблон провайдера с подстановкой `{utc}`, `{utcend}`, `{start}`, `{end}`, `{duration}`.
 */
class ArchiveUrlBuilderTest {

    private val builder = ArchiveUrlBuilder()

    private val streamUrl = "http://example.com/live/one.m3u8"

    /** Передача «вчера с 12:00 до 13:00», сейчас — 20:00 того же дня. */
    private val programmeStart: Instant = Instant.parse("2026-11-02T12:00:00Z")
    private val programmeEnd: Instant = Instant.parse("2026-11-02T13:00:00Z")
    private val now: Instant = Instant.parse("2026-11-02T20:00:00Z")

    private val startEpoch = programmeStart.epochSecond
    private val endEpoch = programmeEnd.epochSecond

    private fun request(
        mode: CatchupMode = CatchupMode.DEFAULT,
        source: String? = null,
        days: Int = 7,
        start: Instant? = programmeStart,
        end: Instant? = programmeEnd,
    ) = ArchiveRequest(
        streamUrl = streamUrl,
        archive = ArchiveInfo(mode = mode, source = source, days = days),
        programmeStart = start,
        programmeEnd = end,
        now = now,
    )

    private fun url(request: ArchiveRequest): String =
        (builder.build(request) as ArchiveUrlResult.Available).url

    private fun reason(request: ArchiveRequest): String =
        (builder.build(request) as ArchiveUrlResult.Unavailable).reason

    @Test
    fun `канал без архива не даёт архивный url`() {
        val result = builder.build(
            ArchiveRequest(
                streamUrl = streamUrl,
                archive = null,
                programmeStart = programmeStart,
                programmeEnd = programmeEnd,
                now = now,
            ),
        )

        assertThat(result).isInstanceOf(ArchiveUrlResult.Unavailable::class.java)
        assertThat(reason(result)).contains("архив")
    }

    private fun reason(result: ArchiveUrlResult) = (result as ArchiveUrlResult.Unavailable).reason

    @Test
    fun `default добавляет utc и utcend в query`() {
        assertThat(url(request())).isEqualTo("$streamUrl?utc=$startEpoch&utcend=$endEpoch")
    }

    @Test
    fun `default к url с query добавляет параметры через амперсанд`() {
        val request = request().copy(streamUrl = "$streamUrl?token=abc")

        assertThat(url(request)).isEqualTo("$streamUrl?token=abc&utc=$startEpoch&utcend=$endEpoch")
    }

    @Test
    fun `append добавляет начало и конец сегментами пути`() {
        assertThat(url(request(mode = CatchupMode.APPEND)))
            .isEqualTo("$streamUrl/$startEpoch/$endEpoch")
    }

    @Test
    fun `append убирает завершающий слэш`() {
        val request = request(mode = CatchupMode.APPEND).copy(streamUrl = "$streamUrl/")

        assertThat(url(request)).isEqualTo("$streamUrl/$startEpoch/$endEpoch")
    }

    @Test
    fun `shift добавляет timeshift в минутах от текущего момента`() {
        // Сейчас 20:00, передача началась в 12:00 → 480 минут назад.
        assertThat(url(request(mode = CatchupMode.SHIFT)))
            .isEqualTo("$streamUrl?timeshift=480")
    }

    @Test
    fun `flussonic строит файл архива с началом и длительностью`() {
        val expected = "http://example.com/live/index-$startEpoch-3600.m3u8"

        assertThat(url(request(mode = CatchupMode.FLUSSONIC))).isEqualTo(expected)
        assertThat(url(request(mode = CatchupMode.FLUSSONIC_HLS))).isEqualTo(expected)
    }

    @Test
    fun `flussonic понимает url вида index m3u8`() {
        val request = request(mode = CatchupMode.FLUSSONIC)
            .copy(streamUrl = "http://example.com/live/one/index.m3u8")

        assertThat(url(request)).isEqualTo("http://example.com/live/one/index-$startEpoch-3600.m3u8")
    }

    @Test
    fun `flussonic для идущей передачи использует now`() {
        val request = request(mode = CatchupMode.FLUSSONIC, end = now.plusSeconds(1800))

        assertThat(url(request)).isEqualTo("http://example.com/live/index-$startEpoch-now.m3u8")
    }

    @Test
    fun `шаблон catchup-source подставляет время и длительность`() {
        val request = request(
            source = "http://example.com/arch?start={utc}&end={utcend}&len={duration}",
        )

        assertThat(url(request))
            .isEqualTo("http://example.com/arch?start=$startEpoch&end=$endEpoch&len=3600")
    }

    @Test
    fun `шаблон понимает вариант с фигурными долларами`() {
        val request = request(source = "http://example.com/arch/\${start}/\${end}.m3u8")

        assertThat(url(request)).isEqualTo("http://example.com/arch/$startEpoch/$endEpoch.m3u8")
    }

    @Test
    fun `неизвестный плейсхолдер в шаблоне остаётся как есть`() {
        val request = request(source = "http://example.com/arch?start={utc}&x={unknown}")

        assertThat(url(request)).isEqualTo("http://example.com/arch?start=$startEpoch&x={unknown}")
    }

    @Test
    fun `передача старше глубины архива недоступна`() {
        // Глубина 1 день, передача была позавчера.
        val request = request(days = 1, start = now.minusSeconds(2 * 24 * 3600))

        assertThat(reason(request)).contains("глубже архива")
    }

    @Test
    fun `без времени начала архивный url не строится`() {
        val request = request(start = null)

        assertThat(reason(request)).contains("начал")
    }

    @Test
    fun `неизвестный режим без шаблона не угадывается`() {
        val request = request(mode = CatchupMode.UNKNOWN)

        assertThat(reason(request)).contains("формат")
    }
}
