package com.mops1k.smotrishka.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.FavoritesStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.RecentChannelsStore
import com.mops1k.smotrishka.tv.core.data.SyncStatusStore
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import com.mops1k.smotrishka.tv.epg.EpgUpdateState
import com.mops1k.smotrishka.tv.epg.EpgUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
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
 * @param epgUpdater фоновая догрузка программы передач.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val catalogStore: CatalogStore,
    private val epgStore: EpgStore,
    private val epgMatcher: EpgMatcher,
    private val settingsStore: PlayerSettingsStore,
    private val favoritesStore: FavoritesStore,
    private val recentChannels: RecentChannelsStore,
    private val syncStatusStore: SyncStatusStore,
    private val clock: Clock,
    private val epgUpdater: EpgUpdater,
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

    /** Строка поиска: фильтр по названию канала. */
    private var query: String = ""

    /** Развёрнута ли телепрограмма в карточке. */
    private var programmeOpen: Boolean = false

    /** Каналы последней загрузки: фильтрация идёт из них, без чтения базы. */
    private var allChannels: List<ChannelUi> = emptyList()

    /** Сопоставление каналов с EPG из последней загрузки. */
    private var epgMatches: Map<ChannelId, String> = emptyMap()

    /** Избранные каналы. */
    private var favorites: Set<String> = emptySet()

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
        viewModelScope.launch {
            epgStore.programmeCount
                .debounce(2_000)
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
            recentChannels.channels.first().firstOrNull()?.let { selectedChannelId = ChannelId(it) }
            val saved = settingsStore.lastCategory.first()
            if (saved != null && saved.isNotEmpty()) {
                selectedCategoryId = if (saved == PlayerSettingsStore.ALL_CATEGORY) null else saved
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
        section = newSection
        selectedCategoryId = null
        programmeOpen = false
        viewModelScope.launch { settingsStore.setLastCategory(null) }
        val current = _state.value
        if (current is HomeUiState.Content && allChannels.isNotEmpty()) {
            _state.value = current.copy(
                section = section,
                selectedCategoryId = null,
                channels = visibleChannels(),
                programmeOpen = false,
                schedule = emptyList(),
            )
            return
        }
        viewModelScope.launch { _state.value = buildState() }
    }

    /**
     * Выбирает категорию.
     *
     * @param categoryId ключ категории, `null` — «Все каналы».
     */
    fun selectCategory(categoryId: String?) {
        selectedCategoryId = categoryId
        viewModelScope.launch { settingsStore.setLastCategory(categoryId) }
        val current = _state.value
        if (current is HomeUiState.Content && allChannels.isNotEmpty()) {
            _state.value = current.copy(
                selectedCategoryId = categoryId,
                channels = visibleChannels(),
            )
            return
        }
        viewModelScope.launch { _state.value = buildState() }
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
    }

    /** Закрывает поиск и сбрасывает фильтр вместе с запросом. */
    fun closeSearch() {
        if (query.isEmpty()) return
        query = ""
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
     * Обновляет время на экране.
     *
     * Вызывается с экрана по таймеру, а не бесконечным циклом во ViewModel: иначе тесты с
     * виртуальным планировщиком крутятся вечно и сборка зависает.
     */
    fun tick() {
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
    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            restoreDone.await()
            // После возврата из плеера карточка показывает канал, который смотрели последним.
            val lastWatched = runCatching { recentChannels.channels.first().firstOrNull() }.getOrNull()
            if (lastWatched != null && lastWatched != selectedChannelId?.value) {
                selectedChannelId = ChannelId(lastWatched)
            }
            val syncAt = syncStatusStore.last()?.atMillis
            if (!force && _state.value is HomeUiState.Content && syncAt == shownSyncAt) {
                refreshVisible()
                return@launch
            }

            shownSyncAt = syncAt
            if (_state.value !is HomeUiState.Content) {
                _state.value = HomeUiState.Loading
            }
            _state.value = buildState()
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
    private fun visibleChannels(): List<ChannelUi> {
        val base = when (section) {
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
        val epgId = epgMatches[channel.id] ?: channel.epgId ?: return emptyList()
        val now = clock.instant()
        val programmes = epgStore.programmes(
            channelId = epgId,
            from = now.minus(SCHEDULE_BACK_HOURS, ChronoUnit.HOURS),
            to = now.plus(SCHEDULE_FORWARD_HOURS, ChronoUnit.HOURS),
        )
        return ProgrammeRows.build(
            programmes = programmes,
            now = now,
            channelHasArchive = channel.hasArchive(),
        )
    }

    /**
     * Доступен ли архив канала.
     *
     * Заявленный провайдером архив считается доступным, пока фоновая проверка не сказала
     * обратное: пустой набор проверки означает «ещё не проверяли».
     */
    private fun Channel.hasArchive(): Boolean =
        archive != null && (archiveVerified.isEmpty() || id.value in archiveVerified)

    private suspend fun buildState(): HomeUiState = runCatching { loadState() }.getOrElse { error ->
        HomeUiState.Error("Не удалось прочитать каталог: ${error.message ?: "неизвестная ошибка"}")
    }

    private suspend fun loadState(): HomeUiState {
        val playlist = catalogStore.playlist()
        if (playlist.channels.isEmpty()) {
            return HomeUiState.Empty
        }

        val now = clock.instant()
        epgMatches = epgMatcher.match(playlist.channels, epgStore.epgChannels()).matches
        val withProgramme = playlist.channels.map { channel ->
            val epgId = epgMatches[channel.id]
            if (epgId == null) {
                ChannelUi(channel = channel, nowTitle = null, nextTitle = null)
            } else {
                val programmes = epgStore.programmes(
                    channelId = epgId,
                    from = now.minus(WINDOW_BACK_HOURS, ChronoUnit.HOURS),
                    to = now.plus(WINDOW_FORWARD_HOURS, ChronoUnit.HOURS),
                )
                val current = programmes.firstOrNull { !it.start.isAfter(now) && it.end.isAfter(now) }
                val next = programmes.firstOrNull { it.start.isAfter(now) }
                ChannelUi(
                    channel = channel,
                    nowTitle = current?.title,
                    nextTitle = next?.title,
                    nowStart = current?.start,
                    nowEnd = current?.end,
                )
            }
        }

        // «Избранное» и «Недавние» — разделы, а не категории: в плашке их нет.
        val categories = buildList {
            add(CategoryUi(id = null, name = "Все каналы"))
            // В плейлистах названия категорий часто со строчной буквы — показываем с заглавной.
            playlist.categories.forEach {
                add(CategoryUi(id = it.id, name = it.name.replaceFirstChar(Char::uppercaseChar)))
            }
        }

        allChannels = withProgramme
        // Карточка не зависит от раздела: канал остаётся выбранным, даже если его нет в списке.
        val previewChannel = withProgramme.firstOrNull { it.channel.id == selectedChannelId }
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
            now = now,
            favorites = favorites,
            unstable = unstable,
            archiveVerified = archiveVerified,
            recent = recent,
            query = query,
            programmeOpen = programmeOpen && previewChannel != null,
            schedule = schedule,
        )
    }

    private companion object {
        const val WINDOW_BACK_HOURS = 12L
        const val WINDOW_FORWARD_HOURS = 24L
        const val SCHEDULE_BACK_HOURS = 12L
        const val SCHEDULE_FORWARD_HOURS = 24L
        const val STOP_TIMEOUT_MS = 5_000L

        /** Задержка выбора канала при прокрутке списка. */
        const val SELECT_DEBOUNCE_MS = 300L
    }
}
