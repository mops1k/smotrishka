package com.mops1k.smotrishka.tv.core.data.watchnext

import com.mops1k.smotrishka.tv.core.model.Channel

/**
 * Канал каталога вместе с текущей передачей — вход для [WatchNextCardsBuilder].
 *
 * @param channel канал каталога;
 * @param nowTitle что идёт сейчас, `null` — передачи нет (такой канал в ленту не попадает).
 */
data class WatchNextSource(
    val channel: Channel,
    val nowTitle: String?,
)
