package com.mops1k.smotrishka.tv.ui.player

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Скачивает эфир сегментами для локального HLS.
 *
 * Сегмент пишется отдельным файлом. Если между текущим и предыдущим сегментом рвутся метки
 * времени (провайдер пропустил кусок) или мы пропустили битый сегмент, следующий за ним
 * помечается как разрыв — сервер отдаст его с `#EXT-X-DISCONTINUITY`.
 *
 * @param client общий HTTP-клиент;
 * @param cacheDir каталог для сегментов;
 * @param waitLimitMs сколько ждать готовый сегмент, прежде чем пометить разрыв;
 * @param pollDelayMs пауза между опросами плейлиста, когда новых сегментов нет.
 */
class LiveBufferLoader(
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val waitLimitMs: Long = WAIT_LIMIT_MS,
    private val pollDelayMs: Long = POLL_DELAY_MS,
) {

    /**
     * Качает сегменты, пока [isActive] возвращает `true`.
     *
     * @param streamUrl адрес канала;
     * @param onSegment файл сегмента и признак разрыва перед ним;
     * @param onGap сегмент не получен (пропуск в эфире);
     * @param isActive продолжать ли скачивание;
     * @param onConnectivity состояние связи: `false` — поток не получить, `true` — связь вернулась.
     */
    suspend fun load(
        streamUrl: String,
        onSegment: (File, Boolean) -> Unit,
        onGap: () -> Unit = {},
        isActive: () -> Boolean = { true },
        onConnectivity: (Boolean) -> Unit = {},
        onHopeless: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val written = mutableSetOf<Long>()
        var successes = 0
        var givenUp = 0
        var lastTimestamp = 0L
        var lastPts = 0L
        var brokenBefore = false
        // Связь считаем рабочей, пока не набралось подряд несколько неудач:
        // одиночный сбой сети не должен поднимать плашку в плеере.
        var failures = 0
        var online = true

        while (isActive()) {
            val started = System.currentTimeMillis()
            val playlist = fetchText(streamUrl)
            if (playlist == null) {
                failures++
                if (online && failures >= OFFLINE_AFTER_FAILURES) {
                    online = false
                    onConnectivity(false)
                }
                delay(pollDelayMs)
                continue
            }
            if (!online) {
                online = true
                onConnectivity(true)
            }
            failures = 0
            val segments = LivePlaylist.segments(playlist)
            Log.d(TAG, "playlist ${System.currentTimeMillis() - started}ms segments=${segments.size}")
            val pending = segments.mapNotNull { url ->
                val timestamp = LiveSegments.timestampOf(url) ?: return@mapNotNull null
                if (!written.add(timestamp)) return@mapNotNull null
                timestamp to url
            }

            if (pending.isEmpty()) {
                delay(pollDelayMs)
                continue
            }

            // Качаем по PREFETCH сегментов параллельно: последовательная загрузка не успевала
            // за воспроизведением, и буфер на здоровых каналах успевал кончиться.
            for (batch in pending.chunked(PREFETCH)) {
                val loaded = coroutineScope {
                    batch.map { (timestamp, url) ->
                        async(Dispatchers.IO) { Triple(timestamp, url, downloadUntilValid(url)) }
                    }.awaitAll()
                }

                for ((timestamp, _, bytes) in loaded) {
                    if (!isActive()) break
                    if (bytes == null) {
                        Log.w(TAG, "gave up segment $timestamp after ${waitLimitMs}ms")
                        onGap()
                        written.remove(timestamp)
                        brokenBefore = true
                        givenUp++
                        // Ни одного сегмента за несколько попыток: прокси на этом канале бесполезен.
                        // Экран обязан вернуться к прямому потоку, иначе «Загрузка» висит вечно.
                        if (successes == 0 && givenUp >= HOPELESS_SEGMENTS) {
                            Log.w(TAG, "прокси не получил ни одного сегмента — отдаём канал напрямую")
                            onHopeless()
                            return@withContext
                        }
                        continue
                    }
                    successes++

                    // Скачок меток времени: вместо маркера разрыва выравниваем PTS, чтобы
                    // плеер не перезапускал декодер и картинка не вставала на паузу.
                    val pts = TsRewriter.firstPts(bytes)
                    var prepared = bytes
                    if (pts != null && lastPts != 0L && lastTimestamp != 0L) {
                        val expected = lastPts + (timestamp - lastTimestamp) * 90L
                        if (TsTimeline.needsShift(expected, pts)) {
                            prepared = TsRewriter.shift(bytes, TsTimeline.offset(expected, pts))
                            Log.d(TAG, "pts shifted by ${TsTimeline.offset(expected, pts)}")
                        }
                    }
                    if (pts != null) lastPts = TsRewriter.firstPts(prepared) ?: pts

                    val discontinuity = brokenBefore ||
                        (lastTimestamp != 0L && timestamp - lastTimestamp > GAP_MILLIS)
                    val file = File(cacheDir, "seg-$timestamp.ts")
                    val saved = runCatching { file.writeBytes(prepared) }
                    if (saved.isFailure) {
                        // Диск заполнен или кэш вычищен системой: пропускаем кусок, а не падаем.
                        Log.e(TAG, "segment $timestamp not saved: ${saved.exceptionOrNull()?.message}")
                        written.remove(timestamp)
                        brokenBefore = true
                        delay(pollDelayMs)
                        continue
                    }
                    Log.d(TAG, "segment $timestamp size=${prepared.size} discontinuity=$discontinuity")
                    onSegment(file, discontinuity)

                    lastTimestamp = timestamp
                    brokenBefore = false
                }
            }
        }
    }

    /** Ждёт целый сегмент: битый кусок повторяем, пока не истечёт [waitLimitMs]. */
    private suspend fun downloadUntilValid(url: String): ByteArray? {
        val deadline = System.currentTimeMillis() + waitLimitMs
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            attempt++
            val bytes = runCatching { fetchBytes(url) }
                .onFailure { Log.w(TAG, "segment error ${it::class.simpleName}: ${it.message} url=…${url.takeLast(48)}") }
                .getOrNull()
            if (bytes != null && bytes.size >= MIN_SEGMENT_BYTES) return bytes
            Log.w(TAG, "segment not ready attempt=$attempt size=${bytes?.size ?: 0} next=${SegmentRetry.delayBefore(attempt)}ms")
            delay(SegmentRetry.delayBefore(attempt))
        }
        Log.w(TAG, "segment deadline: попыток=$attempt за ${waitLimitMs}мс url=…${url.takeLast(48)}")
        return null
    }

    private suspend fun downloadWithRetry(url: String): ByteArray? {
        var attempt = 0
        while (true) {
            attempt++
            val bytes = runCatching { fetchBytes(url) }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) return bytes
            if (!SegmentRetry.canRetry(attempt)) return null
            delay(SegmentRetry.delayBefore(attempt))
        }
    }

    /**
     * Опрос плейлиста.
     *
     * Сеть у провайдера рвётся и висит: сбой опроса не должен уходить наружу. 04.11.2026
     * `SocketTimeoutException` из этого вызова дошёл до корутины Compose и закрыл приложение.
     *
     * @param url адрес плейлиста;
     * @return текст плейлиста либо `null`, если получить его не удалось.
     */
    private fun fetchText(url: String): String? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string().orEmpty()
        }
    }.onFailure {
        Log.w(TAG, "playlist fetch failed: ${it.message}")
    }.getOrNull()

    private fun fetchBytes(url: String): ByteArray =
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "segment http ${response.code} len=${response.body?.contentLength()} url=…${url.takeLast(48)}")
            }
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.size < MIN_SEGMENT_BYTES) {
                Log.w(TAG, "segment short ${bytes.size}B url=…${url.takeLast(48)}")
            }
            bytes
        }

    private companion object {
        const val TAG = "SmotrishkaBuffer"
        const val MIN_SEGMENT_BYTES = 300_000

        /** Сколько сегментов качаем одновременно. */
        const val PREFETCH = 4

        /** Сколько ждём готовый сегмент, прежде чем пометить разрыв и идти дальше. */
        const val WAIT_LIMIT_MS = 20_000L

        /** Сколько сегментов подряд отдаём без ответа, прежде чем отказаться от прокси. */
        const val HOPELESS_SEGMENTS = 2

        /** Сколько неудачных опросов плейлиста подряд считаем обрывом связи. */
        const val OFFLINE_AFTER_FAILURES = 2

        /** Разрыв в метках времени между сегментами. */
        const val GAP_MILLIS = 12_000L

        const val POLL_DELAY_MS = 1_500L
    }
}
