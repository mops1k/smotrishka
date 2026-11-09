package com.mops1k.smotrishka.tv.core.data.backup

import com.mops1k.smotrishka.tv.core.data.AspectMode
import com.mops1k.smotrishka.tv.core.data.AudioLanguage
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.VideoQuality

/**
 * Переносимые настройки приложения: то, что жалко потерять при переустановке.
 *
 * Файл содержит источник в открытом виде (ссылку, код или логин-пароль): после удаления
 * приложения ключ Android Keystore исчезает, и зашифровать секреты нечем.
 *
 * @param schema версия схемы файла;
 * @param exportedAtMillis когда файл сохранён;
 * @param source активный источник каналов;
 * @param player настройки плеера;
 * @param guides гиды XMLTV;
 * @param favorites избранные каналы;
 * @param update настройки обновления приложения.
 */
data class SettingsBackup(
    val schema: Int = SCHEMA_VERSION,
    val exportedAtMillis: Long = 0L,
    val source: Source? = null,
    val player: Player = Player(),
    val guides: List<Guide> = emptyList(),
    val favorites: List<String> = emptyList(),
    val update: Update = Update(),
) {

    /**
     * Источник каналов в плоском виде.
     *
     * @param kind тип: `m3u`, `file`, `iedem` или `xtream`;
     * @param url адрес плейлиста или файла;
     * @param name имя источника;
     * @param code короткий код провайдера;
     * @param server сервер Xtream;
     * @param username логин Xtream;
     * @param password пароль Xtream.
     */
    data class Source(
        val kind: String,
        val url: String? = null,
        val name: String? = null,
        val code: String? = null,
        val server: String? = null,
        val username: String? = null,
        val password: String? = null,
    )

    /**
     * Настройки плеера и программы передач.
     *
     * @param bufferSeconds буфер вперёд;
     * @param osdHideSeconds сколько секунд меню плеера видно без действий;
     * @param videoQuality потолок качества;
     * @param recoveryDelaySeconds потолок паузы между попытками восстановления потока;
     * @param audioLanguage язык звука;
     * @param dataSaver экономия трафика;
     * @param watchNextEnabled лента «Сейчас идёт»;
     * @param watchNextLimit сколько карточек в ленте;
     * @param epgWindowHours окно программы передач;
     * @param archiveDepthHours глубина архива;
     * @param aspectMode режим кадра;
     * @param directStream прямой поток или собственный буфер.
     */
    data class Player(
        val bufferSeconds: Int = PlayerSettingsStore.DEFAULT_BUFFER_SECONDS,
        val osdHideSeconds: Int = PlayerSettingsStore.DEFAULT_OSD_HIDE_SECONDS,
        val videoQuality: String = VideoQuality.AUTO.name,
        val recoveryDelaySeconds: Int = PlayerSettingsStore.DEFAULT_RECOVERY_DELAY_SECONDS,
        val audioLanguage: String = AudioLanguage.RUSSIAN.name,
        val dataSaver: Boolean = false,
        val watchNextEnabled: Boolean = PlayerSettingsStore.DEFAULT_WATCH_NEXT_ENABLED,
        val watchNextLimit: Int = PlayerSettingsStore.DEFAULT_WATCH_NEXT_LIMIT,
        val epgWindowHours: Int = PlayerSettingsStore.DEFAULT_EPG_WINDOW_HOURS,
        val archiveDepthHours: Int = PlayerSettingsStore.DEFAULT_ARCHIVE_DEPTH_HOURS,
        val aspectMode: String = AspectMode.FIT.name,
        val directStream: Boolean = true,
    )

    /**
     * Гид XMLTV.
     *
     * @param url адрес гида;
     * @param name имя для настроек.
     */
    data class Guide(val url: String, val name: String = "")

    /**
     * Настройки обновления приложения.
     *
     * @param includePrereleases предлагать пререлизы;
     * @param wifiOnly проверять и скачивать автоматически только по Wi-Fi.
     */
    data class Update(
        val includePrereleases: Boolean = false,
        val wifiOnly: Boolean = true,
    )

    companion object {
        /** Текущая версия схемы файла. */
        const val SCHEMA_VERSION = 1

        /** Префикс имени файла настроек. */
        const val FILE_PREFIX = "smotrishka-settings"
    }
}
