package com.mops1k.smotrishka.tv.core.vod.domain

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle

/**
 * Агрегат каталога: какие категории есть, какая выбрана и что уже загружено.
 *
 * Инварианты, которые держит сам агрегат:
 * - выбор категории всегда сбрасывает загруженные карточки и курсор — иначе на экране
 *   смешались бы две категории;
 * - дозагрузка страницы склеивает карточки без дублей по идентификатору: портал может
 *   повторить карточку на границе страниц;
 * - `nextOffset = null` означает «дальше ничего нет», и догружать больше нечего.
 */
data class VodLibrary(
    val categories: List<VodCategory> = emptyList(),
    val selectedCategoryId: VodCategoryId? = null,
    val titles: List<VodTitle> = emptyList(),
    val nextOffset: Int? = null,
    val total: Int? = null,
) {

    /** Выбранная категория, если она ещё существует в списке. */
    val selectedCategory: VodCategory?
        get() = categories.firstOrNull { it.id == selectedCategoryId }

    /** Можно ли догрузить следующую страницу. */
    val hasMore: Boolean
        get() = nextOffset != null

    /** Показывать ли пустое состояние: категория выбрана, но карточек нет. */
    val isEmpty: Boolean
        get() = selectedCategoryId != null && titles.isEmpty()

    /**
     * Ставит категории из корневого ответа, сохраняя выбор, если он ещё существует.
     *
     * @param categories категории портала в его порядке.
     */
    fun withCategories(categories: List<VodCategory>): VodLibrary {
        val stillExists = categories.any { it.id == selectedCategoryId }
        return copy(
            categories = categories,
            selectedCategoryId = if (stillExists) selectedCategoryId else categories.firstOrNull()?.id,
            titles = if (stillExists) titles else emptyList(),
            nextOffset = if (stillExists) nextOffset else null,
            total = if (stillExists) total else null,
        )
    }

    /**
     * Выбирает категорию и очищает загруженное.
     *
     * @param categoryId идентификатор категории.
     */
    fun select(categoryId: VodCategoryId): VodLibrary = copy(
        selectedCategoryId = categoryId,
        titles = emptyList(),
        nextOffset = null,
        total = null,
    )

    /**
     * Добавляет страницу в конец списка.
     *
     * @param page полученная страница.
     */
    fun append(page: VodCatalogPage): VodLibrary = copy(
        titles = (titles + page.titles).distinctBy { it.id },
        nextOffset = page.nextOffset,
        total = page.total ?: total,
    )
}
