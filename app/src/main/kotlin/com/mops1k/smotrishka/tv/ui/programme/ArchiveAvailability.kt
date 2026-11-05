package com.mops1k.smotrishka.tv.ui.programme

import java.time.Duration
import java.time.Instant

/**
 * Доступность передачи в архиве канала.
 *
 * Глубина архива настраивается (24/48/72 ч): передача, закончившаяся раньше этого срока,
 * из архива уже недоступна, даже если она есть в списке программы.
 */
object ArchiveAvailability {

    /**
     * Есть ли передача в архиве.
     *
     * @param end конец передачи;
     * @param now текущий момент;
     * @param depthHours глубина архива в часах, 0 — архива нет.
     */
    fun available(end: Instant, now: Instant, depthHours: Int): Boolean {
        if (depthHours <= 0) return false
        val age = Duration.between(end, now)
        return age <= Duration.ofHours(depthHours.toLong())
    }
}
