package com.mops1k.smotrishka.tv.ui.vod

import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality

/**
 * Состояние карточки медиатеки.
 */
sealed interface VodTitleUiState {

    /** Карточка загружается. */
    data object Loading : VodTitleUiState

    /**
     * Карточку не удалось получить.
     *
     * @param reason понятный пользователю текст.
     */
    data class Error(val reason: String) : VodTitleUiState

    /**
     * Карточка загружена.
     *
     * @param details фильм или сезон с сериями;
     * @param favorite карточка в избранном;
     * @param progress сохранённая позиция просмотра, если смотрели;
     * @param qualities доступные качества записи.
     */
    data class Content(
        val details: VodTitleDetails,
        val favorite: Boolean,
        val progress: VodPlaybackProgress?,
        val qualities: Map<VodQuality, String>,
    ) : VodTitleUiState {

        /** Подпись кнопки просмотра: продолжение с места или начало. */
        val watchLabel: String
            get() {
                val current = progress ?: return "Смотреть"
                if (!current.canResume) return "Смотреть"
                // Номер серии берём в переменную: свойство из другого модуля не поддаётся
                // smart cast.
                val episode = current.episodeNumber
                return if (episode != null) {
                    "Продолжить: серия ${episode.value}"
                } else {
                    "Продолжить с ${formatPosition(current.positionMillis)}"
                }
            }
    }
}

/**
 * Позиция в виде «12:34» — для подписи «Продолжить с …».
 *
 * @param positionMillis позиция в миллисекундах.
 */
fun formatPosition(positionMillis: Long): String {
    val totalSeconds = (positionMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
