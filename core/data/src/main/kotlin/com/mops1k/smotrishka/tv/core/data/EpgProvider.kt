package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Programme
import com.mops1k.smotrishka.tv.core.playlist.EpgIndex
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Провайдер программы передач: единственная точка, где канал каталога сопоставляется с гидом.
 *
 * Экраны не знают ни про гиды, ни про порядок поиска — они спрашивают провайдер. Правила
 * (порядок, приоритет связей, свежесть источника, сверка сеток) живут здесь, поэтому правка
 * сопоставления не задевает экраны.
 *
 * Порядок выбора — [EpgProvider]: сохранённая связь → `tvg-id` → точное имя, а если у гида
 * нет свежих передач — брендовое имя без языковых суффиксов. Подмена по бренду может указать
 * на другой канал с похожим названием, поэтому перед ней сетки двух кандидатов сверяются
 * по временам начала передач ([EpgStore.sameSchedule]) (решение Александра 09.11.2026).
 *
 * @param channelEpgStore сохранённые связи каналов с гидом;
 * @param epgStore хранилище программы передач;
 * @param settingsStore настройки плеера: общий и поканальный сдвиги гида;
 * @param matcher сопоставитель каналов по `tvg-id` и названию.
 */
@Singleton
class EpgProvider @Inject constructor(
    private val channelEpgStore: ChannelEpgStore,
    private val epgStore: EpgStore,
    private val settingsStore: PlayerSettingsStore,
    private val matcher: EpgMatcher = EpgMatcher(),
) {

    /** Кеш индекса: гидов тысячи каналов, пересобирать их на каждый экран нельзя. */
    private val cachedMutex = Mutex()

    @Volatile
    private var cached: CachedIndex? = null

    /**
     * Связи каналов каталога с каналами гида.
     *
     * @param channels каналы каталога;
     * @param now текущий момент: по нему определяются свежие каналы гида.
     */
    suspend fun links(channels: List<Channel>, now: Instant): Map<ChannelId, String> {
        if (channels.isEmpty()) return emptyMap()
        val stored = runCatching { channelEpgStore.links() }.getOrDefault(emptyMap())
        return links(channels, stored, now)
    }

    /**
     * Канал гида для одного канала каталога.
     *
     * @param channel канал каталога;
     * @param now текущий момент.
     * @return канал гида или `null`, если гида нет.
     */
    suspend fun epgId(channel: Channel, now: Instant): String? =
        links(listOf(channel), now)[channel.id]

    /**
     * Связи каналов с гидом по готовой карте сохранённых связей.
     *
     * @param channels каналы каталога;
     * @param stored сохранённые связи;
     * @param now текущий момент.
     */
    suspend fun links(
        channels: List<Channel>,
        stored: Map<ChannelId, String>,
        now: Instant,
    ): Map<ChannelId, String> {
        if (channels.isEmpty()) return emptyMap()
        val epgChannels = runCatching { epgStore.epgChannels() }.getOrDefault(emptyList())
        val fresh = runCatching { epgStore.freshChannelIds(now) }.getOrDefault(emptySet())
        // Индекс строится один раз на версию гида, параллельно и вне главного потока:
        // каналов каталога и гида тысячи, нормализация имён на главном потоке вешала экран.
        val index = indexFor(epgChannels)
        return channels.mapNotNull { channel ->
            val chosen = choose(channel, stored, index, fresh, now) ?: return@mapNotNull null
            val verified = verified(channel, chosen, stored, index, now)
            if (verified != chosen) {
                android.util.Log.d(
                    "SmotrishkaGuide",
                    "канал='${channel.name}' подмена $chosen отменена проверкой сеток",
                )
            }
            channel.id to verified
        }.toMap()
    }

    /**
     * Возвращает индекс каналов гида, строя его при первом обращении.
     *
     * @param epgChannels каналы гида.
     */
    private suspend fun indexFor(epgChannels: List<EpgChannel>): EpgIndex {
        val key = epgChannels.size to epgChannels.hashCode()
        return cachedMutex.withLock {
            cached?.takeIf { it.key == key }?.index ?: buildIndex(epgChannels).also { index ->
                cached = CachedIndex(key, index)
            }
        }
    }

    /**
     * Строит индекс: имена нормализуются параллельно на `Dispatchers.Default`.
     *
     * @param epgChannels каналы гида.
     */
    private suspend fun buildIndex(epgChannels: List<EpgChannel>): EpgIndex = coroutineScope {
        val names = epgChannels.chunked(INDEX_CHUNK)
            .map { chunk ->
                async(Dispatchers.Default) {
                    chunk.map { matcher.normalize(it.displayName) to matcher.brandName(it.displayName) }
                }
            }
            .awaitAll()
            .flatten()
        EpgIndex.fromNames(epgChannels, names, matcher)
    }

    /** Индекс и ключ версии гида, по которому он построен. */
    private data class CachedIndex(val key: Pair<Int, Int>, val index: EpgIndex)

    /**
     * Передачи канала каталога: канал гида определяется провайдером.
     *
     * @param channel канал каталога;
     * @param from начало окна;
     * @param to конец окна;
     * @param now текущий момент.
     */
    suspend fun programmes(
        channel: Channel,
        from: Instant,
        to: Instant,
        now: Instant,
    ): List<com.mops1k.smotrishka.tv.core.model.Programme> {
        val epgId = epgId(channel, now) ?: return emptyList()
        return epgStore.programmes(epgId, from, to)
    }

    /**
     * Передачи, которые идут сейчас, для списка каналов — единственная точка входа.
     *
     * Главный экран, оверлеи «Каналы» и «Недавние каналы» спрашивают передачу здесь: окно
     * выборки и сдвиг гида (общий и поканальный) считаются в одном месте, поэтому списки
     * не расходятся (указание Александра 11.11.2026).
     *
     * Окно расширяется на сдвиг любого знака: у канала со сдвигом −3 ч «сейчас» для гида
     * наступает через три часа, и без запаса в будущем передача не находилась вовсе —
     * главный экран показывал передачу, а оверлей каналов нет.
     *
     * Передачи запрашиваются одним действием на всё окно: по запросу на канал список открывался
     * бы секундами, а на каталоге в тысячи каналов — минутами.
     *
     * @param channels каналы каталога, по которым нужны передачи (только они, не весь каталог);
     * @param now текущий момент;
     * @param links готовые связи каналов с гидом, если экран их уже посчитал;
     * @param backHours сколько часов назад искать передачу;
     * @param forwardHours сколько часов вперёд искать передачу;
     * @return передача по идентификатору канала каталога; канала нет в ответе — передачи нет.
     */
    suspend fun current(
        channels: List<Channel>,
        now: Instant,
        links: Map<ChannelId, String>? = null,
        backHours: Long = NOW_BACK_HOURS,
        forwardHours: Long = NOW_FORWARD_HOURS,
    ): Map<ChannelId, NowProgramme> {
        if (channels.isEmpty()) return emptyMap()
        val matched = links
            ?: runCatching { links(channels, now) }.getOrDefault(emptyMap())
        val shiftSettings = shiftSettings()
        val shiftSeconds = channels.associate { channel ->
            channel.id.value to
                (shiftSettings.own[channel.id.value] ?: shiftSettings.default).toLong() * 3600L
        }
        // Сдвиг назад требует запаса в прошлом, сдвиг вперёд — в будущем.
        val maxBack = shiftSeconds.values.maxOrNull()?.coerceAtLeast(0L) ?: 0L
        val maxForward = shiftSeconds.values.minOrNull()?.let { (-it).coerceAtLeast(0L) } ?: 0L
        val programmes = runCatching {
            epgStore.programmesByChannel(
                from = now.minus(backHours, ChronoUnit.HOURS).minusSeconds(maxBack),
                to = now.plus(forwardHours, ChronoUnit.HOURS).plusSeconds(maxForward),
            )
        }.getOrDefault(emptyMap())
        if (programmes.isEmpty()) return emptyMap()
        return channels.mapNotNull { channel ->
            val epgId = matched[channel.id] ?: channel.epgId?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val schedule = programmes[epgId] ?: return@mapNotNull null
            val shift = shiftSeconds[channel.id.value] ?: 0L
            val at = now.minusSeconds(shift)
            val current = schedule.firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
                ?: return@mapNotNull null
            channel.id to NowProgramme(
                title = current.title,
                description = current.description,
                start = current.start.plusSeconds(shift),
                end = current.end.plusSeconds(shift),
                nextTitle = schedule.firstOrNull { it.start.isAfter(at) }?.title,
            )
        }.toMap()
    }

    /**
     * Передача канала на заданный момент: и «сейчас», и архивный выбор.
     *
     * Момент приходит в показываемом (местном) времени, поэтому к сетке гида он приводится
     * через сдвиг канала: без этого в шапке плеера стояла чужая передача — та, что идёт
     * в это время по сетке сдвинутого гида (замечание Александра 11.11.2026).
     *
     * @param channel канал каталога;
     * @param moment момент в показываемом времени;
     * @return передача с границами уже в показываемом времени; `null` — гид её не знает.
     */
    suspend fun at(channel: Channel, moment: Instant): NowProgramme? {
        val shift = shiftSeconds(channel.id.value)
        val guideMoment = moment.minusSeconds(shift)
        val epgId = epgId(channel, guideMoment)
            ?: channel.epgId?.takeIf { it.isNotBlank() }
            ?: return null
        val schedule = runCatching {
            epgStore.programmes(
                channelId = epgId,
                from = guideMoment.minus(MOMENT_WINDOW_HOURS, ChronoUnit.HOURS),
                to = guideMoment.plus(MOMENT_WINDOW_HOURS, ChronoUnit.HOURS),
            )
        }.getOrDefault(emptyList())
        val current = schedule.firstOrNull {
            !it.start.isAfter(guideMoment) && it.end.isAfter(guideMoment)
        } ?: return null
        return NowProgramme(
            title = current.title,
            description = current.description,
            start = current.start.plusSeconds(shift),
            end = current.end.plusSeconds(shift),
            nextTitle = schedule.firstOrNull { it.start.isAfter(guideMoment) }?.title,
        )
    }

    /**
     * Сдвиг гида канала в секундах: свой важнее общего (решение Александра 09.11.2026).
     *
     * @param channelId идентификатор канала каталога.
     */
    suspend fun shiftSeconds(channelId: String): Long {
        val shifts = shiftSettings()
        return (shifts.own[channelId] ?: shifts.default).toLong() * 3600L
    }

    /**
     * Момент, который сейчас играет у зрителя.
     *
     * Единственное место, где решается, «где мы»: в архиве это начало выбранной передачи,
     * в прямом эфире — текущее время. По нему плеер ставит фокус в «Телепрограмме» на ту
     * передачу, которую человек смотрит, а не на эфирную (указание Александра 11.11.2026).
     *
     * @param startEpochSeconds начало архивной передачи; `null` или [NO_START] — прямой эфир;
     * @param now текущий момент.
     */
    fun playbackMoment(startEpochSeconds: Long?, now: Instant): Instant =
        if (startEpochSeconds == null || startEpochSeconds == NO_START) {
            now
        } else {
            Instant.ofEpochSecond(startEpochSeconds)
        }

    /**
     * Полная телепрограмма канала: границы уже со сдвигом гида канала.
     *
     * Единственный вход для списков передач — экран телепрограммы, разворот карточки и панель
     * «Телепрограмма» в плеере: окно и сдвиг считаются здесь, поэтому сетки не расходятся.
     *
     * @param channel канал каталога;
     * @param from начало окна в показываемом времени;
     * @param to конец окна в показываемом времени;
     * @param now текущий момент.
     */
    suspend fun schedule(
        channel: Channel,
        from: Instant,
        to: Instant,
        now: Instant,
    ): List<Programme> {
        val shift = shiftSeconds(channel.id.value)
        val guideNow = now.minusSeconds(shift)
        val found = programmes(
            channel = channel,
            from = from.minusSeconds(shift),
            to = to.minusSeconds(shift),
            now = guideNow,
        )
        if (shift == 0L) return found
        return found.map {
            it.copy(start = it.start.plusSeconds(shift), end = it.end.plusSeconds(shift))
        }
    }

    /** Иконки каналов гида: у каталога провайдера своих адресов логотипов часто нет. */
    suspend fun epgIcons(epgIds: Set<String>): Map<String, String> =
        if (epgIds.isEmpty()) emptyMap() else runCatching { epgStore.icons(epgIds) }.getOrDefault(emptyMap())

    /** Сколько передач в базе: экран перечитывает программу, когда это число меняется. */
    val programmeCount: Flow<Int> get() = epgStore.programmeCount

    /** Название канала гида — для диагностики сопоставления в журнале. */
    suspend fun epgName(epgId: String): String? = runCatching {
        epgStore.epgChannels().firstOrNull { it.id == epgId }?.displayName
    }.getOrNull()

    /** Сдвиги гида из настроек: поканальные и общий по умолчанию. */
    private suspend fun shiftSettings(): ShiftSettings = ShiftSettings(
        own = runCatching { settingsStore.epgShifts.first() }.getOrDefault(emptyMap()),
        default = runCatching { settingsStore.globalEpgShift.first() }.getOrDefault(0),
    )

    /** Сдвиги гида в часах: поканальные и общий по умолчанию. */
    private data class ShiftSettings(val own: Map<String, Int>, val default: Int)

    /**
     * Проверяет подмену гида по бренду.
     *
     * @return канал гида: выбранный, если подмены не было или сетки совпали, иначе привязанный.
     */
    private suspend fun verified(
        channel: Channel,
        chosen: String,
        stored: Map<ChannelId, String>,
        index: EpgIndex,
        now: Instant,
    ): String {
        val primary = index.primary(channel, stored) ?: return chosen
        if (primary == chosen) return chosen
        // Сверяем прошедшие сутки: у привязанного гида свежих передач нет по определению,
        // но за прошлый период сетка обычно есть.
        val same = runCatching {
            epgStore.sameSchedule(
                first = primary,
                second = chosen,
                from = now.minus(VERIFY_HOURS, ChronoUnit.HOURS),
                to = now,
            )
        }.getOrDefault(true)
        if (!same) {
            android.util.Log.d(
                "SmotrishkaGuide",
                "подмена отменена: канал='${channel.name}' привязан=$primary кандидат=$chosen " +
                    "сетки за сутки не совпали",
            )
        }
        return if (same) chosen else primary
    }

    /**
     * Выбирает канал гида для канала каталога.
     *
     * Порядок (решения Александра 09–10.11.2026): привязанный гид, если он обновляется;
     * иначе канал того же бренда из свежего источника, причём предпочитается тот, где сейчас
     * идёт настоящая передача (а не заглушка «программа отсутствует»). Если свежих
     * одноимённых несколько и настоящей передачи нет ни у кого — не угадываем и оставляем
     * привязанный гид.
     *
     * @param channel канал каталога;
     * @param stored сохранённые связи;
     * @param index индекс каналов гида;
     * @param covered каналы гида с передачами в окне; пусто — свежесть неизвестна;
     * @param now текущий момент.
     */
    private suspend fun choose(
        channel: Channel,
        stored: Map<ChannelId, String>,
        index: EpgIndex,
        covered: Set<String>,
        now: Instant,
    ): String? {
        val primary = index.primary(channel, stored)
        if (covered.isEmpty()) return primary
        val brandFresh = index.brandIds(channel).filter { it in covered && it != primary }
        val fresh = buildList {
            primary?.takeIf { it in covered }?.let(::add)
            addAll(brandFresh)
        }
        fresh.firstOrNull { realNow(it, now) }?.let { return it }
        return primary?.takeIf { it in covered } ?: brandFresh.singleOrNull() ?: primary
    }

    /** Идёт ли у канала гида настоящая передача в момент. */
    private suspend fun realNow(epgId: String, now: Instant): Boolean {
        val title = runCatching { epgStore.currentTitle(epgId, now) }.getOrNull() ?: return false
        return !EpgPlaceholders.isPlaceholder(title)
    }

    private companion object {
        /** Глубина сверки сеток: сутки назад. */
        const val VERIFY_HOURS = 24L

        /** Значение «архивной передачи нет»: прямой эфир. */
        const val NO_START = -1L

        /** Размер пачки при параллельной нормализации имён гида. */
        const val INDEX_CHUNK = 256

        /** Окно поиска текущей передачи для списка каналов: шесть часов назад. */
        const val NOW_BACK_HOURS = 6L

        /** Окно поиска текущей передачи для списка каналов: два часа вперёд. */
        const val NOW_FORWARD_HOURS = 2L

        /** Окно поиска передачи на момент: сутки, столько же у экрана телепрограммы. */
        const val MOMENT_WINDOW_HOURS = 24L
    }
}
