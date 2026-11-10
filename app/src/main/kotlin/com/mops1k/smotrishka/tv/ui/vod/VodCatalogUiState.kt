package com.mops1k.smotrishka.tv.ui.vod

import com.mops1k.smotrishka.tv.core.vod.domain.VodLibrary
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle

/**
 * Состояние экрана медиатеки.
 */
sealed interface VodCatalogUiState {

    /** Первая загрузка: категории и первая страница ещё не пришли. */
    data object Loading : VodCatalogUiState

    /** Портал не подключён — медиатеку показывать нечем. */
    data object NotConfigured : VodCatalogUiState

    /**
     * Портал ответил ошибкой.
     *
     * @param reason понятный текст для экрана.
     */
    data class Error(val reason: String) : VodCatalogUiState

    /**
     * Каталог загружен.
     *
     * @param library агрегат каталога: категории, выбранная, загруженные карточки и курсор;
     * @param loadingMore идёт догрузка следующей страницы;
     * @param selected карточка под фокусом — для панели превью.
     */
    data class Content(
        val library: VodLibrary,
        val loadingMore: Boolean = false,
        val selected: VodTitle? = null,
    ) : VodCatalogUiState
}
