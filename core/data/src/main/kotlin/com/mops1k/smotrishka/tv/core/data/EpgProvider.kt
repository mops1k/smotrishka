package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.playlist.EpgIndex
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * @param matcher сопоставитель каналов по `tvg-id` и названию.
 */
@Singleton
class EpgProvider @Inject constructor(
    private val channelEpgStore: ChannelEpgStore,
    private val epgStore: EpgStore,
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

        /** Размер пачки при параллельной нормализации имён гида. */
        const val INDEX_CHUNK = 256
    }
}
