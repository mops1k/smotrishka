package com.mops1k.smotrishka.tv.core.vod.data.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Снапшот избранной карточки медиатеки.
 *
 * Хранится целиком, а не только идентификатор: избранное обязано показываться без связи
 * с порталом, иначе при недоступном портале оно превратилось бы в пустой экран.
 *
 * @property titleId идентификатор карточки на портале;
 * @property title название;
 * @property year год выпуска;
 * @property posterUrl большой постер;
 * @property previewPosterUrl превью для сетки;
 * @property description описание;
 * @property kind вид карточки (`STREAM`/`MULTISTREAM`);
 * @property durationSeconds длительность;
 * @property isFhd признак Full HD;
 * @property is4k признак 4K;
 * @property isHdr признак HDR;
 * @property ageLimit возрастное ограничение;
 * @property addedAtMillis когда добавили — порядок показа «новые сверху».
 */
@Entity(tableName = "vod_favorites")
data class VodFavoriteEntity(
    @PrimaryKey val titleId: Int,
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val previewPosterUrl: String?,
    val description: String?,
    val kind: String,
    val durationSeconds: Long?,
    val isFhd: Boolean,
    val is4k: Boolean,
    val isHdr: Boolean,
    val ageLimit: Int?,
    val addedAtMillis: Long,
)
