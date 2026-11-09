package com.mops1k.smotrishka.tv.ui.programme

import com.mops1k.smotrishka.tv.core.model.ChannelId
import java.time.Instant

/**
 * Состояние экрана программы канала.
 */
sealed interface ProgrammeUiState {

    /** Читаем программу. */
    data object Loading : ProgrammeUiState

    /**
     * Программу прочитать не удалось.
     *
     * @param reason понятный пользователю текст.
     */
    data class Error(val reason: String) : ProgrammeUiState

    /**
     * Программа готова.
     *
     * @param channelId канал;
     * @param channelName название канала;
     * @param logoUrl логотип, если есть;
     * @param items передачи по времени.
     */
    data class Content(
        val channelId: ChannelId,
        val channelName: String,
        val logoUrl: String?,
        val items: List<ProgrammeUi>,
    ) : ProgrammeUiState
}

/**
 * Передача в ленте программы.
 *
 * @param title название;
 * @param description описание передачи из гида, если провайдер его отдал;
 * @param start начало;
 * @param end конец;
 * @param isCurrent идёт ли прямо сейчас;
 * @param isPast уже прошла ли;
 * @param inArchive доступна ли в архиве с учётом его глубины.
 */
data class ProgrammeUi(
    val title: String,
    val description: String? = null,
    val start: Instant,
    val end: Instant,
    val isCurrent: Boolean,
    val isPast: Boolean,
    val inArchive: Boolean = true,
)
