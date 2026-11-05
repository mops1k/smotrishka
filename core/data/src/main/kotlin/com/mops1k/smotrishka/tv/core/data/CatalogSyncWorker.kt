package com.mops1k.smotrishka.tv.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Фоновая синхронизация: источник → каталог → программа передач.
 *
 * Успешный и частичный результат считаются успехом (данные обновились или каталог
 * остался рабочим), а полный сбой повторяется несколько раз с экспоненциальной паузой
 * силами WorkManager, чтобы не бить по серверу провайдера.
 *
 * @param sourceRepository активный источник;
 * @param updater обновление данных;
 * @param statusStore последний результат для экрана настроек.
 */
@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val sourceRepository: SourceRepository,
    private val updater: CatalogUpdater,
    private val statusStore: SyncStatusStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val source = sourceRepository.active() ?: return Result.success()

        return when (val result = updater.update(source)) {
            is UpdateResult.Success -> {
                statusStore.save(result, System.currentTimeMillis())
                Result.success()
            }

            is UpdateResult.Partial -> {
                statusStore.save(result, System.currentTimeMillis())
                Result.success()
            }

            is UpdateResult.Failure -> {
                statusStore.save(result, System.currentTimeMillis())
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
