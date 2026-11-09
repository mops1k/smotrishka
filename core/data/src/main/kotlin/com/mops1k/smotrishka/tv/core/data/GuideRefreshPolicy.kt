package com.mops1k.smotrishka.tv.core.data

import java.time.Duration
import java.time.Instant

/**
 * Решение о сроке обновления программы передач.
 *
 * Главный признак — завершённость импорта: пока в базе есть незавершённая загрузка
 * ([EpgImportTracker]), гид обновляется сразу и целиком. Иначе срок считается по концу
 * покрытия — за `UpdateScheduler.GUIDE_LEAD_HOURS` до последней передачи
 * (решение Александра 10.11.2026: неполное покрытие каналов — не повод качать гид повторно,
 * потому что части каналов нет в публичных гидах вообще).
 */
object GuideRefreshPolicy {

    /**
     * Задержка до обновления гида.
     *
     * @param now текущий момент;
     * @param coverageEnd конец программы в базе; `null` — программы нет;
     * @param hasUnfinishedImport осталась ли незавершённая загрузка гида.
     * @return задержка в миллисекундах, 0 — обновлять сразу.
     */
    fun delayMillis(
        now: Instant,
        coverageEnd: Instant?,
        hasUnfinishedImport: Boolean,
    ): Long {
        if (hasUnfinishedImport) return 0L
        val end = coverageEnd ?: return 0L
        val target = end.minusSeconds(UpdateScheduler.GUIDE_LEAD_HOURS * 3600)
        return if (target.isAfter(now)) Duration.between(now, target).toMillis() else 0L
    }
}
