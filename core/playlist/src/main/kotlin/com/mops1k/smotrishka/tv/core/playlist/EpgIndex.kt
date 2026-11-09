package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.EpgChannel

/**
 * Индексы каналов гида для поиска канала программы.
 *
 * Названия нормализуются один раз при построении, а не на каждый канал каталога: без этого
 * сопоставление 3147 каналов давало миллионы сравнений строк и вешало главный экран (ANR
 * 09.11.2026). Индекс неизменяем — на одну пересборку экрана строится один раз.
 *
 * @param epgChannels каналы гида;
 * @param matcher сопоставитель: нормализация и бренд названия.
 */
class EpgIndex private constructor(
    private val byId: Map<String, EpgChannel>,
    private val byName: Map<String, List<EpgChannel>>,
    private val byBrand: Map<String, List<EpgChannel>>,
    private val matcher: EpgMatcher,
) {

    /**
     * Строит индекс, нормализуя названия на месте.
     *
     * Для больших гидов дорогую нормализацию лучше считать параллельно и передать в [fromNames].
     *
     * @param epgChannels каналы гида;
     * @param matcher сопоставитель: нормализация и бренд названия.
     */
    constructor(
        epgChannels: List<EpgChannel>,
        matcher: EpgMatcher = EpgMatcher(),
    ) : this(
        byId = epgChannels.associateBy { it.id },
        byName = epgChannels.groupBy { matcher.normalize(it.displayName) },
        byBrand = epgChannels.groupBy { matcher.brandName(it.displayName) },
        matcher = matcher,
    )

    /**
     * Канал гида, найденный основными путями: связь → `tvg-id` → точное имя.
     *
     * @param channel канал каталога;
     * @param links сохранённые связи каналов с гидом.
     */
    fun primary(channel: Channel, links: Map<ChannelId, String>): String? {
        links[channel.id]?.let { return it }
        val byEpgId = channel.epgId?.let { id -> byId[id]?.id }
        if (byEpgId != null) return byEpgId
        return byName[matcher.normalize(channel.name)]?.singleOrNull()?.id
    }

    /**
     * Каналы гида того же бренда, что и канал каталога.
     *
     * @param channel канал каталога.
     */
    fun brandIds(channel: Channel): List<String> =
        byBrand[matcher.brandName(channel.name)].orEmpty().map { it.id }.distinct()

    /**
     * Каналы гида, которые провайдер может выбрать для канала каталога.
     *
     * Это основной канал и все каналы того же бренда: импорт гида обязан сохранять их, иначе
     * фолбэк на свежий гид не сработает — канала просто не будет в базе (случай «4Ever Drama
     * HD UA» и «4ever Drama HD» из разных источников).
     *
     * @param channel канал каталога;
     * @param links сохранённые связи каналов с гидом.
     */
    fun candidates(channel: Channel, links: Map<ChannelId, String> = emptyMap()): Set<String> =
        buildSet {
            primary(channel, links)?.let(::add)
            byBrand[matcher.brandName(channel.name)]?.forEach { add(it.id) }
        }

    companion object {
        /**
         * Строит индекс из заранее посчитанных имён.
         *
         * @param epgChannels каналы гида;
         * @param names пары «нормализованное имя — бренд» в том же порядке, что и каналы.
         */
        fun fromNames(
            epgChannels: List<EpgChannel>,
            names: List<Pair<String, String>>,
            matcher: EpgMatcher = EpgMatcher(),
        ): EpgIndex {
            require(names.size == epgChannels.size) {
                "имён ${names.size}, каналов ${epgChannels.size}"
            }
            val pairs = epgChannels.zip(names)
            return EpgIndex(
                byId = epgChannels.associateBy { it.id },
                byName = pairs.groupBy({ it.second.first }, { it.first }),
                byBrand = pairs.groupBy({ it.second.second }, { it.first }),
                matcher = matcher,
            )
        }
    }
}
