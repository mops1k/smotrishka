package com.mops1k.smotrishka.tv.ui.settings

/**
 * Состояние экрана настроек.
 */
sealed interface SettingsUiState {

    /** Читаем сохранённый источник. */
    data object Loading : SettingsUiState

    /** Источник ещё не настроен. */
    data object Empty : SettingsUiState

    /**
     * Источник настроен.
     *
     * @param sourceLabel как источник называется для пользователя;
     * @param sourceDetail уточнение без секретов: хост, логин или маска кода.
     */
    data class Content(
        val sourceLabel: String,
        val sourceDetail: String,
    ) : SettingsUiState

    /**
     * Не удалось прочитать источник.
     *
     * @param reason что именно случилось — понятным пользователю текстом.
     */
    data class Error(val reason: String) : SettingsUiState
}
