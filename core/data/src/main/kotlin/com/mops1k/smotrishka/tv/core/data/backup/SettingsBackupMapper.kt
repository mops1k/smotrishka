package com.mops1k.smotrishka.tv.core.data.backup

import com.mops1k.smotrishka.tv.core.data.AspectMode
import com.mops1k.smotrishka.tv.core.data.AudioLanguage
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.core.model.PlaylistSource

/**
 * Преобразование моделей приложения в переносимый вид и обратно.
 *
 * Неизвестные значения перечислений не ломают импорт: берётся значение по умолчанию.
 */
object SettingsBackupMapper {

    private const val KIND_M3U = "m3u"
    private const val KIND_FILE = "file"
    private const val KIND_IEDEM = "iedem"
    private const val KIND_XTREAM = "xtream"

    /**
     * Источник для файла настроек.
     *
     * @param source активный источник;
     * @return плоское представление либо `null`, если источник не настроен.
     */
    fun toBackup(source: PlaylistSource?): SettingsBackup.Source? = when (source) {
        null -> null
        is PlaylistSource.M3uUrl -> SettingsBackup.Source(
            kind = KIND_M3U,
            url = source.url,
            name = source.name,
        )

        is PlaylistSource.File -> SettingsBackup.Source(
            kind = KIND_FILE,
            url = source.uri,
            name = source.name,
        )

        is PlaylistSource.IedemCode -> SettingsBackup.Source(
            kind = KIND_IEDEM,
            code = source.code,
        )

        is PlaylistSource.Xtream -> SettingsBackup.Source(
            kind = KIND_XTREAM,
            server = source.server,
            username = source.username,
            password = source.password,
        )
    }

    /**
     * Источник из файла настроек.
     *
     * @param backup плоское представление;
     * @return источник либо `null`, если данных не хватает.
     */
    fun toSource(backup: SettingsBackup.Source?): PlaylistSource? = when (backup?.kind) {
        null -> null

        KIND_M3U -> backup.url?.let { PlaylistSource.M3uUrl(url = it, name = backup.name) }

        KIND_FILE -> backup.url?.let { PlaylistSource.File(uri = it, name = backup.name) }

        KIND_IEDEM -> backup.code?.let { PlaylistSource.IedemCode(code = it) }

        KIND_XTREAM -> {
            val server = backup.server
            val username = backup.username
            if (server == null || username == null) {
                null
            } else {
                PlaylistSource.Xtream(
                    server = server,
                    username = username,
                    password = backup.password.orEmpty(),
                )
            }
        }

        else -> null
    }

    /** Качество видео по имени значения из файла. */
    fun toVideoQuality(name: String): VideoQuality =
        runCatching { VideoQuality.valueOf(name) }.getOrDefault(VideoQuality.AUTO)

    /** Язык звука по имени значения из файла. */
    fun toAudioLanguage(name: String): AudioLanguage =
        runCatching { AudioLanguage.valueOf(name) }.getOrDefault(AudioLanguage.RUSSIAN)

    /** Режим кадра по имени значения из файла. */
    fun toAspectMode(name: String): AspectMode =
        runCatching { AspectMode.valueOf(name) }.getOrDefault(AspectMode.FIT)
}
