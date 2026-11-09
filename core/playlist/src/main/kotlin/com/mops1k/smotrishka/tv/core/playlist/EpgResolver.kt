package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.EpgChannel

/**
 * Единый порядок поиска канала гида для канала каталога.
 *
 * Все экраны (список каналов, OSD плеера, оверлеи «Каналы», «Недавние» и «Телепрограмма»,
 * полноэкранная телепрограмма) должны брать программу одинаково, иначе у одного канала
 * передача есть в одном месте и «Программы нет» в другом (замечание Александра 09.11.2026).
 *
 * Порядок: сохранённая связь канала с гидом → `epgId` канала (`tvg-id`) → сопоставление
 * по названию ([EpgMatcher]). Канал, которому гид не нашёлся ни одним путём, остаётся
 * без программы — показать передачу чужого канала хуже, чем честно показать «нет».
 */
object EpgResolver {

    /**
     * Сопоставляет каналы каталога с каналами гида.
     *
     * @param channels каналы каталога;
     * @param links сохранённые связи каналов с гидом;
     * @param epgChannels каналы гида;
     * @param matcher сопоставитель по `tvg-id` и названию.
     */
    fun resolve(
        channels: List<Channel>,
        links: Map<ChannelId, String>,
        epgChannels: List<EpgChannel>,
        matcher: EpgMatcher = EpgMatcher(),
    ): Map<ChannelId, String> {
        if (channels.isEmpty()) return emptyMap()
        val unmatched = channels.filter { links[it.id] == null }
        val matched = if (unmatched.isEmpty() || epgChannels.isEmpty()) {
            emptyMap()
        } else {
            matcher.match(unmatched, epgChannels).matches
        }
        return channels.mapNotNull { channel ->
            (links[channel.id] ?: matched[channel.id])?.let { epgId -> channel.id to epgId }
        }.toMap()
    }
}
