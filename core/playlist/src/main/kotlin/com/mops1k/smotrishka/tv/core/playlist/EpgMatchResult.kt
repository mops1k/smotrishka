package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.ChannelId

/**
 * Результат сопоставления каналов каталога с каналами EPG.
 *
 * @param matches наш канал → идентификатор канала в EPG;
 * @param unmatched каналы, для которых программы нет.
 */
data class EpgMatchResult(
    val matches: Map<ChannelId, String>,
    val unmatched: List<ChannelId>,
)
