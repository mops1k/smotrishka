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
        const val REFRESH_INTERVAL_HOURS = 6L
    }
}
