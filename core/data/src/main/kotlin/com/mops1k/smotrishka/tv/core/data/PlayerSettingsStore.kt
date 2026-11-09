package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId

/**
 * Настройки воспроизведения, рассчитанные на слабый интернет.
 *
 * - Буферизация вперёд — сколько секунд потока плеер держит в запасе.
 * - Качество — потолок разрешения, чтобы не тянуть лишнее.
 * - Повторы — сколько раз пробовать после обрыва, прежде чем показать ошибку.
 * - Окно программы передач — какой диапазон EPG хранить (меньше — быстрее обновление).
 * - Экономия трафика — не запускать превью и не грузить логотипы.
 *
 * @param dataStore хранилище настроек.
 */
class PlayerSettingsStore(private val dataStore: DataStore<Preferences>) {

    /** Сколько секунд вперёд держать в буфере. */
    val bufferSeconds: Flow<Int> = dataStore.data.map { preferences ->
        preferences[BUFFER_KEY] ?: DEFAULT_BUFFER_SECONDS
    }

    /**
     * Сколько секунд меню плеера (OSD) остаётся на экране без действий.
     *
     * Настраивается: на пульте кому-то нужно 4 секунды, чтобы быстро убрать меню, а кому-то
     * 30 — чтобы прочитать программу (просьба Александра 06.11.2026).
     */
    val osdHideSeconds: Flow<Int> = dataStore.data.map { preferences ->
        preferences[OSD_HIDE_KEY] ?: DEFAULT_OSD_HIDE_SECONDS
    }

