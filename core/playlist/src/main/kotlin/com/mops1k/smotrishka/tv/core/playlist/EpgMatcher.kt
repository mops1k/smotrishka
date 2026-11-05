package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import java.util.Locale

/**
 * Сопоставляет каналы каталога с каналами программы передач.
 *
 * Порядок: `tvg-id` → точное имя → нормализованное имя (регистр, лишние пробелы, `ё`→`е`).
 * Если под нормализованное имя подходят несколько каналов EPG, сопоставление не делается:
 * показать передачу чужого канала хуже, чем честно отметить «программы нет».
 */
class EpgMatcher {

    /**
     * Сопоставляет каналы.
     *
     * @param channels каналы каталога;
     * @param epgChannels каналы из файла программы передач.
     */
    fun match(channels: List<Channel>, epgChannels: List<EpgChannel>): EpgMatchResult {
        val byId = epgChannels.associateBy { it.id }
        val byName = epgChannels.groupBy { normalize(it.displayName) }

        val matches = linkedMapOf<ChannelId, String>()
        val unmatched = mutableListOf<ChannelId>()

        channels.forEach { channel ->
            val epgId = channel.epgId?.let { id -> byId[id]?.id }
                ?: byName[normalize(channel.name)]
                    ?.takeIf { it.size == 1 }
                    ?.single()
                    ?.id

            if (epgId != null) {
                matches[channel.id] = epgId
            } else {
                unmatched += channel.id
            }
        }

        return EpgMatchResult(matches = matches, unmatched = unmatched)
    }

    private fun normalize(raw: String): String =
        raw.trim()
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .replace('ё', 'е')

    private companion object {
        val WHITESPACE = Regex("""\s+""")
    }
}
