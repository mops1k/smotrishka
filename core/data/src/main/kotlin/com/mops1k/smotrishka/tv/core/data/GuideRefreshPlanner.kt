package com.mops1k.smotrishka.tv.core.data

import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Планирует обновление гида по фактическому концу покрытия.
 *
 * Расписание привязано не к «прошло N часов с обновления», а к последней передаче в базе:
 * гид обновляется за [UpdateScheduler.GUIDE_LEAD_HOURS] до её окончания. Поэтому покрытие
 * зависит от глубины программы, которую отдаёт источник и настройки, и пробела не возникает.
 */
@Singleton
class GuideRefreshPlanner @Inject constructor(
    private val epgStore: EpgStore,
    private val scheduler: UpdateScheduler,
) {

    /**
     * Пересчитывает срок обновления гида и ставит задачу.
     *
     * @param now текущий момент.
     * @return задержка до обновления в миллисекундах (0 — обновляем сразу).
     */
    suspend fun plan(now: Instant = Instant.now()): Long {
        val coverageEnd = epgStore.coverageEnd()
        val delay = coverageEnd?.let { end ->
            val target = end.minusSeconds(UpdateScheduler.GUIDE_LEAD_HOURS * 3600)
            if (target.isAfter(now)) Duration.between(now, target).toMillis() else 0L
        } ?: 0L
        scheduler.scheduleGuideRefresh(delay)
        return delay
    }
}
