package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Категория медиатеки из корневого ответа портала.
 *
 * Порядок категорий задаёт провайдер, приложение его не сортирует. Пустая категория
 * (`total = 0`) — нормальный случай: например, сезонная подборка «С наступающим!» вне сезона.
 *
 * @property id идентификатор категории;
 * @property title название категории;
 * @property total сколько карточек в категории, если портал сообщил.
 */
data class VodCategory(
    val id: VodCategoryId,
    val title: String,
    val total: Int? = null,
)
