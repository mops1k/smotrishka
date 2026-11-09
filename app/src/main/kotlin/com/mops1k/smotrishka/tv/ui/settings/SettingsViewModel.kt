package com.mops1k.smotrishka.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.data.AudioLanguage
import com.mops1k.smotrishka.tv.core.data.AspectMode
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.CatalogUpdater
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.playlist.M3uParser
import com.mops1k.smotrishka.tv.core.data.EpgGuideCheck
import com.mops1k.smotrishka.tv.core.data.EpgGuideRepository
import com.mops1k.smotrishka.tv.core.data.EpgGuideValidator
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.SourceRepository
import com.mops1k.smotrishka.tv.core.data.SyncStatusStore
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextSync
import com.mops1k.smotrishka.tv.core.model.EpgGuide
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

/**
 * Экран настроек: активный источник, ручное обновление данных и настройки плеера.
 *
 * Секреты не показываются: у кода провайдера видна только маска, у Xtream — сервер и логин.
 * При обновлении каталог и программа передач очищаются целиком: каналы и передачи прежнего
 * плейлиста не должны остаться рядом с новыми.
 *
 * @param sourceRepository хранение активного источника;
 * @param playerSettingsStore настройки плеера (буферизация);
 * @param catalogUpdater обновление каталога и программы передач;
 * @param epgStore хранилище программы передач (очищается перед обновлением);
 * @param syncStatusStore последний результат обновления;
 * @param epgGuideRepository хранилище пользовательских гидов;
 * @param epgGuideValidator проверка гида перед сохранением.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val playerSettingsStore: PlayerSettingsStore,
    private val catalogUpdater: CatalogUpdater,
    private val epgStore: EpgStore,
    private val syncStatusStore: SyncStatusStore,
    private val catalogStore: CatalogStore,
    private val parser: M3uParser,
    private val playlistImporter: PlaylistImporter,
    private val epgGuideRepository: EpgGuideRepository,
    private val epgGuideValidator: EpgGuideValidator,
    private val watchNextSync: WatchNextSync,
) : ViewModel() {

    private val _state = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)

    /** Состояние экрана. */
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)

    /** Текст последнего обновления данных. */
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _guides = MutableStateFlow<List<EpgGuide>>(emptyList())

    /** Гиды программы передач, добавленные пользователем. */
    val guides: StateFlow<List<EpgGuide>> = _guides.asStateFlow()

    private val _guideBusy = MutableStateFlow(false)

    /** Идёт проверка адреса гида перед сохранением. */
    val guideBusy: StateFlow<Boolean> = _guideBusy.asStateFlow()

    private val _guideMessage = MutableStateFlow<String?>(null)

    /** Сообщение раздела гидов: чем закончилась проверка адреса. */
    val guideMessage: StateFlow<String?> = _guideMessage.asStateFlow()

    private val _updating = MutableStateFlow(false)

    /** Идёт ли обновление данных прямо сейчас. */
    val updating: StateFlow<Boolean> = _updating.asStateFlow()

    /** Сколько секунд вперёд плеер держит в буфере. */
    val bufferSeconds: StateFlow<Int> = playerSettingsStore.bufferSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_BUFFER_SECONDS,
    )

    /** Потолок качества видео. */
    val videoQuality: StateFlow<VideoQuality> = playerSettingsStore.videoQuality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = VideoQuality.AUTO,
    )

    /** Потолок паузы между попытками восстановления потока, секунды. */
    val recoveryDelaySeconds: StateFlow<Int> = playerSettingsStore.recoveryDelaySeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_RECOVERY_DELAY_SECONDS,
    )

    /** Окно программы передач: сколько часов назад хранить. */
    val epgWindowHours: StateFlow<Int> = playerSettingsStore.epgWindowHours.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_EPG_WINDOW_HOURS,
    )

    /** Общее смещение гида, часы: применяется к каналам без своего сдвига. */
    val globalEpgShift: StateFlow<Int> = playerSettingsStore.globalEpgShift.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = 0,
    )

    /** Экономия трафика: без превью и без логотипов. */
    val dataSaver: StateFlow<Boolean> = playerSettingsStore.dataSaver.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = false,
    )

    /** Выбранный язык каналов, `null` — все языки. */
    val channelLanguage: StateFlow<String?> = playerSettingsStore.channelLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = null,
    )

    /** Размер шрифта интерфейса: `1.0` — обычный. */
    val fontScale: StateFlow<Float> = playerSettingsStore.fontScale.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_FONT_SCALE,
    )

    /**
     * Задаёт размер шрифта интерфейса.
     *
     * @param scale масштаб шрифта (0.5–2.0); применяется сразу, без перезапуска.
     */
    fun setFontScale(scale: Float) {
        viewModelScope.launch { playerSettingsStore.setFontScale(scale) }
    }

    /** Определять язык канала по названию (`... HD ES`, `... US`). */
    val languageFromName: StateFlow<Boolean> = playerSettingsStore.languageFromName.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = false,
    )

    /**
     * Включает определение языка по названию канала.
     *
     * @param enabled разбирать хвост имени канала как язык; влияет на следующий разбор плейлиста.
     */
    fun setLanguageFromName(enabled: Boolean) {
        // Парсер общий: настройка действует при следующем импорте, без перезапуска приложения.
        parser.languageFromName = enabled
        viewModelScope.launch { playerSettingsStore.setLanguageFromName(enabled) }
    }

    /** Языки из плейлиста для выбора: без пустых значений и дублей. */
    private val languagesState = MutableStateFlow<List<String>>(emptyList())

    /** Языки из плейлиста для выбора. */
    val languages: StateFlow<List<String>> = languagesState.asStateFlow()


    /** Лента «Сейчас идёт» на домашнем экране Android TV. */
    val watchNextEnabled: StateFlow<Boolean> = playerSettingsStore.watchNextEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_WATCH_NEXT_ENABLED,
    )

    /** Сколько карточек показывать в ленте «Сейчас идёт». */
    val watchNextLimit: StateFlow<Int> = playerSettingsStore.watchNextLimit.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_WATCH_NEXT_LIMIT,
    )

    /** Язык звука по умолчанию. */
    val audioLanguage: StateFlow<AudioLanguage> = playerSettingsStore.audioLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AudioLanguage.RUSSIAN,
    )

    /** Глубина архива в часах. */
    val archiveDepthHours: StateFlow<Int> = playerSettingsStore.archiveDepthHours.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_ARCHIVE_DEPTH_HOURS,
    )

    /** Сколько секунд меню плеера показывается без действий. */
    val osdHideSeconds: StateFlow<Int> = playerSettingsStore.osdHideSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_OSD_HIDE_SECONDS,
    )

    /** Режим кадра: та же настройка, что в OSD плеера (решение Д4). */
    val aspectMode: StateFlow<AspectMode> = playerSettingsStore.aspectMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AspectMode.FIT,
    )

    /** Режим эфира: прямой поток или собственный буфер. */
    val directStream: StateFlow<Boolean> = playerSettingsStore.directStream.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = true,
    )

    /**
     * Меняет режим кадра.
     *
     * @param mode выбранный режим.
     */
    fun setAspectMode(mode: AspectMode) {
        viewModelScope.launch { playerSettingsStore.setAspectMode(mode) }
    }

    /**
     * Меняет режим эфира.
     *
     * @param direct `true` — прямой поток, `false` — собственный буфер.
     */
    fun setDirectStream(direct: Boolean) {
        viewModelScope.launch { playerSettingsStore.setDirectStream(direct) }
    }

    init {
        // Начальное значение «определять язык по названию» ставит SmotrishkaApplication:
        // в init ViewModel читать настройку нельзя — это ломает тесты своими корутинами.
        // Языки для фильтра собираем из плейлиста: список нужен только в настройках.
        viewModelScope.launch {
            val values = runCatching {
                catalogStore.playlist().channels.mapNotNull { it.language?.takeIf(String::isNotBlank) }
            }.getOrDefault(emptyList())
            languagesState.value = values.distinct().sorted()
        }
        // Сообщения фонового импортёра показываем в строке состояния настроек.
        viewModelScope.launch {
            playlistImporter.status.collect { message ->
                if (message != null) _status.value = message
            }
        }
        refresh()
        refreshGuides()
        // Статус прошлого обновления не должен затирать свежий: чтение DataStore в init
        // медленнее, чем updateData(), и на медленном раннере поздняя запись обнуляла статус.
        viewModelScope.launch {
            val stored = syncStatusStore.last()?.message
            if (stored != null && _status.value == null) _status.value = stored
        }
    }

    /** Перечитывает активный источник (например, после возврата с онбординга). */
    fun refresh() {
        viewModelScope.launch { _state.value = buildState() }
    }

    /** Перечитывает пользовательские гиды программы передач. */
    fun refreshGuides() {
        viewModelScope.launch { _guides.value = epgGuideRepository.guides() }
    }

    /**
     * Проверяет адрес гида и сохраняет его только при доступности.
     *
     * Недоступный адрес в базу не попадает: иначе фоновое обновление каждые 6 часов
     * ходило бы в мёртвый адрес. Причина отказа показывается в [guideMessage].
     *
     * @param url адрес XMLTV.
     */
    fun addGuide(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            _guideMessage.value = "Укажите адрес гида"
            return
        }
        if (_guideBusy.value) return

        viewModelScope.launch {
            _guideBusy.value = true
            _guideMessage.value = null
            when (val check = epgGuideValidator.check(trimmed)) {
                is EpgGuideCheck.Available -> {
                    val guide = epgGuideRepository.add(trimmed)
                    _guides.value = epgGuideRepository.guides()
                    _guideMessage.value = "Гид добавлен: ${guide.name}"
                }

                is EpgGuideCheck.Unavailable -> {
                    _guideMessage.value = "Гид не добавлен: ${check.reason}"
                }
            }
            _guideBusy.value = false
        }
    }

    /**
     * Удаляет гид из фонового разбора.
     *
     * @param id идентификатор гида.
     */
    fun removeGuide(id: String) {
        viewModelScope.launch {
            epgGuideRepository.remove(id)
            _guides.value = epgGuideRepository.guides()
            _guideMessage.value = null
        }
    }

    /**
     * Очищает каталог и программу передач и заново загружает данные текущего источника.
     *
     * Каталог заменяется внутри загрузки; программа передач очищается заранее, чтобы при сбое
     * загрузки не осталось передач прежнего плейлиста.
     */
    fun updateData() {
        if (_updating.value) return

        viewModelScope.launch {
            val source = sourceRepository.active()
            if (source == null) {
                _status.value = "Источник не настроен"
                return@launch
            }

            _updating.value = true
            epgStore.clear()

            val result = catalogUpdater.update(source)
            syncStatusStore.save(result, System.currentTimeMillis())
            _status.value = syncStatusStore.last()?.message
            _updating.value = false
            refresh()
        }
    }

    /**
     * Возвращает каталог к сохранённому источнику провайдера.
     *
     * Сбрасывает отметку файла и загружает данные по активному источнику: код вводить не нужно,
     * он уже сохранён в источнике.
     */
    fun returnToProviderSource() {
        if (_updating.value) return
        viewModelScope.launch {
            playerSettingsStore.setFileSource(null)
            _status.value = "Каталог по источнику провайдера"
            android.util.Log.d("SmotrishkaSync", "file source cleared, provider source restored")
        }
        updateData()
    }

    /**
     * Импортирует плейлист из файла, выбранного пользователем.
     *
     * Файл читается системным выбором (SAF) и разбирается тем же парсером, что и сетевой
     * плейлист; каталог заменяется целиком, программа передач очищается.
     *
     * @param text содержимое файла плейлиста.
     */
    /** Имя файла, если каталог загружен из файла; иначе `null`. */
    val fileSource: StateFlow<String?> = playerSettingsStore.fileSourceUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun importPlaylist(text: String, fileName: String? = null, uri: String? = null) {
        // Работа идёт в фоновом импортёре: уход с экрана не должен обрывать загрузку программы.
        playlistImporter.import(text, fileName, uri)
    }

    /**
     * Меняет размер буфера плеера.
     *
     * @param seconds значение из [PlayerSettingsStore.BUFFER_CHOICES].
     */
    fun setBufferSeconds(seconds: Int) {
        viewModelScope.launch { playerSettingsStore.setBufferSeconds(seconds) }
    }

    /**
     * Меняет потолок качества.
     *
     * @param quality выбранное качество.
     */
    fun setVideoQuality(quality: VideoQuality) {
        viewModelScope.launch { playerSettingsStore.setVideoQuality(quality) }
    }

    /**
     * Меняет потолок паузы между попытками восстановления потока.
     *
     * @param seconds значение из [PlayerSettingsStore.RECOVERY_DELAY_CHOICES].
     */
    fun setRecoveryDelaySeconds(seconds: Int) {
        viewModelScope.launch { playerSettingsStore.setRecoveryDelaySeconds(seconds) }
    }

    /**
     * Меняет окно программы передач.
     *
     * @param hours значение из [PlayerSettingsStore.EPG_WINDOW_CHOICES].
     */
    fun setEpgWindowHours(hours: Int) {
        viewModelScope.launch { playerSettingsStore.setEpgWindowHours(hours) }
    }

    /**
     * Меняет общее смещение гида.
     *
     * @param hours смещение в часах, допустимо −12…+12.
     */
    fun setGlobalEpgShift(hours: Int) {
        viewModelScope.launch { playerSettingsStore.setGlobalEpgShift(hours) }
    }

    /**
     * Включает или выключает экономию трафика.
     *
     * @param enabled экономить ли трафик.
     */
    fun setDataSaver(enabled: Boolean) {
        viewModelScope.launch { playerSettingsStore.setDataSaver(enabled) }
    }

    /**
     * Выбирает язык каналов для списков.
     *
     * @param language язык из плейлиста, `null` — показывать все каналы.
     */
    fun setChannelLanguage(language: String?) {
        viewModelScope.launch { playerSettingsStore.setChannelLanguage(language) }
    }

    /**
     * Включает или выключает ленту «Сейчас идёт» на домашнем экране Android TV.
     *
     * Выключение сразу убирает канал из лаунчера, не дожидаясь возврата на главный экран.
     *
     * @param enabled публиковать ли канал приложения в лаунчере.
     */
    fun setWatchNextEnabled(enabled: Boolean) {
        viewModelScope.launch {
            playerSettingsStore.setWatchNextEnabled(enabled)
            if (!enabled) {
                runCatching { watchNextSync.clear() }
                    .onFailure { error ->
                        android.util.Log.w("SmotrishkaSettings", "лента «Сейчас идёт» не удалена", error)
                    }
            }
        }
    }

    /**
     * Меняет количество карточек в ленте «Сейчас идёт».
     *
     * @param limit значение из [PlayerSettingsStore.WATCH_NEXT_LIMIT_CHOICES].
     */
    fun setWatchNextLimit(limit: Int) {
        viewModelScope.launch { playerSettingsStore.setWatchNextLimit(limit) }
    }

    /**
     * Меняет язык звука по умолчанию.
     *
     * @param language выбранный язык.
     */
    fun setAudioLanguage(language: AudioLanguage) {
        viewModelScope.launch { playerSettingsStore.setAudioLanguage(language) }
    }

    /**
     * Меняет глубину архива.
     *
     * @param hours значение из [PlayerSettingsStore.ARCHIVE_DEPTH_CHOICES].
     */
    fun setArchiveDepthHours(hours: Int) {
        viewModelScope.launch { playerSettingsStore.setArchiveDepthHours(hours) }
    }

    /**
     * Запоминает время показа меню плеера.
     *
     * @param seconds значение из [PlayerSettingsStore.OSD_HIDE_CHOICES].
     */
    fun setOsdHideSeconds(seconds: Int) {
        viewModelScope.launch { playerSettingsStore.setOsdHideSeconds(seconds) }
    }

    private suspend fun buildState(): SettingsUiState = runCatching { loadState() }.getOrElse { error ->
        SettingsUiState.Error("Не удалось прочитать источник: ${error.message ?: "неизвестная ошибка"}")
    }

    private suspend fun loadState(): SettingsUiState {
        val source = sourceRepository.active() ?: return SettingsUiState.Empty

        return when (source) {
            is PlaylistSource.M3uUrl -> SettingsUiState.Content(
                sourceLabel = source.name?.takeIf(String::isNotBlank) ?: "Плейлист по ссылке",
                sourceDetail = authorityOf(source.url),
            )

            is PlaylistSource.Xtream -> SettingsUiState.Content(
                sourceLabel = "Xtream",
                sourceDetail = "${authorityOf(source.server)} · ${source.username}",
            )

            is PlaylistSource.IedemCode -> SettingsUiState.Content(
                sourceLabel = "iedem.tv по коду",
                sourceDetail = mask(source.code),
            )

            is PlaylistSource.File -> SettingsUiState.Content(
                sourceLabel = source.name?.takeIf(String::isNotBlank) ?: "Плейлист из файла",
                sourceDetail = "файл на устройстве (обновление перечитывает его)",
            )
        }
    }

    private fun authorityOf(url: String): String =
        runCatching { URI(url).authority ?: URI(url).host }.getOrNull().orEmpty().ifBlank { url }

    private fun mask(secret: String): String =
        if (secret.length <= MASK_TAIL) "••••" else "••••" + secret.takeLast(MASK_TAIL)

    private companion object {
        const val MASK_TAIL = 4
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
