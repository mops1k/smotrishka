package com.mops1k.smotrishka.tv.core.vod.data.storage

import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Перевод позиции просмотра между доменом и Room.
 */

/** Доменная позиция в строку таблицы. */
internal fun VodPlaybackProgress.toEntity(): VodProgressEntity = VodProgressEntity(
    titleId = titleId.value,
    episodeId = episodeId?.value,
    episodeNumber = episodeNumber?.value,
    positionMillis = positionMillis,
    durationMillis = durationMillis,
    updatedAtMillis = updatedAtMillis,
)

/** Строка таблицы в доменную позицию. */
internal fun VodProgressEntity.toDomain(): VodPlaybackProgress = VodPlaybackProgress(
    titleId = VodTitleId(titleId),
    episodeId = episodeId?.let { VodTitleId(it) },
    episodeNumber = episodeNumber?.let { VodEpisodeNumber(it) },
    positionMillis = positionMillis,
    durationMillis = durationMillis,
    updatedAtMillis = updatedAtMillis,
)
