package com.mops1k.smotrishka.tv.core.vod.data.event

import com.mops1k.smotrishka.tv.core.vod.domain.event.VodEvent
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Шина доменных событий в процессе приложения.
 *
 * Буфер с вытеснением: событие — уведомление, а не единственный носитель состояния, поэтому
 * потеря устаревшего события при переполнении безопаснее, чем блокировка публикации.
 */
@Singleton
class InProcessVodEventBus @Inject constructor() : VodEventBus {

    private val stream = MutableSharedFlow<VodEvent>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override val events: Flow<VodEvent> = stream.asSharedFlow()

    override suspend fun publish(event: VodEvent) {
        stream.emit(event)
    }

    private companion object {
        const val BUFFER_CAPACITY = 64
    }
}
