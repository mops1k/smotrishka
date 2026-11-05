package com.mops1k.smotrishka.tv.ui.player

import com.mops1k.smotrishka.tv.core.data.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.time.Instant

/**
 * Собирает архивную передачу в один файл.
 *
 * Провайдер отдаёт архив кусками примерно по минуте: запрос с `utc` возвращает плейлист из
 * нескольких сегментов, начинающихся ровно с этого момента. Поэтому передача скачивается
 * последовательными чанками с растущим `utc` и склеивается в `.ts`, а плеер уже играет файл —
 * только так работают и воспроизведение с начала передачи, и перемотка.
 *
 * @param client общий HTTP-клиент приложения;
 * @param cacheDir каталог для временных файлов.
 */
class ArchiveRecorder(
    private val client: OkHttpClient,
    private val cacheDir: File,
) {

    /**
     * Скачивает передачу в файл.
     *
     * @param streamUrl адрес живого канала (без параметров архива);
     * @param start начало передачи;
     * @param end конец передачи;
     * @param onProgress сколько секунд записи уже готово.
     * @return файл с передачей.
     */
    suspend fun record(
        streamUrl: String,
        start: Instant,
        end: Instant,
        onProgress: (Long) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val file = File.createTempFile("archive-", ".ts", cacheDir)
        file.deleteOnExit()
        var written = 0L

        for (chunkStart in ArchiveChunks.offsets(start.epochSecond, end.epochSecond)) {
            val chunkEnd = minOf(chunkStart + CHUNK_SECONDS, end.epochSecond)
            val playlistUrl = "$streamUrl${'?'}utc=$chunkStart&lutc=$chunkEnd"
            val playlist = downloadText(playlistUrl)
            val segments = playlist.lines().filter { it.startsWith("http") }

            for (segment in segments) {
                file.appendBytes(downloadBytes(segment))
                written += SEGMENT_SECONDS
                onProgress(written)
            }
        }

        file
    }

    /**
     * Отдаёт сегменты передачи по одному, не дожидаясь скачивания целиком.
     *
     * Нужен для потокового воспроизведения архива: первые сегменты играются сразу,
     * остальные докачиваются впереди и дописываются в локальный плейлист.
     */
    suspend fun stream(
        streamUrl: String,
        start: Instant,
        end: Instant,
        onSegment: (File, Boolean) -> Unit,
        positionMs: () -> Long = { 0L },
    ): Int = withContext(Dispatchers.IO) {
        var index = 0
        var first = true
        var delivered = 0
        for (chunkStart in ArchiveChunks.offsets(start.epochSecond, end.epochSecond)) {
            val chunkEnd = minOf(chunkStart + CHUNK_SECONDS, end.epochSecond)
            // Контракт провайдера как в ott-play: utc — начало окна, lutc — текущий момент.
            val nowSeconds = System.currentTimeMillis() / 1000
            val playlistUrl = "$streamUrl?utc=$chunkStart&lutc=$nowSeconds"
            val segments = downloadText(playlistUrl).lines().filter { it.startsWith("http") }
            for (segment in segments) {
                val bytes = downloadBytes(segment)
                // Короткий огрызок вместо сегмента — дыра у провайдера, играть его нельзя.
                if (bytes.size < MIN_SEGMENT_BYTES) continue
                val file = File(cacheDir, "arch-${start.epochSecond}-${index++}.ts")
                file.writeBytes(bytes)
                onSegment(file, !first && chunkStart != firstChunkStart(playlistUrl, start))
                first = false
                delivered++
                // Не убегаем вперёд больше чем на запас: скачивание ждёт позицию плеера.
                while (delivered * SEGMENT_SECONDS * 1000L - positionMs() > PREFETCH_AHEAD_MS) {
                    delay(SEGMENT_SECONDS * 50L)
                }
            }
        }
        delivered
    }

    private fun firstChunkStart(playlistUrl: String, start: Instant): Long =
        playlistUrl.substringAfter("utc=", start.epochSecond.toString()).substringBefore('&').toLongOrNull()
            ?: start.epochSecond

    /**
     * Отдаёт ссфлки на архивные сегменты, не скачивая их.
     *
     * Нужно для воспроизведения без диска: локальный сервер получает адреса сегментов и
     * отдаёт плееру редирект на провайдера.
     */
    suspend fun streamUrls(
        streamUrl: String,
        start: Instant,
        end: Instant,
        onUrl: suspend (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        for (chunkStart in ArchiveChunks.offsets(start.epochSecond, end.epochSecond)) {
            val nowSeconds = System.currentTimeMillis() / 1000
            val playlist = downloadText("$streamUrl?utc=$chunkStart&lutc=$nowSeconds")
            playlist.lines().filter { it.startsWith("http") }.forEach { onUrl(it) }
        }
    }

    // Сеть у провайдера рвётся и висит: исключение наружу не выпускаем, иначе падает экран.
    private fun downloadText(url: String): String = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) "" else response.body?.string().orEmpty()
        }
    }.getOrDefault("")

    private fun downloadBytes(url: String): ByteArray = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) ByteArray(0) else response.body?.bytes() ?: ByteArray(0)
        }
    }.getOrDefault(ByteArray(0))

    companion object {
        /** Шаг чанка: провайдер отдаёт примерно минуту за запрос. */
        const val CHUNK_SECONDS = 60L

        /** Длительность одного сегмента архива. */
        const val SEGMENT_SECONDS = 10L

        /** Сегмент меньше этого размера — обрезанный кусок, а не видео. */
        const val MIN_SEGMENT_BYTES = 100_000

        /** Сколько секунд записи держим скачанными впереди позиции воспроизведения. */
        const val PREFETCH_AHEAD_MS = 180_000L
    }
}

/**
 * Список моментов, с которых качать архивные чанки.
 *
 * Вынесено отдельно, чтобы математика покрывалась тестом без сети.
 */
object ArchiveChunks {

    /**
     * Моменты старта чанков от начала передачи до конца.
     *
     * @param startEpochSeconds начало передачи;
     * @param endEpochSeconds конец передачи;
     * @param chunkSeconds шаг чанка.
     */
    fun offsets(
        startEpochSeconds: Long,
        endEpochSeconds: Long,
        chunkSeconds: Long = 60L,
    ): List<Long> {
        if (endEpochSeconds <= startEpochSeconds) return emptyList()
        val result = mutableListOf<Long>()
        var cursor = startEpochSeconds
        while (cursor < endEpochSeconds) {
            result += cursor
            cursor += chunkSeconds
        }
        return result
    }
}

/** Ограничение качества, применяемое при сборке файла архива. */
internal fun archiveQuality(quality: VideoQuality): VideoQuality = quality
