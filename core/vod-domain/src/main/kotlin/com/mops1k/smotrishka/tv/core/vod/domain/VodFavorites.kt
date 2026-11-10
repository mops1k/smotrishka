package com.mops1k.smotrishka.tv.core.vod.domain

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodFavorite
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Агрегат избранного медиатеки.
 *
 * Порядок — по добавлению, новые сверху. Повторное добавление уже избранной карточки не
 * создаёт дубль, а обновляет снапшот и время: карточка могла сменить постер или название.
 *
 * @property items избранные карточки в порядке показа.
 */
data class VodFavorites(val items: List<VodFavorite> = emptyList()) {

    /**
     * Есть ли карточка в избранном.
     *
     * @param titleId идентификатор карточки.
     */
    fun contains(titleId: VodTitleId): Boolean = items.any { it.title.id == titleId }

    /**
     * Добавляет карточку или обновляет её снапшот.
     *
     * @param title карточка;
     * @param atMillis момент добавления.
     */
    fun add(title: VodTitle, atMillis: Long): VodFavorites = VodFavorites(
        listOf(VodFavorite(title = title, addedAtMillis = atMillis)) +
            items.filterNot { it.title.id == title.id },
    )

    /**
     * Убирает карточку из избранного.
     *
     * @param titleId идентификатор карточки.
     */
    fun remove(titleId: VodTitleId): VodFavorites =
        VodFavorites(items.filterNot { it.title.id == titleId })

    /**
     * Переключает карточку и сообщает, добавлена она или убрана.
     *
     * @param title карточка;
     * @param atMillis момент переключения.
     */
    fun toggle(title: VodTitle, atMillis: Long): VodFavoriteToggle =
        if (contains(title.id)) {
            VodFavoriteToggle(favorites = remove(title.id), added = false)
        } else {
            VodFavoriteToggle(favorites = add(title, atMillis), added = true)
        }
}
