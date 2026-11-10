package com.mops1k.smotrishka.tv.ui.vod

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle

/**
 * Состояние поиска по медиатеке.
 *
 * @property query введённая строка;
 * @property results найденные карточки;
 * @property loading идёт запрос к порталу;
 * @property error причина ошибки поиска.
 */
data class VodSearchUiState(
    val query: String = "",
    val results: List<VodTitle> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {

    /** Поиск реально что-то искал: нужен для текста «ничего не найдено». */
    val hasQuery: Boolean
        get() = query.isNotBlank()

    /** Запрос прошёл, но результатов нет. */
    val isEmpty: Boolean
        get() = hasQuery && !loading && error == null && results.isEmpty()
}
