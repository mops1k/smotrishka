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

    /**
     * Приводит название к сравнимому виду: без регистра, лишних пробелов, `ё`→`е`.
     *
     * @param raw название канала или канала гида.
     */
    fun normalize(raw: String): String =
        raw.trim()
            .lowercase(Locale.ROOT)
            .replace(WHITESPACE, " ")
            .replace('ё', 'е')

    /**
     * Даёт «бренд» названия — без языковых и качественных суффиксов.
     *
     * Один и тот же канал в разных гидах зовётся по-разному («4Ever Drama HD UA» и
     * «4ever Drama HD»), поэтому для запасного поиска суффиксы `UA`, `RU`, `HD` и подобные
     * отбрасываются. Если от названия ничего не осталось, возвращается обычная нормализация.
     *
     * @param raw название канала или канала гида.
     */
    fun brandName(raw: String): String {
        val words = normalize(raw).split(' ').filterNot { it in SUFFIXES }
        return words.joinToString(" ").ifBlank { normalize(raw) }
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
        val SUFFIXES = setOf("ua", "ru", "kz", "by", "hd", "fhd", "uhd", "sd", "4k", "int")
    }
}
