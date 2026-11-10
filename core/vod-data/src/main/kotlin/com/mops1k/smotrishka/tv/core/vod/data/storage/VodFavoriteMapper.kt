package com.mops1k.smotrishka.tv.core.vod.data.storage

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodFavorite
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleKind

/**
 * Перевод избранного между доменом и Room.
 */

/** Доменная карточка в строку таблицы. */
internal fun VodFavorite.toEntity(): VodFavoriteEntity = VodFavoriteEntity(
    titleId = title.id.value,
    title = title.title,
    year = title.year,
    posterUrl = title.posterUrl,
    previewPosterUrl = title.previewPosterUrl,
    description = title.description,
    kind = title.kind.name,
    durationSeconds = title.durationSeconds,
    isFhd = title.isFhd,
    is4k = title.is4k,
    isHdr = title.isHdr,
    ageLimit = title.ageLimit,
    addedAtMillis = addedAtMillis,
)

/** Строка таблицы в доменную карточку. */
internal fun VodFavoriteEntity.toDomain(): VodFavorite = VodFavorite(
    title = VodTitle(
        id = VodTitleId(titleId),
        title = title,
        year = year,
        posterUrl = posterUrl,
        previewPosterUrl = previewPosterUrl,
        description = description,
        kind = runCatching { VodTitleKind.valueOf(kind) }.getOrDefault(VodTitleKind.STREAM),
        durationSeconds = durationSeconds,
        isFhd = isFhd,
        is4k = is4k,
        isHdr = isHdr,
        ageLimit = ageLimit,
    ),
    addedAtMillis = addedAtMillis,
)
