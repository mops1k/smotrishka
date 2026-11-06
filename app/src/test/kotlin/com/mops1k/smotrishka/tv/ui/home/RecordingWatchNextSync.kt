package com.mops1k.smotrishka.tv.ui.home

import com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextCard
import com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextSync

/**
 * Лента «Сейчас идёт» без TvProvider: запоминает, что и когда ей передали.
 *
 * TvProvider в юнит-тестах недоступен, поэтому проверяется решение экрана — какие карточки
 * он публикует и убирает ли канал при выключении.
 */
class RecordingWatchNextSync : WatchNextSync {

    /** Что публиковалось, в порядке вызовов. */
    val published = mutableListOf<List<WatchNextCard>>()

    /** Сколько раз ленту убирали из лаунчера. */
    var cleared: Int = 0
        private set

    /** Последняя опубликованная лента, `null` — публикации не было. */
    val last: List<WatchNextCard>? get() = published.lastOrNull()

    override suspend fun publish(cards: List<WatchNextCard>) {
        published += cards
    }

    override suspend fun clear() {
        cleared++
    }
}
