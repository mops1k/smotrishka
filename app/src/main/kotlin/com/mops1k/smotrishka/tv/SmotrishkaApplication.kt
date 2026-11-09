package com.mops1k.smotrishka.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mops1k.smotrishka.tv.archive.ArchiveProbeRunner
import com.mops1k.smotrishka.tv.core.data.EpgGuideRepository
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.GuideRefreshPlanner
import com.mops1k.smotrishka.tv.core.data.UpdateScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Точка входа приложения: инициализирует граф зависимостей Hilt,
 * настраивает фабрику воркеров и ставит обновление данных.
 *
 * Обновление каталога и программы передач запускается при старте (разовая задача)
 * и далее повторяется раз в 6 часов фоновым планировщиком.
 */
@HiltAndroidApp
class SmotrishkaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var updateScheduler: UpdateScheduler

    @Inject
    lateinit var archiveProbe: ArchiveProbeRunner

    @Inject
    lateinit var playerSettings: PlayerSettingsStore

    @Inject
    lateinit var epgGuides: EpgGuideRepository

    @Inject
    lateinit var guideRefreshPlanner: GuideRefreshPlanner

    @Inject
    lateinit var updateRepository: com.mops1k.smotrishka.tv.update.UpdateRepository

    // В WorkManager 2.12 Configuration.Provider объявлен функцией, а не property.
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        updateScheduler.schedulePeriodic()
        // Гид обновляется заранее — за час до конца покрытия, а не по фиксированному интервалу.
        CoroutineScope(Dispatchers.IO).launch {
            // Пояс устройства учитываем один раз: провайдеры часто отдают сетку без зоны,
            // и без поправки все передачи уезжают на смещение пояса (замечание 09.11.2026).
            runCatching { playerSettings.applyAutoEpgShiftOnce() }
                .onFailure { android.util.Log.e("SmotrishkaSync", "epg shift init failed", it) }
        }
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { guideRefreshPlanner.plan() }
                .onSuccess { delay ->
                    android.util.Log.d("SmotrishkaSync", "гид обновится через $delay мс")
                }
                .onFailure { android.util.Log.e("SmotrishkaSync", "guide plan failed", it) }
        }
        // Стартовый набор гидов: без него фоновая программа передач не соберётся,
        // если у плейлиста нет своего адреса гида.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { epgGuides.ensureDefaults() }
                .onFailure { android.util.Log.e("SmotrishkaSync", "epg guides seed failed", it) }
        }
        // Если каталог загружен из файла, автосинхронизация по коду не должна его перезаписывать.
        CoroutineScope(Dispatchers.IO).launch {
            val fromFile = runCatching { playerSettings.fileSourceUri.first() }.getOrNull()
            if (fromFile == null) {
                updateScheduler.syncNow()
            } else {
                android.util.Log.d("SmotrishkaSync", "каталог из файла: автосинхронизация пропущена")
            }
        }
        // Проверка архива раз в сутки: значок в списке ставится по факту выдачи, а не по тегу.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { archiveProbe.runIfStale() }
                .onFailure { android.util.Log.e("SmotrishkaSync", "archive probe failed", it) }
        }
        // Проверка обновлений приложения: раз в сутки и только по Wi-Fi, если так настроено.
        // Сбои здесь не должны мешать старту — внутри всё в runCatching.
        runCatching { updateRepository.checkOnStartup() }
            .onFailure { android.util.Log.e("SmotrishkaUpdate", "startup update check failed", it) }
    }
}
