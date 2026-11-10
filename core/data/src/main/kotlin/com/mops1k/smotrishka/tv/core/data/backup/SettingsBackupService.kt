package com.mops1k.smotrishka.tv.core.data.backup

import com.mops1k.smotrishka.tv.core.data.EpgGuideRepository
import com.mops1k.smotrishka.tv.core.data.FavoritesStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.SourceRepository
import com.mops1k.smotrishka.tv.core.data.update.UpdateSettingsStore
import kotlinx.coroutines.flow.first

/**
 * Экспорт и импорт настроек приложения: источник, плеер, гиды, избранное, медиатека и настройки
 * обновления.
 *
 * Используется в сценарии переустановки (подпись APK не совпала) и для переноса настроек
 * на другое устройство. Любая ошибка возвращается результатом, а не исключением.
 *
 * @param sourceRepository активный источник;
 * @param playerSettingsStore настройки плеера;
 * @param epgGuideRepository гиды XMLTV;
 * @param favoritesStore избранные каналы;
 * @param vodBackup секция медиатеки: порт, который реализует модуль данных медиатеки;
 * @param updateSettingsStore настройки обновления;
 * @param codec сериализация файла;
 * @param clock источник времени.
 */
class SettingsBackupService(
    private val sourceRepository: SourceRepository,
    private val playerSettingsStore: PlayerSettingsStore,
    private val epgGuideRepository: EpgGuideRepository,
    private val favoritesStore: FavoritesStore,
    private val updateSettingsStore: UpdateSettingsStore,
    private val vodBackup: VodBackupPort = VodBackupPort.None,
    private val codec: SettingsBackupCodec = SettingsBackupCodec(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /** Собирает настройки и сериализует их в JSON. */
    suspend fun createJson(): SettingsBackupResult = runCatching {
        SettingsBackupResult.Success(codec.encode(collect()))
    }.getOrElse { error ->
        SettingsBackupResult.Failure(error.message ?: "Не удалось сохранить настройки")
    }

    /**
     * Применяет настройки из JSON: полная замена источника, гидов, избранного и параметров.
     *
     * @param json содержимое файла настроек.
     */
    suspend fun importJson(json: String): SettingsImportResult {
        val backup = when (val decoded = codec.decode(json)) {
            is SettingsBackupReadResult.Failure -> return SettingsImportResult.Failure(decoded.reason)
            is SettingsBackupReadResult.Success -> decoded.backup
        }

        return runCatching {
            apply(backup)
            SettingsImportResult.Success
        }.getOrElse { error ->
            SettingsImportResult.Failure(error.message ?: "Не удалось применить настройки")
        }
    }

    private suspend fun collect(): SettingsBackup = SettingsBackup(
        exportedAtMillis = clock(),
        source = SettingsBackupMapper.toBackup(sourceRepository.active()),
        player = SettingsBackup.Player(
            bufferSeconds = playerSettingsStore.bufferSeconds.first(),
            osdHideSeconds = playerSettingsStore.osdHideSeconds.first(),
            videoQuality = playerSettingsStore.videoQuality.first().name,
            recoveryDelaySeconds = playerSettingsStore.recoveryDelaySeconds.first(),
            audioLanguage = playerSettingsStore.audioLanguage.first().name,
            dataSaver = playerSettingsStore.dataSaver.first(),
            watchNextEnabled = playerSettingsStore.watchNextEnabled.first(),
            watchNextLimit = playerSettingsStore.watchNextLimit.first(),
            epgWindowHours = playerSettingsStore.epgWindowHours.first(),
            archiveDepthHours = playerSettingsStore.archiveDepthHours.first(),
            aspectMode = playerSettingsStore.aspectMode.first().name,
            directStream = playerSettingsStore.directStream.first(),
        ),
        guides = epgGuideRepository.guides().map { guide ->
            SettingsBackup.Guide(url = guide.url, name = guide.name)
        },
        favorites = favoritesStore.channels.first().toList(),
        vod = vodBackup.read(),
        update = updateSettingsStore.settings.first().let { settings ->
            SettingsBackup.Update(
                includePrereleases = settings.includePrereleases,
                wifiOnly = settings.wifiOnly,
            )
        },
    )

    private suspend fun apply(backup: SettingsBackup) {
        SettingsBackupMapper.toSource(backup.source)?.let { source ->
            val name = backup.source?.name?.takeIf { it.isNotBlank() } ?: RESTORED_SOURCE_NAME
            sourceRepository.save(source, name)
        }

        val player = backup.player
        playerSettingsStore.setBufferSeconds(player.bufferSeconds)
        playerSettingsStore.setOsdHideSeconds(player.osdHideSeconds)
        playerSettingsStore.setVideoQuality(SettingsBackupMapper.toVideoQuality(player.videoQuality))
        playerSettingsStore.setRecoveryDelaySeconds(player.recoveryDelaySeconds)
        playerSettingsStore.setAudioLanguage(SettingsBackupMapper.toAudioLanguage(player.audioLanguage))
        playerSettingsStore.setDataSaver(player.dataSaver)
        playerSettingsStore.setWatchNextEnabled(player.watchNextEnabled)
        playerSettingsStore.setWatchNextLimit(player.watchNextLimit)
        playerSettingsStore.setEpgWindowHours(player.epgWindowHours)
        playerSettingsStore.setArchiveDepthHours(player.archiveDepthHours)
        playerSettingsStore.setAspectMode(SettingsBackupMapper.toAspectMode(player.aspectMode))
        playerSettingsStore.setDirectStream(player.directStream)

        // Гиды заменяются целиком: иначе после импорта рядом с новыми остались бы старые.
        epgGuideRepository.guides().forEach { guide -> epgGuideRepository.remove(guide.id) }
        backup.guides.forEach { guide -> epgGuideRepository.add(guide.url, guide.name) }

        favoritesStore.setChannels(backup.favorites.toSet())

        backup.vod?.let { vod -> vodBackup.restore(vod) }

        updateSettingsStore.setIncludePrereleases(backup.update.includePrereleases)
        updateSettingsStore.setWifiOnly(backup.update.wifiOnly)
    }

    private companion object {
        const val RESTORED_SOURCE_NAME = "Восстановленный источник"
    }
}
