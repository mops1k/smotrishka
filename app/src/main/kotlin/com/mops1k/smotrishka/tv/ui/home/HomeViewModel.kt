package com.mops1k.smotrishka.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.FavoritesStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.RecentChannelsStore
import com.mops1k.smotrishka.tv.core.data.SyncStatusStore
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Главный экран: категории, каналы и «сейчас / далее» из программы передач.
 *
 * Программа подтягивается по сопоставлению каналов с EPG; каналы без сопоставления
 * остаются в списке, но без программы — пользователь видит честную разницу.
 *
 * @param catalogStore каталог каналов;
 * @param epgStore программа передач;
 * @param epgMatcher сопоставление каналов каталога и EPG;
 * @param clock текущее время (подменяется в тестах).
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

    /**
     * Фоновая догрузка программы передач: каналы уже доступны, гид приходит позже.
     */
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

    private var selectedCategoryId: String? = null
    private var selectedChannelId: ChannelId? = null
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

    private var showChannels: Boolean = false

    /** Каналы со программой из последней загрузки: категория фильтруется из них, без чтения базы. */
    private var allChannels: List<ChannelUi> = emptyList()

    /** Признак, что сохранённая категория и избранное прочитаны. */
    private val restoreDone = kotlinx.coroutines.CompletableDeferred<Unit>()

    /** Избранные каналы: отдельная категория в самом верху. */
    private var favorites: Set<String> = emptySet()

    /** Каналы с нестабильным потоком. */
    private var unstable: Set<String> = emptySet()

    /** Каналы с проверенным архивом. */
    private var archiveVerified: Set<String> = emptySet()

    init {
        // Гид догружается в фоне: как только передачи появились в базе, перестраиваем список,
        // иначе программа видна только после перезапуска приложения.
        viewModelScope.launch {
            epgStore.programmeCount
                .debounce(2_000)
                .collect { refresh(force = true) }
        }
        // Возвращаемся в категорию, где пользователь был в прошлый раз: после запуска сразу
        // открыт её список каналов, но вернуться к категориям можно как обычно.
        viewModelScope.launch {
            // Избранное читаем сразу, до первой сборки состояния: иначе при восстановлении
            // категории «Избранное» список каналов оказывался пустым до следующего захода.
            favorites = runCatching { favoritesStore.channels.first() }.getOrDefault(favorites)
            unstable = runCatching { settingsStore.unstableChannels.first() }.getOrDefault(emptySet())
            archiveVerified = runCatching { settingsStore.archiveVerified.first() }.getOrDefault(emptySet())
            // Проверка архива идёт фоном: обновляем список, как только пришёл результат.
            launch {
                settingsStore.archiveVerified.collect { verified ->
                    archiveVerified = verified
                    val content = _state.value
                    if (content is HomeUiState.Content) {
                        _state.value = content.copy(archiveVerified = verified)
                    }
                }
            }
            // После возврата из плеера список должен стоять на канале, который только что
            // смотрели: он же последний в «недавних».
            recentChannels.channels.first().firstOrNull()?.let { selectedChannelId = ChannelId(it) }
            val saved = settingsStore.lastCategory.first()
            if (saved != null && saved.isNotEmpty()) {
                selectedCategoryId = if (saved == PlayerSettingsStore.ALL_CATEGORY) null else saved
                showChannels = true
            }
            // Состояние соберёт первый refresh() с экрана, но только после этого чтения:
            // иначе список «Избранного» успевал отрисоваться пустым.
            restoreDone.complete(Unit)
        }
        viewModelScope.launch {
            favoritesStore.channels.collect { set ->
                favorites = set
                refreshFavorites()
            }
        }
    }

    /**
     * Переключает канал в избранном.
     *
     * Состояние меняется сразу (оптимистично), а запись в DataStore идёт следом: иначе сердце
     * нажимается мгновенно, а канал в категории «Избранное» появляется с задержкой.
     */
    fun toggleFavorite(channelId: ChannelId) {
        val id = channelId.value
        favorites = if (id in favorites) favorites - id else favorites + id
        refreshFavorites()
        viewModelScope.launch { favoritesStore.toggle(id) }
    }

    /** Пересобирает категории и видимый список после изменения избранного. */
    private fun refreshFavorites() {
        val current = _state.value
        if (current !is HomeUiState.Content) return
        _state.value = current.copy(
            favorites = favorites,
            unstable = unstable,
            archiveVerified = archiveVerified,
            categories = categoriesWithFavorites(current.categories),
            channels = if (selectedCategoryId == FAVORITES_ID) {
                favoritesChannels()
            } else {
                current.channels
            },
        )
    }

    private fun categoriesWithFavorites(base: List<CategoryUi>): List<CategoryUi> =
        listOf(CategoryUi(id = FAVORITES_ID, name = "Избранное")) +
            base.filterNot { it.id == FAVORITES_ID }

    private fun favoritesChannels(): List<ChannelUi> =
        allChannels.filter { it.channel.id.value in favorites }
            .sortedBy { it.channel.name.lowercase() }
    private var selectJob: Job? = null

    /** Время последнего обновления данных, которое уже показано на экране. */
    private var shownSyncAt: Long? = null

    init {
        viewModelScope.launch { shownSyncAt = syncStatusStore.last()?.atMillis }
        refresh()
    }

    /**
     * Перечитывает каталог и программу передач.
     *
     * Экран «Загружаем каналы…» показывается только когда показывать ещё нечего: при возврате
     * из плеера или с настроек список остаётся на месте. Перестройка списка (а это запросы
     * программы по тысячам каналов) делается только если данные действительно обновились.
     */
    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            // Ждём восстановления настроек: без этого первая отрисовка «Избранного» пустая.
            restoreDone.await()
            // После возврата из плеера выделяем канал, который смотрели последним.
            val lastWatched = runCatching { recentChannels.channels.first().firstOrNull() }.getOrNull()
            if (lastWatched != null && lastWatched != selectedChannelId?.value) {
                selectedChannelId = ChannelId(lastWatched)
                val content = _state.value
                if (content is HomeUiState.Content) {
                    _state.value = content.copy(
                        selectedChannelId = selectedChannelId,
                        previewChannel = allChannels.firstOrNull { it.channel.id.value == lastWatched }
                            ?: content.previewChannel,
                    )
                }
            }
            val syncAt = syncStatusStore.last()?.atMillis
            if (!force && _state.value is HomeUiState.Content && syncAt == shownSyncAt) return@launch

            shownSyncAt = syncAt
            if (_state.value !is HomeUiState.Content) {
                _state.value = HomeUiState.Loading
            }
            _state.value = buildState()
        }
    }

    /**
     * Выбирает категорию и открывает её каналы.
     *
     * @param categoryId ключ категории, `null` — «Все».
     */
    fun selectCategory(categoryId: String?) {
        selectedCategoryId = categoryId
        showChannels = true
        viewModelScope.launch { settingsStore.setLastCategory(categoryId) }
        val current = _state.value
        if (current is HomeUiState.Content && allChannels.isNotEmpty()) {
            // Переключаем категорию из уже загруженного каталога: без повторного чтения базы и
            // повторного разбора программы — иначе переход в список каналов заметно тормозит.
            // «Избранное» — не категория канала, а набор отмеченных: фильтр по идентификатору
            // категории его бы не нашёл, из-за чего список выглядел пустым.
            val filtered = when {
                categoryId == FAVORITES_ID -> allChannels.filter { it.channel.id.value in favorites }
                categoryId == null -> allChannels
                else -> allChannels.filter { it.channel.categoryId == categoryId }
            }.sortedBy { it.channel.name.lowercase() }
            _state.value = current.copy(
                selectedCategoryId = categoryId,
                channels = filtered,
                showChannels = true,
            )
            return
        }
        viewModelScope.launch { _state.value = buildState() }
    }

    /**
     * Возвращает к списку категорий.
     *
     * Состояние живёт в модели, а не в Compose: возврат из плеера должен попадать в список
     * каналов с тем же выбором, а не начинать с категорий.
     */
    fun showCategories() {
        showChannels = false
        val current = _state.value
        if (current is HomeUiState.Content) {
            _state.value = current.copy(showChannels = false)
        }
    }

    /**
     * Выбирает канал для превью.
     *
     * Смена откладывается на несколько сотен миллисекунд: при быстрой прокрутке списка фокус
     * проскакивает строки, и без задержки превью дёргалось бы на каждом канале.
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
                // Превью держим в состоянии вместе с выбором: иначе после возврата из плеера
                // оно пустое, хотя канал выбран.
                val preview = current.previewChannel
                    ?.takeIf { it.channel.id == channelId }
                    ?: current.channels.firstOrNull { it.channel.id == channelId }
                _state.value = current.copy(
                    selectedChannelId = channelId,
                    previewChannel = preview,
                )
            }
        }
    }

    private suspend fun buildState(): HomeUiState = runCatching { loadState() }.getOrElse { error ->
        HomeUiState.Error("Не удалось прочитать каталог: ${error.message ?: "неизвестная ошибка"}")
    }

    private suspend fun loadState(): HomeUiState {        val playlist = catalogStore.playlist()
        if (playlist.channels.isEmpty()) {
            return HomeUiState.Empty
        }

        val now = clock.instant()
        val matches = epgMatcher.match(playlist.channels, epgStore.epgChannels()).matches
        val withProgramme = playlist.channels.map { channel ->
            val epgId = matches[channel.id]
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

        val categories = buildList {
            add(CategoryUi(id = FAVORITES_ID, name = "Избранное"))
            add(CategoryUi(id = null, name = "Все"))
            // В плейлистах названия категорий часто со строчной буквы — показываем с заглавной.
            playlist.categories.forEach {
                add(CategoryUi(id = it.id, name = it.name.replaceFirstChar(Char::uppercaseChar)))
            }
        }

        // Каналы по умолчанию идут по названию: так проще находить нужный.
        allChannels = withProgramme
        // «Избранное» — набор отмеченных каналов, а не категория канала: фильтр по categoryId
        // давал пустой список после восстановления категории при запуске.
        val visible = when {
            selectedCategoryId == FAVORITES_ID -> withProgramme.filter { it.channel.id.value in favorites }
            selectedCategoryId == null -> withProgramme
            else -> withProgramme.filter { it.channel.categoryId == selectedCategoryId }
        }.sortedBy { it.channel.name.lowercase() }

        // Превью не зависит от выбранной категории: канал остаётся тем же, даже если его
        // нет в списке новой категории.
        val previewChannel = withProgramme.firstOrNull { it.channel.id == selectedChannelId }

        return HomeUiState.Content(
            categories = categories,
            selectedCategoryId = selectedCategoryId,
            channels = visible,
            selectedChannelId = selectedChannelId,
            previewChannel = previewChannel,
            showChannels = showChannels,
            now = now,
            favorites = favorites,
            unstable = unstable,
            archiveVerified = archiveVerified,
        )
    }

    private companion object {
        const val WINDOW_BACK_HOURS = 12L
        const val WINDOW_FORWARD_HOURS = 24L
        const val STOP_TIMEOUT_MS = 5_000L

        /** Ключ категории «Избранное». */
        const val FAVORITES_ID = "\u0001favorites"

        /** Как часто обновляем время на экране. */
        const val CLOCK_TICK_MS = 20_000L

        /** Задержка выбора канала при прокрутке списка. */
        const val SELECT_DEBOUNCE_MS = 300L
    }
}
