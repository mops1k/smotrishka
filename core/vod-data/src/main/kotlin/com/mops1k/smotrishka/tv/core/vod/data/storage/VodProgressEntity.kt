package com.mops1k.smotrishka.tv.core.vod.data.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Позиция просмотра карточки медиатеки.
 *
 * Одна запись на карточку: для сезона хранится последняя открытая серия и позиция в ней.
 *
 * @property titleId карточка (фильм или сезон);
 * @property episodeId последняя открытая серия;
 * @property episodeNumber номер серии;
 * @property positionMillis позиция в миллисекундах;
 * @property durationMillis длительность, если плеер её знал;
 * @property updatedAtMillis когда обновили.
 */
@Entity(tableName = "vod_progress")
data class VodProgressEntity(
    @PrimaryKey val titleId: Int,
    val episodeId: Int?,
    val episodeNumber: Int?,
    val positionMillis: Long,
    val durationMillis: Long?,
    val updatedAtMillis: Long,
)
