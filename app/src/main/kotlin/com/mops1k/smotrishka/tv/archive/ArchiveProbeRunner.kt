package com.mops1k.smotrishka.tv.archive

import android.util.Log
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.ui.player.PlayerHttp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Проверяет, отдаёт ли провайдер архив по каждому каналу.
 *
 * Значок архива в списке опирается на этот результат: тег `tvg-rec` в плейлисте бывает
 * недостоверным (провайдер заявляет глубину, но отдаёт пустой плейлист). Проверка дешёвая —
 * качается только текст плейлиста, сегменты не запрашиваются.
 *
 * @param catalogStore каталог каналов;
 * @param settingsStore хранилище результата проверки.
 */
@Singleton
class ArchiveProbeRunner @Inject constructor(
    private val catalogStore: CatalogStore,
    private val settingsStore: PlayerSettingsStore,
) {

    /**
     * Запускает проверку, если она не выполнялась сутки.
     *
     * @param nowMillis текущее время, миллисекунды эпохи.
     */
    suspend fun runIfStale(nowMillis: Long = System.currentTimeMillis()) {
        val lastAt = settingsStore.archiveVerifiedAt.first()
        if (lastAt != 0L && nowMillis - lastAt < STALE_MILLIS) return
        run()
    }

    /** Проверяет архив по всем каналам с заявленной глубиной. */
    suspend fun run() = withContext(Dispatchers.IO) {
        val channels = catalogStore.playlist().channels
            .filter { it.archive != null }
            .map { it.id.value to it.streamUrl.substringBefore('?') }
        if (channels.isEmpty()) return@withContext

        val nowSeconds = System.currentTimeMillis() / 1000
        val start = nowSeconds - PROBE_OFFSET_SECONDS
        val verified = mutableSetOf<String>()

        channels.chunked(PARALLEL).forEach { chunk ->
            coroutineScope {
                chunk.map { (id, base) ->
                    async { id.takeIf { hasArchive(base, start, nowSeconds) } }
                }.awaitAll().filterNotNull().let { verified += it }
            }
        }

        settingsStore.saveArchiveVerified(verified, System.currentTimeMillis())
        Log.d(
            "SmotrishkaSync",
            "archive probe: проверено=${channels.size} подтверждено=${verified.size}",
        )
    }

    /** Есть ли архив у канала: первый сегмент ответа должен быть в запрошенном окне. */
    private fun hasArchive(base: String, startSeconds: Long, nowSeconds: Long): Boolean = runCatching {
        val request = Request.Builder().url("$base?utc=$startSeconds&lutc=$nowSeconds").build()
        val body = PlayerHttp.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) "" else response.body?.string().orEmpty()
        }
        val first = FIRST_SEGMENT.find(body)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return false
        // Метка времени сегмента в миллисекундах: сравниваем с запрошенной секундой.
        kotlin.math.abs(first / 1000 - startSeconds) <= TOLERANCE_SECONDS
    }.getOrDefault(false)

    private companion object {
        /** Сколько каналов проверяем одновременно. */
        const val PARALLEL = 8

        /** Насколько назад запрашиваем архив при проверке. */
        const val PROBE_OFFSET_SECONDS = 1800L

        /** Допустимое расхождение первой метки с запрошенным временем. */
        const val TOLERANCE_SECONDS = 600L

        /** Через сколько повторяем проверку. */
        const val STALE_MILLIS = 24L * 60L * 60L * 1000L

        /** Метка времени в имени архивного сегмента (`/<epochMillis>.ts`). */
        val FIRST_SEGMENT = Regex("""/(\d{13})\.ts""")
    }
}
