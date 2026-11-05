package com.mops1k.smotrishka.tv.ui.player

import androidx.lifecycle.SavedStateHandle
import com.mops1k.smotrishka.tv.ui.home.ProgrammeRowUi
import com.mops1k.smotrishka.tv.ui.home.ProgrammeRows
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.mops1k.smotrishka.tv.core.data.AudioLanguage
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.ChannelEpgStore
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.FavoritesStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.RecentChannelsStore
import com.mops1k.smotrishka.tv.core.data.AspectMode
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Programme
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * Экран плеера: берёт канал из каталога, текущую передачу из EPG и настройку буфера.
 *
 * Каталог — единственный источник правды: если канал пропал из плейлиста (например, после
 * смены источника), плеер честно скажет об этом вместо пустого чёрного экрана.
 *
 * @param catalogStore каталог каналов;
 * @param epgStore программа передач;
 * @param epgMatcher сопоставление канала каталога с каналом EPG;
 * @param settingsStore настройки плеера (буферизация);
 * @param clock текущее время (подменяется в тестах);
 * @param savedStateHandle аргументы навигации (id канала).
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val catalogStore: CatalogStore,
    private val epgStore: EpgStore,
    private val epgMatcher: EpgMatcher,
    private val channelEpgStore: ChannelEpgStore,
    private val settingsStore: PlayerSettingsStore,
    private val recentChannels: RecentChannelsStore,
    private val favoritesStore: FavoritesStore,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId = savedStateHandle.get<String>(ARG_CHANNEL_ID)
        .orEmpty()
        .let(Uri::decode)

    /** Начало архивной передачи в секундах эпохи, `NO_START` — прямой эфир. */
    private val startEpochSeconds: Long = savedStateHandle.get<Long>(ARG_START) ?: NO_START

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)

    /** Состояние экрана. */
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _bufferSeconds = MutableStateFlow(PlayerSettingsStore.DEFAULT_BUFFER_SECONDS)

    /** Сколько секунд вперёд держать в буфере (настройка из экрана настроек). */
    val bufferSeconds: StateFlow<Int> = _bufferSeconds.asStateFlow()

    /** Сколько секунд меню плеера остаётся на экране (настраивается). */
    val osdHideSeconds: StateFlow<Int> = settingsStore.osdHideSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_OSD_HIDE_SECONDS,
    )

    /** Потолок качества видео. */
    /** Избранные каналы. */
    val favorites: Flow<Set<String>> = favoritesStore.channels

    /** Переключает канал в избранном. */
    fun toggleFavorite() {
        viewModelScope.launch { favoritesStore.toggle(channelId) }
    }

    /** Каналу нужен собственный буфер: проблема была обнаружена раньше. */
    val bufferPreferred: Flow<Boolean> = settingsStore.bufferedChannels.map { ids ->
        channelId in ids
    }

    /** Запоминает, что этот канал работает через буфер. */
    fun rememberBuffered() {
        viewModelScope.launch { settingsStore.setBufferedChannel(channelId) }
    }

    /** Поток канала нестабилен: провайдер отдаёт его с пропусками. */
    val unstable: Flow<Boolean> = settingsStore.unstableChannels.map { channelId in it }

    /** Помечает канал как нестабильный. */
    fun markUnstable() {
        viewModelScope.launch { settingsStore.setUnstableChannel(channelId) }
    }

    /**
     * Каналы, между которыми ходит плеер: порядок списка в оверлее и порядок переключения.
     *
     * Область та же, что была у переключения: «Избранное» — только избранные, «Все» — весь
     * список, обычная категория — её каналы. Считается [ChannelListScope], чтобы список
     * и переключение не разъехались.
     */
    val channelsInScope: Flow<List<ChannelRef>> = flow {
        val category = settingsStore.lastCategory.first()
        val favoriteIds = favoritesStore.channels.first()
        val catalog = catalogStore.playlist().channels
        val scope = ChannelListScope.resolve(
            channels = catalog,
            category = category,
            favoriteIds = favoriteIds,
            allCategory = PlayerSettingsStore.ALL_CATEGORY,
            favoritesCategory = FAVORITES_CATEGORY,
        )
        // Сразу отдаём список с названиями: панель открывается мгновенно, а передачи
        // и иконки приходят следом (раньше на первом открытии список был пустым).
        emit(scope)
        emit(withProgrammes(scope, catalog))
    }

    /**
     * Дополняет каналы названием текущей передачи.
     *
     * Один запрос на весь список: гида по каналу в отдельности — сотни обращений, и список
     * в OSD открывался бы секундами.
     */
    /**
     * Логотипы каналов по идентификаторам.
     *
     * У каталога провайдера адресов иконок часто нет, а в гиде они есть: берём иконку гида
     * по сохранённой связи каналов. Читаются только нужные иконки.
     */
    private suspend fun logoUrls(channels: List<Channel>): Map<String, String> {
        val withoutLogo = channels.filter { it.logoUrl.isNullOrBlank() }
        if (withoutLogo.isEmpty()) return emptyMap()
        val links = runCatching { channelEpgStore.links() }.getOrDefault(emptyMap())
        val epgIds = withoutLogo.mapNotNull { links[it.id] }.toSet()
        val icons = runCatching { epgStore.icons(epgIds) }.getOrDefault(emptyMap())
        return withoutLogo.mapNotNull { channel ->
            links[channel.id]?.let { icons[it] }?.let { channel.id.value to it }
        }.toMap()
    }

    private suspend fun withProgrammes(
        scope: List<ChannelRef>,
        catalog: List<Channel>,
    ): List<ChannelRef> {
        val titles = programmeTitles(catalog)
        val logos = logoUrls(catalog)
        return scope.map { ref -> ref.copy(programmeTitle = titles[ref.id], logoUrl = logos[ref.id]) }
    }

    /**
     * Названия текущих передач по идентификаторам каналов.
     *
     * Один запрос к гиду на весь набор: по одному запросу на канал список открывался бы
     * секундами, а на панели недавних — тем более.
     */
    private suspend fun programmeTitles(channels: List<Channel>): Map<String, String?> {
        if (channels.isEmpty()) return emptyMap()
        val now = clock.instant()
        // Сохранённые связи каналов с гидом: сопоставление по названиям (чтение всей
        // таблицы гида) выполняем только если связей ещё нет.
        val saved = runCatching { channelEpgStore.links() }.getOrDefault(emptyMap())
        val matches = if (saved.isNotEmpty()) {
            // Если для канала связи нет, берём epgId из самого каталога.
            channels.mapNotNull { channel ->
                (saved[channel.id] ?: channel.epgId)?.let { channel.id to it }
            }.toMap()
        } else {
            epgMatcher.match(channels, epgStore.epgChannels()).matches
        }
        val programmes = epgStore.programmesByChannel(
            from = now.minus(6, ChronoUnit.HOURS),
            to = now.plus(2, ChronoUnit.HOURS),
        )
        return channels.associate { channel ->
            val epgId = matches[channel.id]
            channel.id.value to epgId
                ?.let { programmes[it] }
                ?.firstOrNull { !it.start.isAfter(now) && it.end.isAfter(now) }
                ?.title
        }
    }

    /** Соседние каналы по алфавиту: (предыдущий, следующий). */
    val neighbours: Flow<Pair<String?, String?>> = channelsInScope.map { scope ->
        val index = scope.indexOfFirst { it.id == channelId }
        if (index < 0 || scope.size < 2) {
            null to null
        } else {
            // По кругу: с первого канала вверх — последний, с последнего вниз — первый.
            scope[(index - 1 + scope.size) % scope.size].id to
                scope[(index + 1) % scope.size].id
        }
    }

    /**
     * Телепрограмма текущего канала для OSD-панели.
     *
     * Считается прямо в плеере: панель открывается поверх видео и не должна уводить
     * на отдельный экран (указание Александра 05.11.2026).
     */
    val schedule: Flow<List<ProgrammeRowUi>> = flow {
        val channel = catalogStore.playlist().channels
            .firstOrNull { it.id.value == channelId }
        android.util.Log.d(
            "SmotrishkaGuide",
            "старт: channelId=$channelId найден=${channel != null} имя='${channel?.name.orEmpty()}'",
        )
        if (channel == null) {
            emit(emptyList())
            return@flow
        }
        val now = clock.instant()
        val links = runCatching { channelEpgStore.links() }.getOrDefault(emptyMap())
        val epgId = links[channel.id] ?: channel.epgId
        android.util.Log.d(
            "SmotrishkaGuide",
            "канал='${channel.name}' связей=${links.size} epgId=${epgId ?: "нет"} " +
                "epgId каталога='${channel.epgId ?: "нет"}'",
        )
        val resolved = epgId ?: currentEpgId(channel)
        if (resolved == null) {
            emit(emptyList())
            return@flow
        }
        val programmes = epgStore.programmes(
            channelId = resolved,
            from = now.minus(6, ChronoUnit.HOURS),
            to = now.plus(6, ChronoUnit.HOURS),
        )
        // Ручной сдвиг гида этого канала: у части каналов провайдер отдаёт сетку со смещением.
        val shiftSeconds = (settingsStore.epgShifts.first()[channel.id.value] ?: 0).toLong() * 3600L
        val shifted = if (shiftSeconds == 0L) {
            programmes
        } else {
            programmes.map {
                it.copy(start = it.start.plusSeconds(shiftSeconds), end = it.end.plusSeconds(shiftSeconds))
            }
        }
        // Диагностика: видно, с каким каналом гида сопоставлен канал и что считается текущим.
        logGuideMatch(channel, resolved, programmes)
        emit(
            ProgrammeRows.build(
                programmes = shifted,
                now = now,
                channelHasArchive = channel.archive != null,
            ),
        )
    }

    /**
     * Диагностика сопоставления канала с гидом.
     *
     * Пишет в журнал, с каким каналом гида сопоставлен канал каталога и какая передача
     * считается текущей. Нужно, чтобы отличить чужую сетку передач от сдвига времени.
     */
    private suspend fun logGuideMatch(channel: Channel, epgId: String, programmes: List<Programme>) {
        val epgName = runCatching {
            epgStore.epgChannels().firstOrNull { it.id == epgId }?.displayName
        }.getOrNull()
        val current = programmes.firstOrNull { !it.start.isAfter(clock.instant()) && it.end.isAfter(clock.instant()) }
        android.util.Log.d(
            "SmotrishkaGuide",
            "канал='${channel.name}' epgId=$epgId гид='${epgName.orEmpty()}' " +
                "текущая='${current?.title.orEmpty()}' " +
                "начало=${current?.start} конец=${current?.end} сейчас=${clock.instant()}",
        )
    }

    /** Идентификатор канала в гиде: через сохранённые связи, иначе через сопоставление. */
    private suspend fun currentEpgId(channel: Channel): String? =
        channelEpgStore.links()[channel.id]
            ?: epgMatcher.match(listOf(channel), epgStore.epgChannels()).matches[channel.id]

    /** Режим кадра. */
    val aspectMode: Flow<AspectMode> = settingsStore.aspectMode

    /** Недавние каналы: свежие первыми, с названием текущей передачи, как в списке каналов. */
    val recents: Flow<List<RecentChannelRef>> = recentChannels.channels.transform { ids ->
        val catalog = catalogStore.playlist().channels
        val byId = catalog.associateBy { it.id.value }
        val items = ids.mapNotNull { id -> byId[id] }
        // Сначала только названия: панель не должна ждать гид.
        emit(items.map { RecentChannelRef(id = it.id.value, name = it.name) })
        val titles = programmeTitles(items)
        val logos = logoUrls(items)
        emit(
            items.map { channel ->
                RecentChannelRef(
                    id = channel.id.value,
                    name = channel.name,
                    logoUrl = channel.logoUrl?.takeIf { it.isNotBlank() } ?: logos[channel.id.value],
                    programmeTitle = titles[channel.id.value],
                )
            },
        )
    }

    /** Переключает режим кадра по кругу и запоминает выбор. */
    fun cycleAspectMode(current: AspectMode) {
        viewModelScope.launch { settingsStore.setAspectMode(AspectModes.next(current)) }
    }

    /** Режим эфира: прямой поток провайдера или собственный буфер. */
    val directStream: kotlinx.coroutines.flow.Flow<Boolean> = settingsStore.directStream

    val videoQuality: StateFlow<VideoQuality> = settingsStore.videoQuality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = VideoQuality.AUTO,
    )

    /** Сколько раз пытаться восстановить поток. */
    val retryAttempts: StateFlow<Int> = settingsStore.retryAttempts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_RETRY_ATTEMPTS,
    )

    /** Предпочитаемый язык звука. */
    val audioLanguage: StateFlow<AudioLanguage> = settingsStore.audioLanguage.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AudioLanguage.RUSSIAN,
    )

    init {
        viewModelScope.launch { _state.value = buildState() }
        viewModelScope.launch { settingsStore.bufferSeconds.collect { _bufferSeconds.value = it } }
        // Канал попадает в «недавние» в момент открытия, а не при наведении фокуса.
        viewModelScope.launch { recentChannels.remember(channelId) }
    }

    /**
     * Перепроверяет передачу в OSD.
     *
     * Вызывается с экрана по таймеру: бесконечный цикл во ViewModel зависал в тестах с
     * виртуальным планировщиком.
     */
    fun refreshProgramme() {
        viewModelScope.launch { _state.value = buildState() }
    }

    private suspend fun buildState(): PlayerUiState = runCatching { loadState() }.getOrElse { error ->
        PlayerUiState.Error("Не удалось открыть канал: ${error.message ?: "неизвестная ошибка"}")
    }

    private suspend fun loadState(): PlayerUiState {
        if (channelId.isBlank()) {
            return PlayerUiState.Error("Канал не выбран")
        }

        val channel = catalogStore.playlist().channels.firstOrNull { it.id == ChannelId(channelId) }
            ?: return PlayerUiState.Error("Канал не найден в плейлисте — обновите источник в настройках")

        val isArchive = startEpochSeconds > NO_START
        // Для архивной передачи в OSD должна быть именно она, а не текущий эфир.
        val programme = if (isArchive) {
            programmeAt(channel, Instant.ofEpochSecond(startEpochSeconds))
        } else {
            currentProgramme(channel)
        }

        if (isArchive && channel.archive == null) {
            return PlayerUiState.Error("У этого канала нет архива")
        }

        val streamUrl = if (isArchive) {
            archiveStreamUrl(
                base = channel.streamUrl.substringBefore('?'),
                start = Instant.ofEpochSecond(startEpochSeconds),
            )
        } else {
            channel.streamUrl
        }

        return PlayerUiState.Content(
            channelId = channel.id,
            title = channel.name,
            streamUrl = streamUrl,
            logoUrl = channel.logoUrl,
            programmeTitle = programme?.title,
            programmeStart = programme?.start,
            programmeEnd = programme?.end,
            archiveAvailable = channel.archive != null,
            isArchive = isArchive,
        )
    }

    /** Передача, в которую попадает момент времени. */
    private suspend fun programmeAt(channel: Channel, at: Instant): Programme? {
        val matches = epgMatcher.match(listOf(channel), epgStore.epgChannels()).matches
        val epgId = matches[channel.id] ?: return null
        return epgStore.programmes(
            channelId = epgId,
            from = at.minus(12, ChronoUnit.HOURS),
            to = at.plus(12, ChronoUnit.HOURS),
        ).firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
    }

    /** Передача, которая идёт прямо сейчас по этому каналу. */
    private suspend fun currentProgramme(channel: Channel): Programme? {        val matches = epgMatcher.match(listOf(channel), epgStore.epgChannels()).matches
        val epgId = matches[channel.id] ?: return null

        val now = clock.instant()
        return epgStore.programmes(
            channelId = epgId,
            from = now.minus(24, ChronoUnit.HOURS),
            to = now.plus(24, ChronoUnit.HOURS),
        ).firstOrNull { !it.start.isAfter(now) && it.end.isAfter(now) }
    }

    companion object {
        /** Ключ категории «Избранное» (совпадает с HomeViewModel.FAVORITES_ID). */
        private const val FAVORITES_CATEGORY = "\u0001favorites"

        /** Имя аргумента навигации с id канала. */
        const val ARG_CHANNEL_ID = "channelId"

        /** Имя аргумента навигации с началом архивной передачи. */
        const val ARG_START = "start"

        /** Значение [ARG_START] при обычном (прямом) эфире. */
        const val NO_START = -1L

        private const val WINDOW_HOURS = 24L
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Как часто обновляем передачу в OSD. */
        private const val PROGRAMME_TICK_MS = 20_000L
    }

    /** Адрес архивного потока: провайдер ждёт `utc` (начало) и `lutc` (сейчас). */
    /**
     * Адрес архивной передачи.
     *
     * Контракт провайдера (проверено по запросу рабочего плеера): `utc` — начало окна,
     * `lutc` — текущий момент. Без `lutc` провайдер отдаёт прямой эфир вместо архива.
     */
    private fun archiveStreamUrl(base: String, start: Instant): String {
        // Адрес должен быть стабильным: lutc подставляется в момент запроса в ArchiveRecorder,
        // иначе каждое обновление программы меняло streamUrl и экран перезапускал архив.
        val separator = if (base.contains('?')) '&' else '?'
        return "$base${separator}utc=${start.epochSecond}"
    }
}
