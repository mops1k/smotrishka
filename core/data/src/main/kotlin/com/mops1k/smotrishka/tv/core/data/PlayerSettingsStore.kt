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

    /** Потолок качества видео. */
    val videoQuality: Flow<VideoQuality> = dataStore.data.map { preferences ->
        preferences[QUALITY_KEY]?.let { name -> VideoQuality.entries.firstOrNull { it.name == name } }
            ?: VideoQuality.AUTO
    }

    /** Сколько раз повторять загрузку после обрыва. */
    val retryAttempts: Flow<Int> = dataStore.data.map { preferences ->
        preferences[RETRY_KEY] ?: DEFAULT_RETRY_ATTEMPTS
    }

    /** Окно программы передач в часах (назад), вперёд — вдвое больше. */
    val epgWindowHours: Flow<Int> = dataStore.data.map { preferences ->
        preferences[EPG_WINDOW_KEY] ?: DEFAULT_EPG_WINDOW_HOURS
    }

    /** Экономить трафик: без автозапуска превью и без логотипов. */
    val dataSaver: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[DATA_SAVER_KEY] ?: false
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
     * Сохраняет потолок качества.
     *
     * @param quality выбранное качество.
     */
    suspend fun setVideoQuality(quality: VideoQuality) {
        dataStore.edit { preferences -> preferences[QUALITY_KEY] = quality.name }
    }

    /**
     * Сохраняет число повторов.
     *
     * @param attempts значение из [RETRY_CHOICES].
     */
    suspend fun setRetryAttempts(attempts: Int) {
        dataStore.edit { preferences -> preferences[RETRY_KEY] = attempts.coerceIn(1, MAX_RETRY_ATTEMPTS) }
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

        /** Варианты, которые предлагаются в настройках. */
        val BUFFER_CHOICES = listOf(2, 5, 10, 20)

        const val MIN_BUFFER_SECONDS = 1
        const val MAX_BUFFER_SECONDS = 60

        const val DEFAULT_RETRY_ATTEMPTS = 5

        /** Варианты числа повторов. */
        val RETRY_CHOICES = listOf(3, 5, 8)

        const val MAX_RETRY_ATTEMPTS = 10

        const val DEFAULT_EPG_WINDOW_HOURS = 24

        /**
         * Варианты окна программы передач (часы назад).
         *
         * Ряд доходит до семи суток с шагом в сутки: провайдеры дают архив не меньше трёх
         * суток, а окно должно уметь накрыть всю выбранную глубину.
         */
        val EPG_WINDOW_CHOICES = listOf(12, 24, 48, 72, 96, 120, 144, 168)

        /** Глубина архива по умолчанию: у iedem архив до 72 часов. */
        const val DEFAULT_ARCHIVE_DEPTH_HOURS = 72

        /** Варианты глубины архива: от суток до семи суток, шаг в сутки. */
        val ARCHIVE_DEPTH_CHOICES = listOf(24, 48, 72, 96, 120, 144, 168)

        private val BUFFER_KEY = intPreferencesKey("player_buffer_seconds")
        private val QUALITY_KEY = stringPreferencesKey("player_video_quality")
        private val RETRY_KEY = intPreferencesKey("player_retry_attempts")
        private val EPG_WINDOW_KEY = intPreferencesKey("epg_window_hours")
        private val DATA_SAVER_KEY = booleanPreferencesKey("data_saver")
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
