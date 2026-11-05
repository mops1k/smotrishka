package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сохранённые связи каналов каталога с каналами гида.
 *
 * Сопоставление по названиям — дорогая операция: она требует прочитать все каналы гида
 * (на приставке ~3.2 с при 3147 каналах). Поэтому связь считается при обновлении каталога
 * и хранится в базе, а экран читает уже готовое соответствие.
 */
@Singleton
class ChannelEpgStore @Inject constructor(
    private val dao: ChannelEpgDao,
    private val epgStore: EpgStore,
    private val epgMatcher: EpgMatcher,
) {

    /**
     * Пересобирает связи по текущему каталогу.
     *
     * @param channels каналы каталога;
     * @return сколько связей сохранено.
     */
    suspend fun rebuild(channels: List<Channel>): Int {
        val epgChannels = epgStore.epgChannels()
        if (epgChannels.isEmpty() || channels.isEmpty()) return 0
        val matches = epgMatcher.match(channels, epgChannels).matches
        val links = matches.map { (channelId, epgId) ->
            ChannelEpgEntity(channelId = channelId.value, epgChannelId = epgId)
        }
        dao.clear()
        if (links.isNotEmpty()) dao.insertAll(links)
        return links.size
    }

    /** Сохранённые связи: канал каталога → канал гида. */
    suspend fun links(): Map<ChannelId, String> =
        dao.all().associate { ChannelId(it.channelId) to it.epgChannelId }
}
