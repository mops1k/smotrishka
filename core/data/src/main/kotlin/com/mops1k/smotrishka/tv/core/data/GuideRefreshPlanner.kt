package com.mops1k.smotrishka.tv.core.data

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планирует обновление гида по завершённости импорта и концу сетки.
 *
 * Если прошлая загрузка не дочитана до конца (в базе осталась запись [EpgImportTracker]),
 * гид перекачивается сразу при запуске: покрытие каналов тут не годится — части каналов нет
 * в публичных гидах вообще. Иначе обновление ставится за [UpdateScheduler.GUIDE_LEAD_HOURS]
 * до последней передачи в базе. Само решение — в [GuideRefreshPolicy], здесь только сбор
 * данных и постановка задачи.
 */
@Singleton
class GuideRefreshPlanner @Inject constructor(
    private val epgStore: EpgStore,
    private val importTracker: EpgImportTracker,
    private val scheduler: UpdateScheduler,
) {

    /**
     * Пересчитывает срок обновления гида и ставит задачу.
     *
     * @param now текущий момент.
     * @return задержка до обновления в миллисекундах (0 — обновляем сразу).
     */
    suspend fun plan(now: Instant = Instant.now()): Long {
        val unfinished = runCatching { importTracker.unfinished() }.getOrDefault(emptyList())
        val delay = GuideRefreshPolicy.delayMillis(
            now = now,
            coverageEnd = runCatching { epgStore.coverageEnd() }.getOrNull(),
            hasUnfinishedImport = unfinished.isNotEmpty(),
        )
        if (delay == 0L) {
            android.util.Log.d(
                "SmotrishkaSync",
                "гид обновим сразу: незавершённых импортов ${unfinished.size}",
            )
        }
        scheduler.scheduleGuideRefresh(delay)
        return delay
    }
}
