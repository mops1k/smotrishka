package com.mops1k.smotrishka.tv.core.vod.domain.port

import com.mops1k.smotrishka.tv.core.vod.domain.event.VodEvent
import kotlinx.coroutines.flow.Flow

/**
 * Шина доменных событий медиатеки.
 *
 * Реализация в процессе приложения: события нужны для реакции интерфейса в текущем сеансе,
 * а состояние (избранное, прогресс) живёт в Room и восстанавливается без шины.
 */
interface VodEventBus {

    /** Поток событий для подписчиков. */
    val events: Flow<VodEvent>

    /**
     * Публикует событие подписчикам.
     *
     * @param event доменное событие.
     */
    suspend fun publish(event: VodEvent)
}
