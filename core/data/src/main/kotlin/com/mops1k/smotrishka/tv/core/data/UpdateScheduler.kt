package com.mops1k.smotrishka.tv.core.data

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Планирует обновление каталога и программы передач.
 *
 * Периодическое обновление — раз в 6 часов (решение интервью), только при сети.
 * Дополнительно разовый запуск при старте приложения: так данные свежие сразу
 * после запуска, не дожидаясь периода.
 *
 * @param workManager менеджер фоновых задач.
 */
class UpdateScheduler(private val workManager: WorkManager) {

    /** Ставит периодическое обновление (существующее не пересоздаётся). */
    fun schedulePeriodic() {
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CatalogSyncWorker>(REFRESH_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .build(),
        )
    }

    /**
     * Планирует обновление гида к концу покрытия.
     *
     * Гид обновляется за час до последней передачи в базе: так он приходит заранее и без
     * пробела в программе. Существующая задача заменяется — срок всегда считается заново.
     *
     * @param delayMillis через сколько запустить обновление (0 — сразу).
     */
    fun scheduleGuideRefresh(delayMillis: Long) {
        workManager.enqueueUniqueWork(
            GUIDE_REFRESH_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CatalogSyncWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setConstraints(networkConstraints())
                .build(),
        )
    }

    /** Запускает обновление один раз (например, при открытии приложения). */
    fun syncNow() {
        workManager.enqueueUniqueWork(
            ONE_SHOT_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<CatalogSyncWorker>()
                .setConstraints(networkConstraints())
                .build(),
        )
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    companion object {
        const val PERIODIC_NAME = "catalog-sync-periodic"
        const val ONE_SHOT_NAME = "catalog-sync-now"
        const val GUIDE_REFRESH_NAME = "guide-refresh"
        const val REFRESH_INTERVAL_HOURS = 6L

        /** За сколько до конца покрытия обновлять гид: час запаса на загрузку и разбор. */
        const val GUIDE_LEAD_HOURS = 1L
    }
}
