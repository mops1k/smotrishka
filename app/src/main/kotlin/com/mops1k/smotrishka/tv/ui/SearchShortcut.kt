package com.mops1k.smotrishka.tv.ui

import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Сигнал «открыть поиск» от кнопки пульта.
 *
 * Кнопка поиска на пульте приходит в активити как `KEYCODE_SEARCH`, поэтому она пробрасывается
 * на экран через этот канал: главный экран открывает ту же строку поиска, что и кнопка «Поиск»
 * (замечание Александра 09.11.2026).
 */
object SearchShortcut {

    /** Запросы на открытие поиска. */
    val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Просит главный экран открыть поиск. */
    fun request() {
        requests.tryEmit(Unit)
    }
}
