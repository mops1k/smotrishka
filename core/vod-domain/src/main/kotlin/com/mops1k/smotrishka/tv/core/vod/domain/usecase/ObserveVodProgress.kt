package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import kotlinx.coroutines.flow.Flow

/**
 * Наблюдает за историей просмотра медиатеки.
 *
 * @param progressPort история просмотра.
 */
class ObserveVodProgress(private val progressPort: VodPlaybackProgressPort) {

    /** Поток всех сохранённых позиций. */
    operator fun invoke(): Flow<List<VodPlaybackProgress>> = progressPort.progress
}
