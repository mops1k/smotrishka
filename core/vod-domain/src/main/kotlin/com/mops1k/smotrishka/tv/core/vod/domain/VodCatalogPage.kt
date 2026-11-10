package com.mops1k.smotrishka.tv.core.vod.domain

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle

/**
 * Одна страница каталога медиатеки.
 *
 * @property titles карточки страницы в порядке портала;
 * @property nextOffset смещение следующей страницы, `null` — категория закончилась;
 * @property total сколько всего карточек в категории, если портал сообщил.
 */
data class VodCatalogPage(
    val titles: List<VodTitle>,
    val nextOffset: Int?,
    val total: Int? = null,
) {

    /** Есть ли что догружать дальше. */
    val hasMore: Boolean
        get() = nextOffset != null
}
