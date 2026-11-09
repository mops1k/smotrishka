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
import com.mops1k.smotrishka.tv.core.playlist.EpgResolver
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
        val programmes = nowProgrammes(catalog)
        val logos = logoUrls(catalog)
        return scope.map { ref ->
            val programme = programmes[ref.id]
            ref.copy(
                programmeTitle = programme?.title,
                programmeDescription = programme?.description,
                programmeStart = programme?.start,
                programmeEnd = programme?.end,
                logoUrl = logos[ref.id],
            )
        }
    }

    /**
     * Текущие передачи по идентификаторам каналов.
     *
     * Один запрос к гиду на весь набор: по одному запросу на канал список открывался бы
     * секундами, а на панели недавних — тем более. Границы передачи показываются со сдвигом
     * гида канала, как в OSD и на главном экране: иначе полоса прогресса и подпись «до HH:MM»
     * разъезжаются с тем, что человек видит в эфире.
     */
    private suspend fun nowProgrammes(channels: List<Channel>): Map<String, NowProgramme> {
        if (channels.isEmpty()) return emptyMap()
        val now = clock.instant()
        // Тот же порядок поиска гида, что на всех экранах: сохранённая связь → epgId канала →
        // сопоставление по названию (EpgResolver). Иначе оверлей каналов оставлял канал
        // без передачи там, где она есть в оверлее телепрограммы.
        val matches = runCatching {
            EpgResolver.resolve(
                channels = channels,
                links = channelEpgStore.links(),
                epgChannels = epgStore.epgChannels(),
                matcher = epgMatcher,
            )
        }.getOrDefault(emptyMap())
        val shifts = channelShifts(channels)
        // Окно расширяем на максимальный сдвиг: у канала со сдвигом «сейчас» уезжает в сторону,
        // и передача, идущая в этот момент, иначе не попадёт в выборку.
        val maxShift = shifts.values.maxOrNull()?.coerceAtLeast(0L) ?: 0L
        val programmes = epgStore.programmesByChannel(
            from = now.minus(6, ChronoUnit.HOURS).minusSeconds(maxShift),
            to = now.plus(2, ChronoUnit.HOURS),
        )
        return channels.mapNotNull { channel ->
            val epgId = matches[channel.id] ?: return@mapNotNull null
            val shiftSeconds = shifts[channel.id.value] ?: 0L
            val at = now.minusSeconds(shiftSeconds)
            val current = programmes[epgId]
                ?.firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
                ?: return@mapNotNull null
            channel.id.value to NowProgramme(
                title = current.title,
                description = current.description,
                start = current.start.plusSeconds(shiftSeconds),
                end = current.end.plusSeconds(shiftSeconds),
            )
        }.toMap()
    }

    /**
     * Сдвиги гида каналов в секундах.
     *
     * Свой сдвиг канала важнее общего (решение Александра 09.11.2026) — то же правило,
     * что у OSD и списка каналов.
     */
    private suspend fun channelShifts(channels: List<Channel>): Map<String, Long> {
        val own = runCatching { settingsStore.epgShifts.first() }.getOrDefault(emptyMap())
        val global = runCatching { settingsStore.globalEpgShift.first() }.getOrDefault(0)
        return channels.associate { channel ->
            channel.id.value to (own[channel.id.value] ?: global).toLong() * 60L * 60L
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
        val resolved = resolveEpg(channel)
        android.util.Log.d(
            "SmotrishkaGuide",
            "канал='${channel.name}' epgId=${resolved ?: "нет"} " +
                "epgId каталога='${channel.epgId ?: "нет"}'",
        )
        if (resolved == null) return emptyList()
        val now = clock.instant()
        val depthHours = settingsStore.archiveDepthHours.first()
        // Такое же окно, как на экране «Телепрограмма»: глубина архива назад и сутки вперёд.
        val backHours = maxOf(SCHEDULE_BACK_HOURS, depthHours.toLong())
        val programmes = epgStore.programmes(
            channelId = resolved,
            from = now.minus(backHours, ChronoUnit.HOURS),
            to = now.plus(SCHEDULE_FORWARD_HOURS, ChronoUnit.HOURS),
        )
        val shiftSeconds = epgShiftSeconds(channel)
        val shifted = if (shiftSeconds == 0L) {
            programmes
        } else {
            programmes.map {
                it.copy(start = it.start.plusSeconds(shiftSeconds), end = it.end.plusSeconds(shiftSeconds))
            }
        }
        // Диагностика: видно, с каким каналом гида сопоставлен канал и что считается текущим.
        logGuideMatch(channel, resolved, programmes, shiftSeconds)
        return ProgrammeRows.build(
            programmes = shifted,
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
    private suspend fun logGuideMatch(
        channel: Channel,
        epgId: String,
        programmes: List<Programme>,
        shiftSeconds: Long = 0L,
    ) {
        val epgName = runCatching {
            epgStore.epgChannels().firstOrNull { it.id == epgId }?.displayName
        }.getOrNull()
        val at = clock.instant().minusSeconds(shiftSeconds)
        val current = programmes.firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
        android.util.Log.d(
            "SmotrishkaGuide",
            "канал='${channel.name}' epgId=$epgId гид='${epgName.orEmpty()}' " +
                "текущая='${current?.title.orEmpty()}' " +
                "начало=${current?.start} конец=${current?.end} сейчас=${clock.instant()}",
        )
    }

    /**
     * Канал гида для канала каталога.
     *
     * Порядок тот же, что у списка каналов на главном экране: сохранённая связь → `epgId`
     * канала → сопоставление по названию ([EpgResolver]). Раньше OSD, оверлей телепрограммы
     * и оверлей каналов искали гид по-разному, и передачи расходились.
     */
    private suspend fun resolveEpg(channel: Channel): String? {
        val links = runCatching { channelEpgStore.links() }.getOrDefault(emptyMap())
        return EpgResolver.resolve(
            channels = listOf(channel),
            links = links,
            epgChannels = epgStore.epgChannels(),
            matcher = epgMatcher,
        )[channel.id]
    }

    /** Режим кадра. */
    val aspectMode: Flow<AspectMode> = settingsStore.aspectMode

    /** Недавние каналы: свежие первыми, с текущей передачей и её описанием, как в списке каналов. */
    val recents: Flow<List<RecentChannelRef>> = recentChannels.channels.transform { ids ->
        val catalog = catalogStore.playlist().channels
        val byId = catalog.associateBy { it.id.value }
        val items = ids.mapNotNull { id -> byId[id] }
        // Сначала только названия: панель не должна ждать гид. Архив известен сразу —
        // он приходит из плейлиста, а не из гида.
        emit(items.map { it.toRecentRef() })
        val programmes = nowProgrammes(items)
        val logos = logoUrls(items)
        emit(
            items.map { channel ->
                channel.toRecentRef(
                    logoUrl = channel.logoUrl?.takeIf { it.isNotBlank() } ?: logos[channel.id.value],
                    programme = programmes[channel.id.value],
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
        viewModelScope.launch { settingsStore.setAspectMode(AspectModes.next(current)) }
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
    private suspend fun currentProgramme(channel: Channel): Programme? {
        // Тот же гид и тот же сдвиг, что у списка каналов и экрана программы: иначе
        // OSD показывал передачу, которой в эфире нет (замечание Александра 09.11.2026).
        val epgId = resolveEpg(channel) ?: return null

        val shiftSeconds = epgShiftSeconds(channel)
        val at = clock.instant().minusSeconds(shiftSeconds)
        val found = epgStore.programmes(
            channelId = epgId,
            from = at.minus(24, ChronoUnit.HOURS),
            to = at.plus(24, ChronoUnit.HOURS),
        ).firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
        return if (found == null || shiftSeconds == 0L) {
            found
        } else {
            // Время показываем с поправкой, как в списке каналов.
            found.copy(
                start = found.start.plusSeconds(shiftSeconds),
                end = found.end.plusSeconds(shiftSeconds),
            )
        }
    }

    /** Сдвиг гида канала в секундах: свой важнее общего (решение Александра 09.11.2026). */
    private suspend fun epgShiftSeconds(channel: Channel): Long {
        val own = settingsStore.epgShifts.first()[channel.id.value]
        val hours = own ?: settingsStore.globalEpgShift.first()
        return hours.toLong() * 60L * 60L
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

        /** Окно телепрограммы в OSD: столько же, сколько на экране программы. */
        private const val SCHEDULE_BACK_HOURS = 12L
        private const val SCHEDULE_FORWARD_HOURS = 24L

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
