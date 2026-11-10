package com.mops1k.smotrishka.tv.core.vod.domain.port

import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import kotlinx.coroutines.flow.Flow

/**
 * Хранилище позиций просмотра медиатеки.
 *
 * Портал историю не отдаёт, поэтому «Продолжить с …» строится только на этом хранилище.
 */
interface VodPlaybackProgressPort {

    /** Все сохранённые позиции. */
    val progress: Flow<List<VodPlaybackProgress>>

    /**
     * Сохраняет позицию карточки (или серии).
     *
     * @param progress позиция просмотра.
     */
    suspend fun save(progress: VodPlaybackProgress)

    /** Очищает историю — при смене или отключении портала. */
    suspend fun clear()
}
