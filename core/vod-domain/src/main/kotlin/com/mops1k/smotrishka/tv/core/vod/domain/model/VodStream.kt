package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Поток медиатеки: ссылка на HLS и варианты качества, если портал их дал.
 *
 * `url` — всегда `master.m3u8` со всеми вариантами внутри: именно его отдают плееру по
 * умолчанию. Конкретные `qualities` нужны только для явного выбора дорожки.
 *
 * @property url адрес master-плейлиста;
 * @property qualities варианты качества из ответа портала;
 * @property durationSeconds длительность, если портал её знает (у части фильмов приходит 0).
 */
data class VodStream(
    val url: String,
    val qualities: Map<VodQuality, String> = emptyMap(),
    val durationSeconds: Long? = null,
)
