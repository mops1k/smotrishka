package com.mops1k.smotrishka.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.ChannelEpgStore
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.FavoritesStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.RecentChannelsStore
import com.mops1k.smotrishka.tv.epg.EpgUpdateState
import com.mops1k.smotrishka.tv.core.model.Playlist
import com.mops1k.smotrishka.tv.core.data.SearchHit
import com.mops1k.smotrishka.tv.core.data.SearchRepository
import com.mops1k.smotrishka.tv.core.data.SyncStatusStore
import com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextCardsBuilder
import com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextSource
import com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextSync
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.playlist.EpgResolver
import com.mops1k.smotrishka.tv.epg.EpgUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Главный экран: разделы «Телеканалы», «Избранное», «Недавние», категории, список каналов,
 * карточка выбранного и разворот телепрограммы.
 *
 * Программа подтягивается по сопоставлению каналов с EPG; каналы без сопоставления остаются в
 * списке, но без программы — пользователь видит честную разницу.
 *
 * @param catalogStore каталог каналов;
 * @param epgStore программа передач;
 * @param epgMatcher сопоставление каналов каталога и EPG;
 * @param settingsStore настройки (последняя категория, глубина архива);
 * @param favoritesStore избранные каналы;
 * @param recentChannels недавно просмотренные каналы;
 * @param syncStatusStore время последней синхронизации;
 * @param clock текущее время (подменяется в тестах);
 * @param epgUpdater фоновая догрузка программы передач;
 * @param searchRepository полнотекстовый поиск по каталогу и передачам.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogStore: CatalogStore,
    private val epgStore: EpgStore,
    private val channelEpgStore: ChannelEpgStore,
    private val settingsStore: PlayerSettingsStore,
    private val favoritesStore: FavoritesStore,
    private val recentChannels: RecentChannelsStore,
    private val syncStatusStore: SyncStatusStore,
    private val clock: Clock,
    private val epgUpdater: EpgUpdater,
    private val searchRepository: SearchRepository,
    private val watchNextSync: WatchNextSync,
) : ViewModel() {

    /** Фоновая догрузка программы передач: каналы уже доступны, гид приходит позже. */
    val epgState: StateFlow<EpgUpdateState> = epgUpdater.state

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)

    /** Состояние экрана. */
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Сколько секунд вперёд держать в буфере (общая настройка плеера). */
    val bufferSeconds: StateFlow<Int> = settingsStore.bufferSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_BUFFER_SECONDS,
    )

    /** Экономия трафика: без автозапуска превью и без логотипов. */
    val dataSaver: StateFlow<Boolean> = settingsStore.dataSaver.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = false,
    )

    /** Текущий раздел. */
    private var section: HomeSection = HomeSection.TV

    /** Выбранная категория, `null` — «Все каналы». */
    private var selectedCategoryId: String? = null

    /** Последняя категория раздела «Все каналы»: возвращается при переключении разделов. */
    private var lastTvCategoryId: String? = null

    /** Ручной сдвиг гида по каналам, часы: у части каналов провайдер отдаёт сетку со сдвигом. */
    private var epgShifts: Map<String, Int> = emptyMap()

    // Общее смещение гида: применяется к каналам без своего сдвига.
    private var globalEpgShift: Int = 0

    /** Выбранный язык каналов, `null` — все языки. */
    private var channelLanguage: String? = null

    /** Пользователь сам выбрал категорию: за просмотром её больше не переключаем. */
    private var userChoseCategory: Boolean = false
    // Показываем список каналов; «назад» из него открывает панель категорий.
    private var showChannels: Boolean = true

    /** Строка поиска: фильтр по названию канала и запрос в полнотекстовый индекс. */
    private var query: String = ""

    private val _searchResults = MutableStateFlow<List<SearchHit>>(emptyList())

    /** Результаты полнотекстового поиска: каналы, категории и текущие передачи. */
    val searchResults: StateFlow<List<SearchHit>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null

    /** Развёрнута ли телепрограмма в карточке. */
    private var programmeOpen: Boolean = false

    // Сколько каналов было при последнем сопоставлении с гидом: сопоставление — самая
    // дорогая часть старта (сотни миллисекунд, на приставке до 4 с), и без кеша она
    // повторялась на каждую пересборку экрана.
    // Пересборки не должны накладываться: каждая идёт по всем каналам, и несколько
    // параллельных проходов держали экран в «Загружаем каналы…».
    private var refreshJob: Job? = null

    /**
     * Экран списка сейчас на виду.
     *
     * Пока открыт плеер, главный экран не в композиции: пересчитывать каталог и программу
     * незачем, это только грузит устройство (указание Александра 09.11.2026).
     */
    private var screenVisible: Boolean = true

    /**
     * Канал, подтверждённый OK: по нему показывается видео в карточке.
     *
     * Переживает пересборки состояния (обновление гида не должно переключать превью).
     * `null` — подтверждения не было, берём выбранный канал.
     */
    private var confirmedVideoChannelId: ChannelId? = null

    private var matchedCatalogSize: Int = -1
    private var matchedEpgSize: Int = -1

    /** Каналы последней загрузки: фильтрация идёт из них, без чтения базы. */
    private var allChannels: List<ChannelUi> = emptyList()

    /** Сопоставление каналов с EPG из последней загрузки. */
    private var epgMatches: Map<ChannelId, String> = emptyMap()

    /**
     * Сохранённые связи каналов с гидом из последней загрузки.
     *
     * Используются и списком, и телепрограммой: считается один раз при обновлении каталога.
     */
    private var epgLinks: Map<ChannelId, String> = emptyMap()

    /** Избранные каналы. */
    private var favorites: Set<String> = emptySet()

    /** Сборка карточек ленты: только каналы с текущей передачей. */
    private val watchNextBuilder = WatchNextCardsBuilder()

    /** Публикация ленты откладывается: состояние пересобирается несколько раз подряд. */
    private var watchNextJob: Job? = null

    /** Недавние каналы: свежие первыми. */
    private var recent: List<String> = emptyList()

    /** Каналы с нестабильным потоком. */
    private var unstable: Set<String> = emptySet()

    /** Каналы с проверенным архивом. */
    private var archiveVerified: Set<String> = emptySet()

    /** Признак, что сохранённые категория и избранное прочитаны. */
    private val restoreDone = kotlinx.coroutines.CompletableDeferred<Unit>()

    private var selectedChannelId: ChannelId? = null
    private var selectJob: Job? = null

    /** Время последнего обновления данных, которое уже показано на экране. */
    private var shownSyncAt: Long? = null

    init {
        // Гид догружается в фоне: как только передачи появились в базе, перестраиваем список,
        // иначе программа видна только после перезапуска приложения.
        // Гид обновляется в фоне и не должен тормозить каналы. Раньше экран перестраивался на
        // каждое изменение счётчика передач: во время импорта это десятки полных пересборок
        // списка на 3752 канала, и старт выглядел как «Загружаем каналы…» на много секунд.
        viewModelScope.launch {
            epgUpdater.state.collect { state ->
                if (state is EpgUpdateState.Done || state is EpgUpdateState.Failed) {
                    refresh(force = true)
                }
            }
        }
        // Страховка: если передачи изменились в обход загрузчика, перечитываем их после паузы.
        viewModelScope.launch {
            epgStore.programmeCount
                .debounce(EPG_SETTLE_MS)
                .collect { refresh(force = true) }
        }
        viewModelScope.launch {
            favorites = runCatching { favoritesStore.channels.first() }.getOrDefault(favorites)
            unstable = runCatching { settingsStore.unstableChannels.first() }.getOrDefault(emptySet())
            archiveVerified = runCatching { settingsStore.archiveVerified.first() }.getOrDefault(emptySet())
            launch {
                settingsStore.archiveVerified.collect { verified ->
                    archiveVerified = verified
                    val content = _state.value
                    if (content is HomeUiState.Content) {
                        _state.value = content.copy(archiveVerified = verified)
                    }
                }
            }
            // После возврата из плеера карточка показывает канал, который только что смотрели.
            followLastWatched()
            val saved = settingsStore.lastCategory.first()
            if (saved != null && saved.isNotEmpty()) {
                selectedCategoryId = if (saved == PlayerSettingsStore.ALL_CATEGORY) null else saved
                // Запоминаем и в поле: при переключении разделов категория вернётся.
                lastTvCategoryId = selectedCategoryId
            }
            restoreDone.complete(Unit)
        }
        viewModelScope.launch {
            favoritesStore.channels.collect { set ->
                favorites = set
                refreshVisible()
            }
        }
        viewModelScope.launch {
            // Сдвиг гида по каналам: меняется редко, но список должен обновиться сразу.
            settingsStore.epgShifts.collect { shifts ->
                if (shifts == epgShifts) return@collect
                epgShifts = shifts
                // Сдвиг меняет названия передач в карточках: нужен полный проход, а не только
                // перерисовка видимого списка (замечание Александра 09.11.2026).
                refresh(force = true)
            }
        }
        viewModelScope.launch {
            recentChannels.channels.collect { ids ->
                recent = ids
                refreshVisible()
            }
        }
        viewModelScope.launch { shownSyncAt = syncStatusStore.last()?.atMillis }
        refresh()
    }

    /**
     * Переключает раздел.
     *
     * Категория при смене раздела сбрасывается на «Все каналы» (`docs/design/flows.md`),
     * выбранный канал сохраняется: карточка справа не должна прыгать.
     *
     * @param newSection выбранный раздел.
     */
    fun selectSection(newSection: HomeSection) {
        if (section == newSection) return
        // Категория помнится отдельно для «Всех каналов»: при возврате в ТВ она восстанавливается,
        // а не сбрасывается в «Все каналы» (замечание Александра 06.11.2026).
        if (section == HomeSection.TV) lastTvCategoryId = selectedCategoryId
        section = newSection
        selectedCategoryId = if (newSection == HomeSection.TV) lastTvCategoryId else null
        programmeOpen = false
        val restoredCategory = selectedCategoryId
        viewModelScope.launch { settingsStore.setLastCategory(restoredCategory) }
        val current = _state.value
        if (current is HomeUiState.Content && allChannels.isNotEmpty()) {
            _state.value = current.copy(
                section = section,
                selectedCategoryId = null,
                channels = visibleChannels(),
                programmeOpen = false,
                schedule = emptyList(),
            )
            publishWatchNext()
            return
        }
        // Каталог ещё не в памяти — сначала быстрый срез без гида,
        // гид догружаем следом: иначе выбор категории ждал полную пересборку.
        viewModelScope.launch {
            runCatching { buildState(withEpg = false) }.onSuccess { _state.value = it }
            _state.value = buildWithEpgRetry()
            publishWatchNext()
        }
    }

    /**
     * Полный проход с повторами, пока не появятся передачи.
     *
     * Один разовый проход мог не успеть прочитать гид (база большая, параллельно пишется
     * поисковый индекс), и тогда экран оставался без программ до перезапуска приложения.
     */
    private suspend fun buildWithEpgRetry(): HomeUiState {
        var state = buildState()
        repeat(EPG_RETRY_ATTEMPTS) {
            val content = state as? HomeUiState.Content
            val hasProgrammes = content == null || content.channels.any { it.nowTitle != null }
            if (hasProgrammes) return state
            delay(EPG_RETRY_DELAY_MS)
            state = buildState()
        }
        return state
    }

    /**
     * Выбирает категорию.
     *
     * @param categoryId ключ категории, `null` — «Все каналы».
     */
    fun selectCategory(categoryId: String?) {
        // Пользователь выбрал категорию сам: после просмотра больше не переключаем её.
        userChoseCategory = true
        selectedCategoryId = categoryId
        if (section == HomeSection.TV) lastTvCategoryId = categoryId
        // Выбор категории сбрасывает поиск: иначе список оставался отфильтрованным.
        searchJob?.cancel()
        _searchResults.value = emptyList()
        query = ""
        showChannels = true
        viewModelScope.launch { settingsStore.setLastCategory(categoryId) }
        val current = _state.value
        if (current is HomeUiState.Content && allChannels.isNotEmpty()) {
            // Выбранный канал мог остаться в прежней категории: тогда в новом списке его нет,
            // фокус ни на что не встаёт и остаётся в левом меню. Берём первый канал категории.
            val visible = visibleChannels()
            if (visible.none { it.channel.id == selectedChannelId }) {
                selectedChannelId = visible.firstOrNull()?.channel?.id
            }
            _state.value = current.copy(
                selectedCategoryId = categoryId,
                channels = visible,
                selectedChannelId = selectedChannelId,
                previewChannel = visible.firstOrNull { it.channel.id == selectedChannelId }
                    ?: current.previewChannel,
                // Без этого экран оставался в панели категорий: флаг переключался в поле,
                // но не в опубликованном состоянии.
                showChannels = true,
            )
            publishWatchNext()
            return
        }
        // Каталог ещё не в памяти: показываем срез без гида, гид догружаем следом, чтобы
        // переключение категории не ждало сопоставления и выборки передач.
        viewModelScope.launch {
            runCatching { buildState(withEpg = false) }.onSuccess { _state.value = it }
            _state.value = buildState()
            publishWatchNext()
        }
    }

    /** Возвращает список каналов из панели категорий (кнопка «назад»). */
    fun showChannelsList() {
        showChannels = true
        val current = _state.value
        if (current is HomeUiState.Content) {
            _state.value = current.copy(showChannels = true)
        }
    }

    /** Открывает панель категорий (кнопка «назад» из списка каналов). */
    fun showCategories() {
        showChannels = false
        val current = _state.value
        if (current is HomeUiState.Content) {
            _state.value = current.copy(showChannels = false)
        }
    }

    /**
     * Меняет строку поиска.
     *
     * Фильтр идёт по уже загруженному списку: у реальных плейлистов тысячи каналов, и обход
     * базы на каждый введённый символ недопустим.
     *
     * @param value новая строка поиска.
     */
    fun setQuery(value: String) {
        query = value
        refreshVisible()
        // Один индекс на каналы, категории и передачи: тот же запрос уходит в FTS.
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (value.length < SEARCH_MIN_CHARS) {
                _searchResults.value = emptyList()
                return@launch
            }
            runCatching {
                searchRepository.ensureIndexed()
                searchRepository.search(value)
            }.onSuccess { _searchResults.value = it }
                .onFailure { _searchResults.value = emptyList() }
        }
    }

    /** Закрывает поиск и сбрасывает фильтр вместе с запросом. */
    fun closeSearch() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        if (query.isEmpty()) return
        query = ""
        // Канал мог быть найден поиском в другой категории: после сброса он остаётся
        // выбранным, а в текущей категории его нет — тогда показываем «Все каналы».
        val current = _state.value
        if (current is HomeUiState.Content && allChannels.isNotEmpty()) {
            val visible = visibleChannels()
            if (selectedChannelId != null && visible.none { it.channel.id == selectedChannelId }) {
                selectedCategoryId = null
                lastTvCategoryId = null
                viewModelScope.launch { settingsStore.setLastCategory(null) }
                selectedChannelId = visible.firstOrNull()?.channel?.id
            }
        }
        refreshVisible()
    }

    /**
     * Применяет запрос к списку каналов (кнопка «Искать» в окне поиска).
     *
     * Запрос остаётся фильтром списка, а попап с результатами закрывается.
     */
    fun applySearchFilter() {
        searchJob?.cancel()
        _searchResults.value = emptyList()
        refreshVisible()
    }

    /** Разворачивает телепрограмму выбранного канала. */
    fun openProgramme() {
        val current = _state.value as? HomeUiState.Content ?: return
        val channel = current.previewChannel?.channel ?: return
        programmeOpen = true
        viewModelScope.launch {
            val schedule = scheduleFor(channel)
            val fresh = _state.value
            if (fresh is HomeUiState.Content) {
                _state.value = fresh.copy(programmeOpen = true, schedule = schedule)
            }
        }
    }

    /** Сворачивает телепрограмму: карточка возвращается в обычный вид. */
    fun closeProgramme() {
        programmeOpen = false
        val current = _state.value
        if (current is HomeUiState.Content) {
            _state.value = current.copy(programmeOpen = false, schedule = emptyList())
        }
    }

    /**
     * Переключает канал в избранном.
     *
     * Состояние меняется сразу (оптимистично), а запись в DataStore идёт следом: иначе сердце
     * нажимается мгновенно, а канал в разделе «Избранное» появляется с задержкой.
     */
    fun toggleFavorite(channelId: ChannelId) {
        val id = channelId.value
        favorites = if (id in favorites) favorites - id else favorites + id
        refreshVisible()
        viewModelScope.launch { favoritesStore.toggle(id) }
    }

    /**
     * Выбирает канал для карточки.
     *
     * Смена откладывается на несколько сотен миллисекунд: при быстрой прокрутке списка фокус
     * проскакивает строки, и без задержки карточка дёргалась бы на каждом канале.
     *
     * @param channelId ключ канала.
     */
    fun selectChannel(channelId: ChannelId) {
        selectJob?.cancel()
        selectJob = viewModelScope.launch {
            delay(SELECT_DEBOUNCE_MS)
            selectedChannelId = channelId
            val current = _state.value
            if (current is HomeUiState.Content) {
                val preview = current.previewChannel
                    ?.takeIf { it.channel.id == channelId }
                    ?: allChannels.firstOrNull { it.channel.id == channelId }
                _state.value = current.copy(
                    selectedChannelId = channelId,
                    previewChannel = preview,
                )
            }
        }
    }

    /**
     * Подтверждает канал для живого превью (нажатие OK).
     *
     * Фокус листает карточку, но видео должно переключаться только по явному подтверждению.
     *
     * @param channelId канал, который пользователь выбрал пультом.
     */
    fun confirmPreview(channelId: ChannelId) {
        confirmedVideoChannelId = channelId
        val current = _state.value
        if (current is HomeUiState.Content) {
            _state.value = current.copy(previewVideoChannelId = channelId)
        }
    }

    /**
     * Обновляет время на экране.
     *
     * Вызывается с экрана по таймеру, а не бесконечным циклом во ViewModel: иначе тесты с
     * виртуальным планировщиком крутятся вечно и сборка зависает.
     */
    fun tick() {
        // Плеер открыт — экран не виден, время и передачу не пересчитываем.
        if (!screenVisible) return
        val current = _state.value
        if (current !is HomeUiState.Content) return
        val now = clock.instant()
        if (current.channels.any { it.nowEnd?.isBefore(now) == true }) {
            viewModelScope.launch { _state.value = buildState() }
        } else {
            _state.value = current.copy(now = now)
        }
    }

    /**
     * Перечитывает каталог и программу передач.
     *
     * Экран «Загружаем каналы…» показывается только когда показывать ещё нечего: при возврате
     * из плеера или с настроек список остаётся на месте.
     *
     * @param force перестроить состояние, даже если данные не менялись.
     */
    /**
     * Сообщает модели, виден ли сейчас список каналов.
     *
     * @param visible экран в композиции (не перекрыт плеером или поиском-оверлеем).
     */
    fun setScreenVisible(visible: Boolean) {
        if (screenVisible == visible) return
        screenVisible = visible
        // Возврат на экран: сразу показываем актуальную передачу, не дожидаясь тика.
        if (visible) refresh(force = true)
    }

    fun refresh(force: Boolean = false) {
        refreshJob?.cancel()
        // Плеер перекрыл список: ничего не пересчитываем, вернёмся — пересчитаем.
        if (!screenVisible) return
        refreshJob = viewModelScope.launch {
            restoreDone.await()
            // После возврата из плеера карточка показывает канал, который смотрели последним.
            // Если выделение/категория сменились, нужен полный проход: refreshVisible()
            // не публикует раздел и категорию, и экран оставался на «Недавних».
            val followed = followLastWatched()
            android.util.Log.d(
                "SmotrishkaFollow",
                "refresh: переключились=$followed раздел=$section категория=$selectedCategoryId " +
                    "показанСписок=$showChannels",
            )
            // Общее смещение гида читаем на каждый пересчёт: настройка меняется редко,
            // а живой коллектор оставлял незавершённую корутину в тестах.
            globalEpgShift = settingsStore.globalEpgShift.first()
            // Язык списков читаем на каждый пересчёт: настройка меняется редко.
            channelLanguage = settingsStore.channelLanguage.first()
            android.util.Log.d(
                "SmotrishkaHome",
                "сдвиг гида: общий=$globalEpgShift поканальных=${epgShifts.size} " +
                    "пояс=${java.time.ZoneId.systemDefault()}",
            )
            val syncAt = syncStatusStore.last()?.atMillis
            if (!force && !followed && _state.value is HomeUiState.Content && syncAt == shownSyncAt) {
                refreshVisible()
                return@launch
            }

            if (syncAt != shownSyncAt) {
                // Данные обновились (в том числе гид): связи каналов с гидом пересобираем,
                // иначе после импорта нового гида список показывает «Программы нет»,
                // хотя окно программы находит передачи свежим сопоставлением.
                runCatching { channelEpgStore.rebuild(catalogStore.playlist().channels) }
            }

            shownSyncAt = syncAt
            if (_state.value !is HomeUiState.Content) {
                _state.value = HomeUiState.Loading
                // Быстрый проход: только каталог из базы, без гида — список появляется сразу,
                // а передачи подставляются следующим проходом. Раньше экран ждал сопоставление
                // с гидом и выборку передач, и это занимало секунды.
                runCatching { buildState(withEpg = false) }
                    .onSuccess { fast -> _state.value = fast }
            }
            // Полный проход с повторами: если гид не прочитался сразу, экран не должен
            // оставаться с многоточиями — названия передач в базе есть.
            _state.value = buildWithEpgRetry()
            publishWatchNext()
        }
    }

    /**
     * Переводит выделение на канал, который смотрели последним, и открывает его категорию.
     *
     * Без смены категории канал, переключённый в оверлее плеера, остаётся за пределами
     * видимого списка, и после «назад» экран показывает первый канал прежней категории
     * (замечание Александра 09.11.2026).
     */
    private suspend fun followLastWatched(): Boolean {
        val lastWatched = runCatching { recentChannels.channels.first().firstOrNull() }.getOrNull()
            ?: return false
        val channelId = ChannelId(lastWatched)
        val categoryId = allChannels.firstOrNull { it.channel.id == channelId }?.channel?.categoryId
            ?: runCatching {
                catalogStore.playlist().channels
                    .firstOrNull { it.id == channelId }?.categoryId
            }.getOrNull()
            ?: return false
        // Уже стоим там, где нужно: ничего не перестраиваем, иначе пересчёт списка каждый раз
        // возвращал бы пользователя в прежнюю категорию.
        val alreadyThere = section == HomeSection.TV &&
            selectedCategoryId == categoryId &&
            selectedChannelId == channelId
        if (alreadyThere) return false
        selectedChannelId = channelId
        // Канал уже открывали: живое превью должно показывать его эфир, а не логотип.
        confirmedVideoChannelId = channelId
        // Возврат из плеера — всегда в «Телеканалы» на категорию игравшего канала, списком,
        // а не панелью категорий. Работает и для «Избранного», и для «Недавних»: раздел
        // переключаем полями, без selectSection (тот публикует состояние своей корутиной
        // и мог перезаписать уже собранный список).
        userChoseCategory = false
        if (section != HomeSection.TV) {
            if (section == HomeSection.TV) lastTvCategoryId = selectedCategoryId
            section = HomeSection.TV
            programmeOpen = false
        }
        showChannels = true
        selectedCategoryId = categoryId
        lastTvCategoryId = categoryId
        settingsStore.setLastCategory(categoryId)
        android.util.Log.d(
            "SmotrishkaFollow",
            "вернулись из плеера: канал=$channelId категория=$categoryId раздел=$section",
        )
        return true
    }

    /**
     * Пересобирает ленту «Сейчас идёт».
     *
     * Приоритет источников (решение Александра 06.11.2026): избранные каналы, затем недавние
     * (свежие первыми), затем последняя выбранная категория, остаток — случайные каналы
     * каталога. Канал показывается один раз, каналы без текущей передачи не попадают.
     * Публикация откладывается: состояние пересобирается несколькими проходами, и каждый
     * проход в лаунчере был бы лишней записью.
     */
    private fun publishWatchNext() {
        // Каталог берётся целиком, а не из видимого списка: лента не зависит от раздела
        // и от строки поиска.
        val sources = allChannels.map { WatchNextSource(it.channel, it.nowTitle) }
        watchNextJob?.cancel()
        watchNextJob = viewModelScope.launch {
            if (!settingsStore.watchNextEnabled.first()) {
                clearWatchNext()
                return@launch
            }
            val limit = settingsStore.watchNextLimit.first()
            // Категория берётся из памяти: selectCategory пишет настройку асинхронно, и чтение
            // из хранилища в этой же корутине ещё видит прошлую категорию — лента собиралась
            // со старым приоритетом и больше не пересобиралась.
            val lastCategory = lastTvCategoryId
                ?.takeUnless { it == PlayerSettingsStore.ALL_CATEGORY }
            val cards = watchNextBuilder.build(
                favourites = favorites,
                recent = recent,
                lastCategoryId = lastCategory,
                channels = sources,
                limit = limit,
            )
            // Показывать нечего — ленты быть не должно: канал удаляется из лаунчера.
            if (cards.isEmpty()) {
                clearWatchNext()
                return@launch
            }
            delay(WATCH_NEXT_DEBOUNCE_MS)
            runCatching { watchNextSync.publish(cards) }
                .onFailure { error ->
                    android.util.Log.w(LOGTAG, "лента «Сейчас идёт» не опубликована", error)
                }
        }
    }

    /** Убирает канал ленты из лаунчера: показывать нечего или лента выключена. */
    private suspend fun clearWatchNext() {
        runCatching { watchNextSync.clear() }
            .onFailure { error ->
                android.util.Log.w(LOGTAG, "лента «Сейчас идёт» не удалена", error)
            }
    }

    /** Пересобирает видимый список из уже загруженного каталога. */
    private fun refreshVisible() {
        val current = _state.value
        if (current !is HomeUiState.Content) return
        _state.value = current.copy(
            channels = visibleChannels(),
            favorites = favorites,
            recent = recent,
            query = query,
        )
    }

    /** Каналы текущего раздела после фильтра по категории и поиску. */
    private fun visibleChannels(): List<ChannelUi> {        val base = when (section) {
            HomeSection.TV -> when (val category = selectedCategoryId) {
                null -> allChannels
                else -> allChannels.filter { it.channel.categoryId == category }
            }

            HomeSection.FAVORITES -> allChannels.filter { it.channel.id.value in favorites }
            // Порядок «Недавних» — порядок просмотра, а не алфавит.
            HomeSection.RECENT -> recent.mapNotNull { id ->
                allChannels.firstOrNull { it.channel.id.value == id }
            }
        }
        val searched = if (query.isBlank()) {
            base
        } else {
            base.filter { it.channel.name.contains(query.trim(), ignoreCase = true) }
        }
        return if (section == HomeSection.RECENT) searched else searched.sortedBy { it.channel.name.lowercase() }
    }

    /** Телепрограмма канала для разворота. */
    private suspend fun scheduleFor(channel: Channel): List<ProgrammeRowUi> {
        // Связь канала с гидом берём из сохранённых связей (та же карта, что у списка):
        // прежняя карта сопоставления больше не заполняется, из-за чего телепрограмма
        // оставалась пустой.
        val epgId = epgLinks[channel.id] ?: channel.epgId ?: return emptyList()
        val now = clock.instant()
        // Ручной сдвиг гида: время передач у канала сдвинуто относительно эфира.
        val shift = secondsShift(channel)
        val programmes = epgStore.programmes(
            channelId = epgId,
            from = now.minus(SCHEDULE_BACK_HOURS, ChronoUnit.HOURS),
            to = now.plus(SCHEDULE_FORWARD_HOURS, ChronoUnit.HOURS),
        )
        val shiftSeconds = secondsShift(channel)
        // Сдвинутая сетка: статусы считаются по реальному «сейчас», а время — с поправкой.
        val shifted = if (shiftSeconds == 0L) {
            programmes
        } else {
            programmes.map { it.copy(start = it.start.plusSeconds(shiftSeconds), end = it.end.plusSeconds(shiftSeconds)) }
        }
        return ProgrammeRows.build(
            programmes = shifted,
            now = now,
            channelHasArchive = channel.hasArchive(),
        )
    }

    /**
     * Сдвиг гида канала в часах.
     *
     * Свой сдвиг канала важнее общего (решение Александра 09.11.2026): общий нужен, когда
     * провайдер отдаёт всю сетку в чужом поясе, а поканальный — для отдельных каналов.
     */
    private fun shiftHours(channelId: String): Int = epgShifts[channelId] ?: globalEpgShift

    /** Сдвиг гида канала в секундах (положительный — сетка «отстаёт» от эфира). */
    private fun secondsShift(channel: Channel): Long = shiftHours(channel.id.value).toLong() * 60L * 60L

    /**
     * Задаёт сдвиг гида для текущего канала карточки.
     *
     * @param delta шаг в часах, −1 или +1.
     */
    fun adjustEpgShift(delta: Int) {
        val current = _state.value as? HomeUiState.Content ?: return
        val preview = current.previewChannel ?: return
        val channelId = preview.channel.id.value
        val next = ((epgShifts[channelId] ?: 0) + delta)
            .coerceIn(-PlayerSettingsStore.MAX_EPG_SHIFT_HOURS, PlayerSettingsStore.MAX_EPG_SHIFT_HOURS)
        viewModelScope.launch {
            settingsStore.setEpgShift(channelId, next)
            // Новое значение сразу в память: пересобираем только телепрограмму этого канала,
            // без полного перечитывания каталога и гида (было заметно медленно).
            epgShifts = epgShifts.toMutableMap().apply {
                if (next == 0) remove(channelId) else put(channelId, next)
            }
            val rows = if (current.programmeOpen) scheduleFor(preview.channel) else current.schedule
            val latest = _state.value as? HomeUiState.Content ?: return@launch
            _state.value = latest.copy(schedule = rows, epgShiftHours = next)
            // Карточки каналов тоже считают передачу со сдвигом: без полного прохода
            // в списке остаются названия, посчитанные до смены сдвига.
            refresh(force = true)
        }
    }

    /**
     * Доступен ли архив канала.
     *
     * Заявленный провайдером архив считается доступным, пока фоновая проверка не сказала
     * обратное: пустой набор проверки означает «ещё не проверяли».
     */
    private fun Channel.hasArchive(): Boolean = archive != null

    /**
     * Подходит ли канал под выбранный язык.
     *
     * Канал без метки показывается всегда (решение Александра 09.11.2026): фильтр не должен
     * терять каналы, язык которых не определён.
     *
     * @param channel карточка канала с меткой языка из плейлиста.
     */
    private fun matchesLanguage(channel: ChannelUi): Boolean {
        val selected = channelLanguage ?: return true
        val language = channel.channel.language
        // «Основные» — только каналы без метки языка: так отсекаются все явные языки.
        if (selected == PlayerSettingsStore.UNLABELLED_LANGUAGE) return language == null
        return language == null || language.equals(selected, ignoreCase = true)
    }

    private suspend fun buildState(withEpg: Boolean = true): HomeUiState =
        try {
            loadState(withEpg)
        } catch (cancelled: CancellationException) {
            // Отмена предыдущего обновления — не сбой каталога: иначе экран показывал
            // «Не удалось прочитать каталог: StandaloneCoroutine was cancelled».
            throw cancelled
        } catch (error: Throwable) {
            HomeUiState.Error("Не удалось прочитать каталог: ${error.message ?: "неизвестная ошибка"}")
        }

    private suspend fun loadState(withEpg: Boolean = true): HomeUiState {
        // Замеры этапов: на приставке экран открывался больше десяти секунд, и надо видеть,
        // что именно из шагов съедает время (каталог, EPG, сопоставление или передачи).
        val startedAt = System.currentTimeMillis()
        fun mark(stage: String) {
            android.util.Log.d(
                "SmotrishkaHome",
                "load $stage: ${System.currentTimeMillis() - startedAt} мс",
            )
        }
        val playlist = catalogStore.playlist()
        mark("каталог")
        if (playlist.channels.isEmpty()) {
            return HomeUiState.Empty
        }

        val now = clock.instant()
        val windowFrom = now.minus(WINDOW_BACK_HOURS, ChronoUnit.HOURS)
        val windowTo = now.plus(WINDOW_FORWARD_HOURS, ChronoUnit.HOURS)
        if (!withEpg) {
            // Быстрый проход: каналы без программы передач — список рисуется сразу.
            // Многоточие нужно только когда показать ещё нечего.
            return buildContent(
                playlist = playlist,
                channels = playlist.channels.map { ChannelUi(it, null, null) },
                now = now,
                epgPending = allChannels.isEmpty(),
            )
        }

        // Связи каналов с гидом уже сохранены в базе: не читаем 3147 каналов гида
        // и не сопоставляем названия на каждом открытии экрана.
        val stored = withTimeoutOrNull(EPG_READ_TIMEOUT_MS) { channelEpgStore.links() }.orEmpty()
        // Запоминаем связи: по ним же собирается развёрнутая телепрограмма канала.
        epgLinks = stored
        if (stored.isEmpty()) {
            mark("связи с гидом пусты — показываем каналы, гид догрузится")
            // Первый запуск после обновления: собираем связи в фоне и перестраиваем экран.
            viewModelScope.launch {
                runCatching { channelEpgStore.rebuild(playlist.channels) }
                refresh(force = true)
            }
            return buildContent(
                playlist = playlist,
                channels = playlist.channels.map { ChannelUi(it, null, null) },
                now = now,
                epgPending = true,
            )
        }
        mark("связи с гидом ${stored.size}")
        // Единый порядок поиска гида для всех экранов: сохранённая связь → epgId канала →
        // сопоставление по названию (EpgResolver). Иначе список показывал «Программы нет»
        // там, где программа есть и видна в оверлее телепрограммы (замечание 09.11.2026).
        val links = runCatching {
            EpgResolver.resolve(
                channels = playlist.channels,
                links = stored,
                epgChannels = epgStore.epgChannels(),
            )
        }.getOrDefault(stored)
        // У каталога провайдера часто нет адресов логотипов, а в гиде они есть.
        // Подставляем иконку гида тем каналам, у которых своего логотипа нет.
        val channelsNeedingIcon = playlist.channels.filter { it.logoUrl.isNullOrBlank() }
        val epgIds = channelsNeedingIcon.mapNotNull { links[it.id] }.toSet()
        val icons = runCatching { epgStore.icons(epgIds) }.getOrDefault(emptyMap())
        android.util.Log.d(
            "SmotrishkaHome",
            "логотипы: своих=${playlist.channels.count { !it.logoUrl.isNullOrBlank() }} " +
                "без=${channelsNeedingIcon.size} связанных=${epgIds.size} из гида=${icons.size}",
        )
        val catalogWithIcons = if (icons.isEmpty()) {
            playlist.channels
        } else {
            playlist.channels.map { channel ->
                if (channel.logoUrl.isNullOrBlank()) {
                    val icon = links[channel.id]?.let { icons[it] }
                    if (icon != null) channel.copy(logoUrl = icon) else channel
                } else {
                    channel
                }
            }
        }
        // Одна выборка на всё окно вместо запроса на каждый канал: иначе экран открывался
        // больше десяти секунд (проверено на приставке t950s_be311 05.11.2026).
        val programmesByChannel = withTimeoutOrNull(EPG_READ_TIMEOUT_MS) {
            epgStore.programmesByChannel(windowFrom, windowTo)
        }.orEmpty()
        if (programmesByChannel.isEmpty()) {
            // Не «Программы нет»: гид мог просто не прочитаться из-за занятой базы
            // (идёт импорт). Показываем многоточие и повторим проход. Названия передач
            // из прошлого удачного прохода сохраняем: иначе после выхода из плеера
            // весь список оставался с многоточиями.
            mark("передачи не ответили за ${EPG_READ_TIMEOUT_MS} мс — гид ещё читается")
            val fallback = playlist.channels.map { channel ->
                allChannels.firstOrNull { it.channel.id == channel.id }
                    ?: ChannelUi(channel, null, null)
            }
            return buildContent(
                playlist = playlist,
                channels = fallback,
                now = now,
                // Гид не ответил вовремя: если передачи уже показаны, оставляем их как есть.
                // Многоточие на экране — это видимый «провал» (замечание Александра
                // 09.11.2026): обновление должно проходить незаметно.
                epgPending = allChannels.isEmpty(),
            )
        }
        mark("передачи ${programmesByChannel.size}")
        val withProgramme = catalogWithIcons.map { channel ->
            // Связь из базы, а если её нет — epgId самого канала: иначе у части каналов
            // программа видна в развороте, но в списке стоит «Программы нет».
            val epgId = links[channel.id] ?: channel.epgId
            if (epgId == null) {
                ChannelUi(channel = channel, nowTitle = null, nextTitle = null)
            } else {
                val programmes = programmesByChannel[epgId].orEmpty()
                // Сдвиг гида канала: у части каналов провайдер отдаёт сетку со смещением.
                val shiftSeconds = shiftHours(channel.id.value).toLong() * 60L * 60L
                val at = now.minusSeconds(shiftSeconds)
                val current = programmes.firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
                val next = programmes.firstOrNull { it.start.isAfter(at) }
                ChannelUi(
                    channel = channel,
                    nowTitle = current?.title,
                    nextTitle = next?.title,
                    nowDescription = current?.description,
                    nowStart = current?.start?.plusSeconds(shiftSeconds),
                    nowEnd = current?.end?.plusSeconds(shiftSeconds),
                )
            }
        }

        mark("карточки каналов")
        // Категории собираются из плейлиста, а пустые отбрасываются в buildContent:
        // при языковом фильтре категории без каналов не показываются.
        val categories = buildList {
            add(CategoryUi(id = null, name = "Все каналы"))
            playlist.categories.forEach {
                add(CategoryUi(id = it.id, name = it.name.replaceFirstChar(Char::uppercaseChar)))
            }
        }

        return buildContent(playlist, withProgramme, now, categories)
    }

    /**
     * Собирает состояние экрана из готового списка каналов.
     *
     * Вынесено из [loadState]: тот же хвост используется и быстрым проходом без гида, чтобы
     * список каналов появлялся сразу, а передачи подставлялись следующим проходом.
     *
     * @param playlist каталог;
     * @param channels каналы с передачами или без них;
     * @param now текущее время;
     * @param preparedCategories готовый список категорий, если уже собран.
     */
    private suspend fun buildContent(
        playlist: Playlist,
        channels: List<ChannelUi>,
        now: Instant,
        preparedCategories: List<CategoryUi>? = null,
        // true — гид ещё читается из базы: «Программы нет» в это время не показываем.
        epgPending: Boolean = false,
    ): HomeUiState {
        // Передачи обновляются незаметно: если свежий проход не дал название, оставляем
        // прежнее, а не показываем «…» или «Программы нет» (замечание Александра 09.11.2026).
        allChannels = channels.withProgrammesFrom(allChannels)
            // Языковой фильтр: остаются каналы выбранного языка и каналы без метки
            // (решение Александра 09.11.2026 — каналы без метки не теряются).
            .filter { matchesLanguage(it) }
        // Категории без каналов не показываем: при выбранном языке целые категории
        // отфильтровываются (замечание Александра 09.11.2026).
        val categories = (preparedCategories ?: buildList {
            add(CategoryUi(id = null, name = "Все каналы"))
            playlist.categories.forEach {
                add(CategoryUi(id = it.id, name = it.name.replaceFirstChar(Char::uppercaseChar)))
            }
        }).filter { category ->
            category.id == null || allChannels.any { it.channel.categoryId == category.id }
        }
        // Живое превью включается только для канала, который пользователь уже открыл:
        // канал из истории ставит followLastWatched, явный выбор — confirmPreview.
        // Автоподтверждение первого канала запускало кадр на холодном старте (баг 09.11.2026).
        // Карточка не зависит от раздела: канал остаётся выбранным, даже если его нет в списке.
        val previewChannel = channels.firstOrNull { it.channel.id == selectedChannelId }
        val schedule = if (programmeOpen && previewChannel != null) {
            scheduleFor(previewChannel.channel)
        } else {
            emptyList()
        }

        return HomeUiState.Content(
            section = section,
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            channels = visibleChannels(),
            selectedChannelId = selectedChannelId,
            previewChannel = previewChannel,
            previewVideoChannelId = confirmedVideoChannelId,
            previewVideoChannel = channels.firstOrNull { it.channel.id == confirmedVideoChannelId },
            epgPending = epgPending,
            now = now,
            favorites = favorites,
            unstable = unstable,
            archiveVerified = archiveVerified,
            recent = recent,
            query = query,
            showChannels = showChannels,
            programmeOpen = programmeOpen && previewChannel != null,
            schedule = schedule,
            epgShiftHours = previewChannel?.let { epgShifts[it.channel.id.value] ?: 0 } ?: 0,
        )
    }

    /**
     * Сохраняет известные названия передач из прошлого прохода.
     *
     * Проход, в котором гид не прочитался, не должен обнулять уже показанные названия:
     * иначе после выхода из плеера весь список оказывался с многоточиями.
     */
    private fun List<ChannelUi>.withProgrammesFrom(previous: List<ChannelUi>): List<ChannelUi> =
        map { channel ->
            if (channel.nowTitle != null) {
                channel
            } else {
                val old = previous.firstOrNull { it.channel.id == channel.channel.id }
                if (old?.nowTitle != null) {
                    channel.copy(
                        nowTitle = old.nowTitle,
                        nextTitle = old.nextTitle,
                        nowStart = old.nowStart,
                        nowEnd = old.nowEnd,
                    )
                } else {
                    channel
                }
            }
        }

    private companion object {        // Списку нужна только текущая передача (и следующая): широкое окно в 36 часов
        // возвращало десятки тысяч строк по 3147 каналам гида, и выборка не укладывалась
        // в таймаут — все каналы показывались как «Программы нет» (замер 05.11.2026).
        const val WINDOW_BACK_HOURS = 1L
        const val WINDOW_FORWARD_HOURS = 6L
        const val SCHEDULE_BACK_HOURS = 12L
        const val SCHEDULE_FORWARD_HOURS = 24L
        const val STOP_TIMEOUT_MS = 5_000L

        /** Сколько ждём гид, прежде чем показать каналы без передач. */
        // 2.5 с не хватало: на приставке база 146 МБ, чтение гида не укладывалось,
        // и все каналы показывались без программы («Программы нет»).
        const val EPG_READ_TIMEOUT_MS = 8_000L

        /** Повторы полного прохода, если передачи не прочитались с первого раза. */
        const val EPG_RETRY_ATTEMPTS = 3

        /** Пауза между повторами чтения гида. */
        const val EPG_RETRY_DELAY_MS = 3_000L

        /** Пауза после изменения числа передач: экран не должен пересобираться на каждое. */
        const val EPG_SETTLE_MS = 15_000L

        /** Поиск начинается с двух символов и через паузу в наборе. */
        const val SEARCH_MIN_CHARS = 2
        const val SEARCH_DEBOUNCE_MS = 250L

        /** Задержка выбора канала при прокрутке списка. */
        const val SELECT_DEBOUNCE_MS = 300L

        /** Пауза перед публикацией ленты: состояние успевает устояться после нескольких проходов. */
        const val WATCH_NEXT_DEBOUNCE_MS = 800L

        /** Тег журнала главного экрана. */
        const val LOGTAG = "SmotrishkaHome"
    }
}