    /**
     * Общее смещение гида, часы.
     *
     * У части провайдеров сетка отдаётся в чужом часовом поясе: тогда все передачи сдвинуты
     * одинаково. Сдвиг отдельного канала ([epgShifts]) важнее общего (решение Александра
     * 09.11.2026). Значение по умолчанию — ноль; пояс устройства учитывается один раз при
     * старте приложения ([applyAutoEpgShiftOnce]), чтобы не сдвигать сетку молча в тестах.
     */
    val globalEpgShift: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[GLOBAL_EPG_SHIFT_KEY] ?: 0).coerceIn(-MAX_EPG_SHIFT_HOURS, MAX_EPG_SHIFT_HOURS)
    }

    /**
     * Задаёт общее смещение гида.
     *
     * @param hours смещение в часах, допустимо −12…+12 (0 — без смещения).
     */
    suspend fun setGlobalEpgShift(hours: Int) {
        dataStore.edit { preferences ->
            preferences[GLOBAL_EPG_SHIFT_KEY] = hours.coerceIn(-MAX_EPG_SHIFT_HOURS, MAX_EPG_SHIFT_HOURS)
        }
    }

    /**
     * Один раз подставляет смещение по поясу устройства.
     *
     * Провайдеры часто отдают XMLTV без часового пояса, и время читается как UTC: без поправки
     * все передачи уезжают на смещение пояса (на планшете в Москве — на +3 часа). Поправка
     * ставится только пока пользователь не задал смещение сам и только один раз — иначе ручной
     * выбор затирался бы при каждом запуске.
     *
     * @param hours смещение по умолчанию, по умолчанию — минус пояс устройства.
     */
    suspend fun applyAutoEpgShiftOnce(hours: Int = autoEpgShiftHours()) {
        dataStore.edit { preferences ->
            if (preferences[EPG_SHIFT_AUTO_KEY] == true) return@edit
            preferences[EPG_SHIFT_AUTO_KEY] = true
            if (preferences[GLOBAL_EPG_SHIFT_KEY] == null) preferences[GLOBAL_EPG_SHIFT_KEY] = hours
        }
    }

    /** Смещение гида по умолчанию: минус смещение пояса устройства в часах. */
    private fun autoEpgShiftHours(): Int =
        -(ZoneId.systemDefault().rules.getOffset(Instant.now()).totalSeconds / 3600)

    /**
     * Сдвиг времени гида по каналам, в часах.
     *
     * У части каналов провайдер отдаёт сетку со смещением (например, время без зоны),
     * поэтому сдвиг задаётся отдельно для канала, а не глобально.
     */
    val epgShifts: Flow<Map<String, Int>> = dataStore.data.map { preferences ->
        preferences[EPG_SHIFTS_KEY].orEmpty()
            .mapNotNull { entry ->
                val parts = entry.split('=')
                if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
            }
            .filter { it.second != 0 }
            .toMap()
    }

    /**
     * Задаёт сдвиг гида для одного канала.
     *
     * @param channelId канал каталога;
     * @param hours сдвиг в часах, допустимо −12…+12 (0 — убрать сдвиг).
     */
    suspend fun setEpgShift(channelId: String, hours: Int) {
        val value = hours.coerceIn(-MAX_EPG_SHIFT_HOURS, MAX_EPG_SHIFT_HOURS)
        dataStore.edit { preferences ->
            val current = preferences[EPG_SHIFTS_KEY].orEmpty().toMutableSet()
            current.removeAll { it.substringBefore('=') == channelId }
            if (value != 0) current.add("$channelId=$value")
            preferences[EPG_SHIFTS_KEY] = current
        }
    }

    /** Потолок качества видео. */
    val videoQuality: Flow<VideoQuality> = dataStore.data.map { preferences ->
        preferences[QUALITY_KEY]?.let { name -> VideoQuality.entries.firstOrNull { it.name == name } }
            ?: VideoQuality.AUTO
    }

    /**
     * Потолок паузы между попытками восстановления потока, секунды.
     *
     * Плеер не сдаётся и повторяет попытки бесконечно; значение из [RECOVERY_DELAY_CHOICES]
     * задаёт, как часто это происходит в худшем случае, чтобы не забивать канал.
     */
    val recoveryDelaySeconds: Flow<Int> = dataStore.data.map { preferences ->
        preferences[RECOVERY_DELAY_KEY] ?: DEFAULT_RECOVERY_DELAY_SECONDS
    }

    /** Окно программы передач в часах (назад), вперёд — вдвое больше. */
    val epgWindowHours: Flow<Int> = dataStore.data.map { preferences ->
        preferences[EPG_WINDOW_KEY] ?: DEFAULT_EPG_WINDOW_HOURS
    }

    /** Экономить трафик: без автозапуска превью и без логотипов. */
    val dataSaver: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DATA_SAVER_KEY] ?: false
    }

    /**
     * Выбранный язык каналов, `null` — показывать все.
     *
     * Фильтр работает по `tvg-language` плейлиста (решение Александра 09.11.2026).
     */
    val channelLanguage: Flow<String?> = dataStore.data.map { preferences ->
        preferences[CHANNEL_LANGUAGE_KEY]?.takeIf(String::isNotBlank)
    }

    /**
     * Задаёт язык каналов для списков.
     *
     * @param language язык из плейлиста, `null` — все каналы.
     */
    suspend fun setChannelLanguage(language: String?) {
        dataStore.edit { preferences ->
            val value = language?.takeIf(String::isNotBlank)
            if (value == null) preferences.remove(CHANNEL_LANGUAGE_KEY) else preferences[CHANNEL_LANGUAGE_KEY] = value
        }
    }

    /** Показывать каналы без метки языка: иначе они скрываются вместе с чужими языками. */
    val showUnlabelledChannels: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_UNLABELLED_KEY] ?: true
    }

    /**
     * Определять язык канала по названию (`... HD ES`, `... US`).
     *
     * По умолчанию выключено: у взрослых каналов хвосты есть, но язык там не важен
     * (решение Александра 09.11.2026).
     */
    val languageFromName: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[LANGUAGE_FROM_NAME_KEY] ?: false
    }

    /**
     * Включает определение языка по названию канала.
     *
     * @param enabled разбирать хвост имени канала как язык.
     */
    suspend fun setLanguageFromName(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[LANGUAGE_FROM_NAME_KEY] = enabled }
    }

    /**
     * Включает показ каналов без метки языка.
     *
     * @param enabled показывать каналы, у которых `tvg-language` не задан.
     */
    suspend fun setShowUnlabelledChannels(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[SHOW_UNLABELLED_KEY] = enabled }
    }

    /**
     * Лента «Сейчас идёт» на домашнем экране Android TV.
     *
     * Выключена — канал из лаунчера удаляется и больше не публикуется
     * (требование Александра 06.11.2026: функционал должен отключаться).
     */
    val watchNextEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[WATCH_NEXT_ENABLED_KEY] ?: DEFAULT_WATCH_NEXT_ENABLED
    }

    /**
     * Сколько карточек показывать в ленте «Сейчас идёт».
     *
     * Значение — из [WATCH_NEXT_LIMIT_CHOICES]: чем больше карточек, тем больше места лента
     * занимает на домашнем экране приставки.
     */
    val watchNextLimit: Flow<Int> = dataStore.data.map { preferences ->
        preferences[WATCH_NEXT_LIMIT_KEY] ?: DEFAULT_WATCH_NEXT_LIMIT
    }

    /** Язык звуковой дорожки по умолчанию. */
    val audioLanguage: Flow<AudioLanguage> = dataStore.data.map { preferences ->
        preferences[AUDIO_LANGUAGE_KEY]
            ?.let { name -> AudioLanguage.entries.firstOrNull { it.name == name } }
            ?: AudioLanguage.RUSSIAN
    }

    /** Каналы, которым нужен собственный буфер: проблемы обнаружены автоматически. */
    val bufferedChannels: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[BUFFERED_CHANNELS_KEY].orEmpty()
    }

    /**
     * Последняя открытая категория: при запуске приложение возвращается в неё.
     *
     * `null` — не было сохранено, [ALL_CATEGORY] — категория «Все».
     */
    val lastCategory: Flow<String?> = dataStore.data.map { preferences ->
        preferences[LAST_CATEGORY_KEY]
    }

    /**
     * Сохраняет последнюю открытую категорию.
     *
     * @param categoryId ключ категории, `null` — «Все».
     */
    suspend fun setLastCategory(categoryId: String?) {
        dataStore.edit { preferences ->
            preferences[LAST_CATEGORY_KEY] = categoryId ?: ALL_CATEGORY
        }
    }

    /** Каналы с нестабильным потоком у провайдера (пропуски сегментов). */
    val unstableChannels: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[UNSTABLE_CHANNELS_KEY].orEmpty()
    }

    /** Каналы с проверенным архивом: значок ставится по факту, а не по тегу провайдера. */
    val archiveVerified: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[ARCHIVE_VERIFIED_KEY].orEmpty()
    }

    /** URI файла плейлиста, если каталог загружен из файла: тогда автосинхронизация не бежит. */
    val fileSourceUri: Flow<String?> = dataStore.data.map { preferences ->
        preferences[FILE_SOURCE_KEY]
    }

    /** Запоминает файл как источник каталога (`null` — вернуть источник провайдера). */
    suspend fun setFileSource(uri: String?) {
        dataStore.edit { preferences ->
            if (uri == null) preferences.remove(FILE_SOURCE_KEY) else preferences[FILE_SOURCE_KEY] = uri
        }
    }

    /** Когда архив проверяли последний раз, миллисекунды эпохи. */
    val archiveVerifiedAt: Flow<Long> = dataStore.data.map { preferences ->
        preferences[ARCHIVE_VERIFIED_AT_KEY]?.toLongOrNull() ?: 0L
    }

    /** Сохраняет результат проверки архива. */
    suspend fun saveArchiveVerified(channels: Set<String>, atMillis: Long) {
        dataStore.edit { preferences ->
            preferences[ARCHIVE_VERIFIED_KEY] = channels
            preferences[ARCHIVE_VERIFIED_AT_KEY] = atMillis.toString()
        }
    }

    /** Режим отображения кадра. */
    val aspectMode: Flow<AspectMode> = dataStore.data.map { preferences ->
        preferences[ASPECT_MODE_KEY]
            ?.let { name -> AspectMode.entries.firstOrNull { it.name == name } }
            ?: AspectMode.FIT
    }

    /** Режим эфира: прямой поток провайдера или собственный буфер (локальный HLS). */
    val directStream: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DIRECT_STREAM_KEY] ?: true
    }

    /** Глубина архива в часах: сколько назад доступны прошедшие передачи. */
    val archiveDepthHours: Flow<Int> = dataStore.data.map { preferences ->
        preferences[ARCHIVE_DEPTH_KEY] ?: DEFAULT_ARCHIVE_DEPTH_HOURS
    }

    /**
     * Сохраняет размер буфера.
     *
     * @param seconds значение из [BUFFER_CHOICES] (иначе приводится к допустимому диапазону).
     */
    suspend fun setBufferSeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[BUFFER_KEY] = seconds.coerceIn(MIN_BUFFER_SECONDS, MAX_BUFFER_SECONDS)
        }
    }

    /**
     * Сохраняет время показа меню плеера.
     *
     * @param seconds значение из [OSD_HIDE_CHOICES]; иначе берётся ближайшее допустимое.
     */
    suspend fun setOsdHideSeconds(seconds: Int) {
        val value = if (seconds in OSD_HIDE_CHOICES) {
            seconds
        } else {
            OSD_HIDE_CHOICES.minBy { kotlin.math.abs(it - seconds) }
        }
        dataStore.edit { preferences -> preferences[OSD_HIDE_KEY] = value }
    }

    /**
     * Сохраняет потолок качества.
     *
     * @param quality выбранное качество.
     */
    suspend fun setVideoQuality(quality: VideoQuality) {
        dataStore.edit { preferences -> preferences[QUALITY_KEY] = quality.name }
    }

    /**
     * Сохраняет потолок паузы между попытками восстановления потока.
     *
     * @param seconds значение из [RECOVERY_DELAY_CHOICES] (иначе приводится к краям списка).
     */
    suspend fun setRecoveryDelaySeconds(seconds: Int) {
        dataStore.edit { preferences ->
            preferences[RECOVERY_DELAY_KEY] = seconds.coerceIn(
                RECOVERY_DELAY_CHOICES.first(),
                RECOVERY_DELAY_CHOICES.last(),
            )
        }
    }

    /**
     * Сохраняет окно программы передач.
     *
     * @param hours значение из [EPG_WINDOW_CHOICES] (иначе приводится к краям списка).
     */
    suspend fun setEpgWindowHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[EPG_WINDOW_KEY] = hours.coerceIn(
                EPG_WINDOW_CHOICES.first(),
                EPG_WINDOW_CHOICES.last(),
            )
        }
    }

    /**
     * Включает или выключает экономию трафика.
     *
     * @param enabled экономить ли трафик.
     */
    suspend fun setDataSaver(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[DATA_SAVER_KEY] = enabled }
    }

    /**
     * Включает или выключает ленту «Сейчас идёт» на домашнем экране.
     *
     * @param enabled показывать ли канал приложения в лаунчере.
     */
    suspend fun setWatchNextEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[WATCH_NEXT_ENABLED_KEY] = enabled }
    }

    /**
     * Сохраняет, сколько карточек показывать в ленте «Сейчас идёт».
     *
     * @param limit значение из [WATCH_NEXT_LIMIT_CHOICES]; вне диапазона приводится к краю.
     */
    suspend fun setWatchNextLimit(limit: Int) {
        dataStore.edit { preferences ->
            preferences[WATCH_NEXT_LIMIT_KEY] = limit.coerceIn(
                WATCH_NEXT_LIMIT_CHOICES.first(),
                WATCH_NEXT_LIMIT_CHOICES.last(),
            )
        }
    }

    /**
     * Сохраняет язык звука по умолчанию.
     *
     * @param language выбранный язык.
     */
    suspend fun setAudioLanguage(language: AudioLanguage) {
        dataStore.edit { preferences -> preferences[AUDIO_LANGUAGE_KEY] = language.name }
    }

    /**
     * Помечает канал как нестабильный: провайдер отдаёт поток с пропусками.
     *
     * @param channelId ключ канала.
     */
    suspend fun setUnstableChannel(channelId: String) {
        dataStore.edit { preferences ->
            preferences[UNSTABLE_CHANNELS_KEY] =
                preferences[UNSTABLE_CHANNELS_KEY].orEmpty() + channelId
        }
    }

    /**
     * Запоминает, что каналу нужен собственный буфер.
     *
     * @param channelId ключ канала.
     */
    suspend fun setBufferedChannel(channelId: String) {
        dataStore.edit { preferences ->
            preferences[BUFFERED_CHANNELS_KEY] = preferences[BUFFERED_CHANNELS_KEY].orEmpty() + channelId
        }
    }

    /**
     * Сохраняет режим кадра.
     *
     * @param mode выбранный режим.
     */
    suspend fun setAspectMode(mode: AspectMode) {
        dataStore.edit { preferences -> preferences[ASPECT_MODE_KEY] = mode.name }
    }

    /**
     * Сохраняет режим эфира.
     *
     * @param direct `true` — прямой поток провайдера, `false` — собственный буфер.
     */
    suspend fun setDirectStream(direct: Boolean) {
        dataStore.edit { preferences -> preferences[DIRECT_STREAM_KEY] = direct }
    }

    /**
     * Сохраняет глубину архива.
     *
     * @param hours значение из [ARCHIVE_DEPTH_CHOICES].
     */
    suspend fun setArchiveDepthHours(hours: Int) {
        dataStore.edit { preferences ->
            preferences[ARCHIVE_DEPTH_KEY] = hours.coerceIn(
                ARCHIVE_DEPTH_CHOICES.first(),
                ARCHIVE_DEPTH_CHOICES.last(),
            )
        }
    }

    companion object {
        const val DEFAULT_BUFFER_SECONDS = 5

        /** Выбор «Основные» в фильтре языка: показывать только каналы без метки языка. */
        const val UNLABELLED_LANGUAGE = "unlabelled"

        /** Варианты, которые предлагаются в настройках. */
        val BUFFER_CHOICES = listOf(2, 5, 10, 20)

        const val MIN_BUFFER_SECONDS = 1
        const val MAX_BUFFER_SECONDS = 60

        /** Потолок паузы между попытками восстановления по умолчанию: раз в 10 секунд. */
        const val DEFAULT_RECOVERY_DELAY_SECONDS = 10

        /** Варианты потолка паузы между попытками восстановления, секунды. */
        val RECOVERY_DELAY_CHOICES = listOf(5, 10, 15)

        const val DEFAULT_EPG_WINDOW_HOURS = 24

        /** Сколько секунд меню плеера показывается по умолчанию. */
        const val DEFAULT_OSD_HIDE_SECONDS = 4

        /** Варианты времени показа меню плеера: от быстрого до «прочитать программу». */
        val OSD_HIDE_CHOICES = listOf(4, 8, 15, 30)

        /** Предел ручного сдвига гида для канала, часы. */
        const val MAX_EPG_SHIFT_HOURS = 12

        /**
         * Варианты окна программы передач (часы назад).
         *
         * Ряд доходит до семи суток с шагом в сутки: провайдеры дают архив не меньше трёх
         * суток, а окно должно уметь накрыть всю выбранную глубину.
         */
        val EPG_WINDOW_CHOICES = listOf(12, 24, 48, 72, 96, 120, 144, 168)

        /** Глубина архива по умолчанию: у iedem архив до 72 часов. */
        const val DEFAULT_ARCHIVE_DEPTH_HOURS = 72

        /** Лента «Сейчас идёт» включена по умолчанию: её видно на домашнем экране приставки. */
        const val DEFAULT_WATCH_NEXT_ENABLED = true

        /** Сколько карточек в ленте по умолчанию. */
        const val DEFAULT_WATCH_NEXT_LIMIT = 10

        /** Варианты количества карточек ленты, которые предлагаются в настройках. */
        val WATCH_NEXT_LIMIT_CHOICES = listOf(5, 10, 15, 20)

        /** Варианты глубины архива: от суток до семи суток, шаг в сутки. */
        val ARCHIVE_DEPTH_CHOICES = listOf(24, 48, 72, 96, 120, 144, 168)

        private val BUFFER_KEY = intPreferencesKey("player_buffer_seconds")
        private val OSD_HIDE_KEY = intPreferencesKey("player_osd_hide_seconds")
        private val EPG_SHIFTS_KEY = stringSetPreferencesKey("epg_shifts_by_channel")
        private val GLOBAL_EPG_SHIFT_KEY = intPreferencesKey("epg_global_shift_hours")

        /** Признак, что авто-смещение по поясу уже применялось: второй раз не подставляем. */
        private val EPG_SHIFT_AUTO_KEY = booleanPreferencesKey("epg_shift_auto_applied")
        private val QUALITY_KEY = stringPreferencesKey("player_video_quality")
        private val RECOVERY_DELAY_KEY = intPreferencesKey("player_recovery_max_delay_seconds")
        private val EPG_WINDOW_KEY = intPreferencesKey("epg_window_hours")
        private val DATA_SAVER_KEY = booleanPreferencesKey("data_saver")

        private val CHANNEL_LANGUAGE_KEY = stringPreferencesKey("channel_language")

        private val SHOW_UNLABELLED_KEY = booleanPreferencesKey("show_unlabelled_channels")

        private val LANGUAGE_FROM_NAME_KEY = booleanPreferencesKey("language_from_name")
        private val WATCH_NEXT_ENABLED_KEY = booleanPreferencesKey("watch_next_enabled")
        private val WATCH_NEXT_LIMIT_KEY = intPreferencesKey("watch_next_limit")
        private val AUDIO_LANGUAGE_KEY = stringPreferencesKey("player_audio_language")
        private val ARCHIVE_DEPTH_KEY = intPreferencesKey("archive_depth_hours")
        private val DIRECT_STREAM_KEY = booleanPreferencesKey("direct_stream")
        private val ASPECT_MODE_KEY = stringPreferencesKey("aspect_mode")
        private val BUFFERED_CHANNELS_KEY = stringSetPreferencesKey("buffered_channels_v2")
        private val UNSTABLE_CHANNELS_KEY = stringSetPreferencesKey("unstable_channels")
        private val ARCHIVE_VERIFIED_KEY = stringSetPreferencesKey("archive_verified")
        private val ARCHIVE_VERIFIED_AT_KEY = stringPreferencesKey("archive_verified_at")
        private val FILE_SOURCE_KEY = stringPreferencesKey("file_source_uri")
        private val LAST_CATEGORY_KEY = stringPreferencesKey("last_category")

        /** Значение для категории «Все»: `null` в DataStore не сохранить. */
        const val ALL_CATEGORY = "\u0000all"
    }
}
