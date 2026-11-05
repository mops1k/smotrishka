package com.mops1k.smotrishka.tv.ui.onboarding

/**
 * Состояние экрана подключения источника.
 */
sealed interface OnboardingUiState {

    /** Пользователь заполняет форму. */
    data object Idle : OnboardingUiState

    /** Идёт загрузка плейлиста и каталога. */
    data object Loading : OnboardingUiState

    /** Источник подключён, каталог сохранён. */
    data object Success : OnboardingUiState

    /**
     * Подключиться не удалось.
     *
     * @param reason текст причины для пользователя.
     */
    data class Error(val reason: String) : OnboardingUiState
}
