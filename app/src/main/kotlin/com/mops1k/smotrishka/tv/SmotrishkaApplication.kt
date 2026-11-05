package com.mops1k.smotrishka.tv

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mops1k.smotrishka.tv.archive.ArchiveProbeRunner
import com.mops1k.smotrishka.tv.core.data.EpgGuideRepository
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
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

    // В WorkManager 2.12 Configuration.Provider объявлен функцией, а не property.
    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        updateScheduler.schedulePeriodic()
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
    }
}
