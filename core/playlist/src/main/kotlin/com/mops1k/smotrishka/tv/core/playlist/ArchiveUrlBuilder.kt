package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.CatchupMode
import java.time.Instant

/**
 * Строит адрес архивного потока по тегам catchup из плейлиста.
 *
 * Режимы:
 * - `default` — параметры `utc`/`utcend` в query (секунды эпохи);
 * - `append` — начало и конец сегментами пути;
 * - `shift` — `timeshift` в минутах от текущего момента;
 * - `flussonic`/`flussonic-hls` — формат Flussonic
 *   `index-<начало>-<длительность>.m3u8`, для идущей передачи — `index-<начало>-now.m3u8`
 *   (https://flussonic.com/doc/fms/dvr/playback/);
 * - `catchup-source` — шаблон провайдера: подставляются `{utc}`, `{start}`, `{utcend}`,
 *   `{end}`, `{duration}` (в вариантах `${имя}` и `{имя}`), остальные плейсхолдеры
 *   остаются как есть, чтобы расхождение с провайдером было видно, а не спрятано.
 *
 * Класс синхронный и не делает ввода-вывода: текущее время приходит в запросе.
 */
class ArchiveUrlBuilder {

    /**
     * Строит архивный адрес.
     *
     * @param request канал, передача и текущий момент.
     */
    fun build(request: ArchiveRequest): ArchiveUrlResult {
        val archive = request.archive
            ?: return ArchiveUrlResult.Unavailable("У канала нет архива")

        val start = request.programmeStart
            ?: return ArchiveUrlResult.Unavailable("Неизвестно время начала передачи")

        if (start.isAfter(request.now)) {
            return ArchiveUrlResult.Unavailable("Передача ещё не началась")
        }

        if (archive.days > 0 && start.epochSecond < request.now.epochSecond - archive.days * SECONDS_PER_DAY) {
            return ArchiveUrlResult.Unavailable(
                "Передача глубже архива провайдера: доступно ${archive.days} дн.",
            )
        }

        val template = archive.source?.takeIf(String::isNotBlank)
        if (template != null) {
            return ArchiveUrlResult.Available(fillTemplate(template, start, request))
        }

        val startEpoch = start.epochSecond
        // Будущее в архивном запросе недоступно: просим максимум до текущего момента.
        val endEpoch = minOf(request.programmeEnd ?: request.now, request.now).epochSecond

        return when (archive.mode) {
            CatchupMode.DEFAULT -> ArchiveUrlResult.Available(
                appendQuery(request.streamUrl, "utc=$startEpoch&utcend=$endEpoch"),
            )

            CatchupMode.APPEND -> ArchiveUrlResult.Available(
                request.streamUrl.trimEnd('/') + "/" + startEpoch + "/" + endEpoch,
            )

            CatchupMode.SHIFT -> ArchiveUrlResult.Available(
                appendQuery(request.streamUrl, "timeshift=" + (request.now.epochSecond - startEpoch) / SECONDS_PER_MINUTE),
            )

            CatchupMode.FLUSSONIC, CatchupMode.FLUSSONIC_HLS -> ArchiveUrlResult.Available(
                flussonicUrl(request.streamUrl, start, request),
            )

            CatchupMode.UNKNOWN -> ArchiveUrlResult.Unavailable(
                "Провайдер не сообщил формат архива (catchup без понятного режима)",
            )
        }
    }

    /** Подставляет значения в шаблон `catchup-source`; незнакомые плейсхолдеры не трогает. */
    private fun fillTemplate(template: String, start: Instant, request: ArchiveRequest): String {
        val startEpoch = start.epochSecond
        val endEpoch = minOf(request.programmeEnd ?: request.now, request.now).epochSecond
        val values = mapOf(
            "utc" to startEpoch.toString(),
            "start" to startEpoch.toString(),
            "utcend" to endEpoch.toString(),
            "end" to endEpoch.toString(),
            "duration" to (endEpoch - startEpoch).toString(),
        )

        return PLACEHOLDER.replace(template) { match ->
            val name = match.groupValues[1].ifEmpty { match.groupValues[2] }
            values[name] ?: match.value
        }
    }

    /**
     * Адрес архива в формате Flussonic: последний сегмент пути заменяется на `index-…`.
     *
     * Живой поток Flussonic отдаётся как `/<поток>/index.m3u8`, архив — как
     * `/<поток>/index-<начало>-<длительность>.m3u8`, поэтому имя файла из живого URL
     * отбрасывается.
     */
    private fun flussonicUrl(streamUrl: String, start: Instant, request: ArchiveRequest): String {
        val trimmed = streamUrl.trimEnd('/')
        val base = trimmed.substringBeforeLast('/', trimmed)
        val startEpoch = start.epochSecond
        val end = request.programmeEnd

        return if (end != null && end.isAfter(request.now)) {
            "$base/index-$startEpoch-now.m3u8"
        } else {
            val endEpoch = (end ?: request.now).epochSecond
            "$base/index-$startEpoch-${endEpoch - startEpoch}.m3u8"
        }
    }

    private fun appendQuery(url: String, query: String): String =
        if (url.contains('?')) "$url&$query" else "$url?$query"

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
        const val SECONDS_PER_MINUTE = 60L

        val PLACEHOLDER = Regex("""\$\{(\w+)}|\{(\w+)}""")
    }
}
