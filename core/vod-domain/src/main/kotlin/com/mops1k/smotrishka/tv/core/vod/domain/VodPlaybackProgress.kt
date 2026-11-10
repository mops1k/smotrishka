package com.mops1k.smotrishka.tv.core.vod.domain

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Где остановился просмотр: позиция фильма или последняя открытая серия сезона.
 *
 * Портал историю не отдаёт, поэтому это единственный источник «Продолжить с …».
 *
 * @property titleId карточка (фильм или сезон);
 * @property episodeId серия, если смотрят сезон;
 * @property episodeNumber номер серии;
 * @property positionMillis позиция в миллисекундах;
 * @property durationMillis длительность, если плеер её знает;
 * @property updatedAtMillis когда обновили — по нему выбирается последняя запись.
 */
data class VodPlaybackProgress(
    val titleId: VodTitleId,
    val episodeId: VodTitleId? = null,
    val episodeNumber: VodEpisodeNumber? = null,
    val positionMillis: Long = 0L,
    val durationMillis: Long? = null,
    val updatedAtMillis: Long = 0L,
) {

    /** Доля просмотра 0..1, если длительность известна. */
    val fraction: Float?
        get() = durationMillis
            ?.takeIf { it > 0L }
            ?.let { (positionMillis.toFloat() / it).coerceIn(0f, 1f) }

    /** Просмотр считаем законченным после 95 %: хвост титров не должен требовать досмотра. */
    val isFinished: Boolean
        get() = fraction?.let { it >= FINISHED_FRACTION } ?: false

    /** Признак «продолжать есть с чего»: позиция не в самом начале. */
    val canResume: Boolean
        get() = !isFinished && positionMillis >= RESUME_MIN_MILLIS

    /**
     * Обновляет позицию.
     *
     * @param positionMillis новая позиция;
     * @param durationMillis длительность из плеера, если известна;
     * @param atMillis момент сохранения.
     */
    fun withPosition(
        positionMillis: Long,
        durationMillis: Long?,
        atMillis: Long,
    ): VodPlaybackProgress = copy(
        positionMillis = positionMillis.coerceAtLeast(0L),
        durationMillis = durationMillis ?: this.durationMillis,
        updatedAtMillis = atMillis,
    )

    companion object {

        /** Порог «досмотрено». */
        const val FINISHED_FRACTION: Float = 0.95f

        /** С какого места предлагать «Продолжить», а не «Смотреть с начала». */
        const val RESUME_MIN_MILLIS: Long = 30_000L
    }
}
