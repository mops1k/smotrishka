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
import com.mops1k.smotrishka.tv.core.data.EpgProvider
import com.mops1k.smotrishka.tv.core.data.NowProgramme
import com.mops1k.smotrishka.tv.core.data.FavoritesStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.RecentChannelsStore
import com.mops1k.smotrishka.tv.core.data.AspectMode
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Programme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 * @param channelEpgStore сохранённые связи каналов с гидом (логотипы панелей);
 * @param settingsStore настройки плеера (буферизация);
 * @param clock текущее время (подменяется в тестах);
 * @param epgProvider единственный вход к телепрограмме;
 * @param savedStateHandle аргументы навигации (id канала).
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val catalogStore: CatalogStore,
    private val channelEpgStore: ChannelEpgStore,
    private val settingsStore: PlayerSettingsStore,
    private val recentChannels: RecentChannelsStore,
    private val favoritesStore: FavoritesStore,
    private val clock: Clock,
    private val epgProvider: EpgProvider,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId = savedStateHandle.get<String>(ARG_CHANNEL_ID)
        .orEmpty()
        .let(Uri::decode)

    /** Начало архивной передачи в секундах эпохи, `NO_START` — прямой эфир. */
    private val startEpochSeconds: Long = savedStateHandle.get<Long>(ARG_START) ?: NO_START

    /**
     * Момент, который играет у зрителя: в архиве — начало выбранной передачи, в эфире — сейчас.
     *
     * Решает [EpgProvider.playbackMoment], а не плеер: по этому моменту «Телепрограмма» ставит
     * фокус на ту передачу, которую человек смотрит (указание Александра 11.11.2026).
     */
    fun playingMoment(): Instant = epgProvider.playbackMoment(
        startEpochSeconds = startEpochSeconds.takeIf { it > NO_START },
        now = clock.instant(),
    )

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)

    /** Состояние экрана. */
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    /** Тик пересчёта телепрограммы: секунды эпохи на момент последнего обновления. */
    private val scheduleTick = MutableStateFlow(0L)

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
    val channelsInScope: Flow<List<ChannelRef>> = liveRows(
        initial = { scopeRefs() },
        enriched = { withProgrammes(scopeRefs()) },
        hasProgramme = { rows -> rows.any { it.programmeEnd != null } },
    )

    /** Каналы категории без передач: дешёвая выборка для соседей и первого кадра панели. */
    private suspend fun scopeRefs(): List<ChannelRef> {
        val category = settingsStore.lastCategory.first()
        val favoriteIds = favoritesStore.channels.first()
        return ChannelListScope.resolve(
            channels = catalogStore.playlist().channels,
            category = category,
            favoriteIds = favoriteIds,
            allCategory = PlayerSettingsStore.ALL_CATEGORY,
            favoritesCategory = FAVORITES_CATEGORY,
        )
    }

    /**
     * Живой список оверлея: первое излучение дешёвое, дальше передачи пересчитываются по таймеру,
     * пока экран открыт.
     *
     * Пустая выборка гида не стирает уже показанные передачи: гид мог не прочитаться из-за
     * импорта, и «передач нет» на экране было бы враньём — то же правило, что на главном экране.
     *
     * @param initial дешёвая выборка без передач: панель должна открыться сразу;
     * @param enriched та же выборка с передачами и иконками;
     * @param hasProgramme есть ли в выборке хотя бы одна передача.
     */
    private fun <T> liveRows(
        initial: suspend () -> List<T>,
        enriched: suspend () -> List<T>,
        hasProgramme: (List<T>) -> Boolean,
    ): Flow<List<T>> = flow {
        var shown = initial()
        emit(shown)
        while (true) {
            val fresh = enriched()
            if (!hasProgramme(shown) || hasProgramme(fresh)) {
                shown = fresh
                emit(shown)
            }
            delay(CHANNEL_LIST_REFRESH_MS)
        }
    }.flowOn(Dispatchers.Default)

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
        val icons = epgProvider.epgIcons(epgIds)
        return withoutLogo.mapNotNull { channel ->
            links[channel.id]?.let { icons[it] }?.let { channel.id.value to it }
        }.toMap()
    }

    /**
     * Передачи и иконки только для каналов списка.
     *
     * Считать по всему каталогу (на планшете 3147 каналов) — секунды на каждый вход в плеер,
     * а показываются сотни. Передачу выбирает [EpgProvider.current]: окно и сдвиг гида
     * любого знака считаются в одном месте с главным экраном.
     */
    private suspend fun withProgrammes(refs: List<ChannelRef>): List<ChannelRef> {
        val catalog = catalogStore.playlist().channels
        val ids = refs.mapTo(HashSet()) { it.id }
        val channels = catalog.filter { it.id.value in ids }
        val programmes = epgProvider.current(channels = channels, now = clock.instant())
        val logos = logoUrls(channels)
        return refs.map { ref ->
            val programme = programmes[ChannelId(ref.id)]
            ref.copy(
                programmeTitle = programme?.title,
                programmeDescription = programme?.description,
                programmeStart = programme?.start,
                programmeEnd = programme?.end,
                logoUrl = logos[ref.id] ?: ref.logoUrl,
            )
        }
    }

    /**
     * Соседние каналы по алфавиту: (предыдущий, следующий).
     *
     * Считаются по дешёвой выборке без передач: соседям передача не нужна, а прежний вариант
     * запускал из-за них второй тяжёлый проход по гиду.
     */
    val neighbours: Flow<Pair<String?, String?>> = flow { emit(scopeRefs()) }
        .flowOn(Dispatchers.Default)
        .map { scope ->
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
     * на отдельный экран (указание Александра 05.11.2026). Список пересобирается при каждом
     * [refreshProgramme] — экран зовёт его раз в 20 секунд, иначе к концу передачи OSD
     * показывает её же как «сейчас» (замечание Александра 09.11.2026).
     */
    val schedule: Flow<List<ProgrammeRowUi>> = scheduleTick.map { buildSchedule() }

    /**
     * Просит пересчитать передачу и список телепрограммы.
     *
     * Вызывается экраном по таймеру: время идёт, а состояние плеера само не меняется.
     */
    fun refreshProgramme() {
        viewModelScope.launch { _state.value = buildState() }
        scheduleTick.value = clock.instant().epochSecond
    }

    /** Список передач канала на всё окно архива: столько же, сколько на экране программы. */
    private suspend fun buildSchedule(): List<ProgrammeRowUi> {
        val channel = catalogStore.playlist().channels
            .firstOrNull { it.id.value == channelId }
        android.util.Log.d(
            "SmotrishkaGuide",
            "старт: channelId=$channelId найден=${channel != null} имя='${channel?.name.orEmpty()}'",
        )
        if (channel == null) {
            return emptyList()
        }
        val now = clock.instant()
        val depthHours = settingsStore.archiveDepthHours.first()
        // Такое же окно, как на экране «Телепрограмма»: глубина архива назад и сутки вперёд.
        val backHours = maxOf(SCHEDULE_BACK_HOURS, depthHours.toLong())
        // Сдвиг гида и окно считает провайдер — тот же вход, что у списков каналов.
        val programmes = epgProvider.schedule(
            channel = channel,
            from = now.minus(backHours, ChronoUnit.HOURS),
            to = now.plus(SCHEDULE_FORWARD_HOURS, ChronoUnit.HOURS),
            now = now,
        )
        // Диагностика: видно, с каким каналом гида сопоставлен канал и что считается текущим.
        logGuideMatch(channel, programmes)
        return ProgrammeRows.build(
            programmes = programmes,
            now = now,
            channelHasArchive = channel.archive != null,
        )
    }

    /**
     * Диагностика сопоставления канала с гидом.
     *
     * Пишет в журнал, с каким каналом гида сопоставлен канал каталога и какая передача
     * считается текущей. Нужно, чтобы отличить чужую сетку передач от сдвига времени.
     */
    private suspend fun logGuideMatch(channel: Channel, programmes: List<Programme>) {
        val epgId = runCatching { epgProvider.epgId(channel, clock.instant()) }.getOrNull()
        val epgName = epgId?.let { epgProvider.epgName(it) }
        val at = clock.instant()
        val current = programmes.firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
        val head = programmes.take(3).joinToString(" | ") { "${it.title} ${it.start}..${it.end}" }
        val tail = programmes.takeLast(3).joinToString(" | ") { "${it.title} ${it.start}..${it.end}" }
        android.util.Log.d(
            "SmotrishkaGuide",
            "канал='${channel.name}' epgId=${epgId ?: "нет"} гид='${epgName.orEmpty()}' " +
                "всего=${programmes.size} сдвиг=${epgProvider.shiftSeconds(channel.id.value)} " +
                "текущая='${current?.title.orEmpty()}' " +
                "начало=${current?.start} конец=${current?.end} сейчас=$at " +
                "первые=[$head] последние=[$tail]",
        )
    }

    /** Режим кадра этого канала: у каждого канала свой, по умолчанию — общая настройка. */
    /** Ключ режима кадра: канал хранит свой режим отдельно от остальных. */
    private val aspectItemKey: String = "channel:$channelId"
    val aspectMode: Flow<AspectMode> = settingsStore.aspectMode(aspectItemKey)

    /** Недавние каналы: свежие первыми, с текущей передачей и её описанием, как в списке каналов. */
    val recents: Flow<List<RecentChannelRef>> = recentChannels.channels.transform { ids ->
        val catalog = catalogStore.playlist().channels
        val byId = catalog.associateBy { it.id.value }
        val items = ids.mapNotNull { id -> byId[id] }
        // Сначала только названия: панель не должна ждать гид. Архив известен сразу —
        // он приходит из плейлиста, а не из гида.
        emit(items.map { it.toRecentRef() })
        // Тот же провайдер, что у главного экрана и оверлея «Каналы»: окно и сдвиг — в одном месте.
        val programmes = epgProvider.current(channels = items, now = clock.instant())
        val logos = logoUrls(items)
        emit(
            items.map { channel ->
                channel.toRecentRef(
                    logoUrl = channel.logoUrl?.takeIf { it.isNotBlank() } ?: logos[channel.id.value],
                    programme = programmes[channel.id],
                )
            },
        )
    }

    /**
     * Строка панели «Недавние».
     *
     * @param logoUrl логотип для показа (каталог, иначе иконка гида);
     * @param programme текущая передача канала, `null` — гид её не знает.
     */
    private fun Channel.toRecentRef(
        logoUrl: String? = this.logoUrl?.takeIf { it.isNotBlank() },
        programme: NowProgramme? = null,
    ) = RecentChannelRef(
        id = id.value,
        name = name,
        logoUrl = logoUrl,
        hasArchive = archive != null,
        programmeTitle = programme?.title,
        programmeDescription = programme?.description,
        programmeStart = programme?.start,
        programmeEnd = programme?.end,
    )

    /** Переключает режим кадра по кругу и запоминает выбор. */
    fun cycleAspectMode(current: AspectMode) {
        viewModelScope.launch { settingsStore.setAspectMode(aspectItemKey, AspectModes.next(current)) }
    }


    /** Режим эфира: прямой поток провайдера или собственный буфер. */
    val directStream: kotlinx.coroutines.flow.Flow<Boolean> = settingsStore.directStream

    val videoQuality: StateFlow<VideoQuality> = settingsStore.videoQuality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = VideoQuality.AUTO,
    )

    /** Потолок паузы между попытками восстановления потока, секунды. */
    val recoveryDelaySeconds: StateFlow<Int> = settingsStore.recoveryDelaySeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = PlayerSettingsStore.DEFAULT_RECOVERY_DELAY_SECONDS,
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

    /**
     * Передача на момент: и «сейчас», и архивный выбор.
     *
     * Сдвиг гида, окно поиска и границы передачи считает [NowProgrammeProvider] — то же место,
     * что у списков каналов. Раньше архивная передача искалась без поправки на сдвиг, и в шапке
     * плеера стояло другое название (замечание Александра 11.11.2026).
     */
    private suspend fun programmeAt(channel: Channel, at: Instant) = epgProvider.at(channel, at)

    /** Передача, которая идёт прямо сейчас по этому каналу. */
    private suspend fun currentProgramme(channel: Channel) =
        epgProvider.at(channel, clock.instant())

    /** Сдвиг гида канала в секундах: свой важнее общего (решение Александра 09.11.2026). */
    private suspend fun epgShiftSeconds(channel: Channel): Long =
        epgProvider.shiftSeconds(channel.id.value)
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

        /** Окно телепрограммы в OSD: столько же, сколько на экране программы. */
        private const val SCHEDULE_BACK_HOURS = 12L
        private const val SCHEDULE_FORWARD_HOURS = 24L

        /** Как часто обновляем передачу в OSD. */
        private const val PROGRAMME_TICK_MS = 20_000L

        /** Как часто пересчитываем передачи в оверлеях, пока экран открыт. */
        private const val CHANNEL_LIST_REFRESH_MS = 20_000L
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
