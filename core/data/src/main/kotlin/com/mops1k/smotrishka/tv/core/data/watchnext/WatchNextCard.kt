package com.mops1k.smotrishka.tv.core.data.watchnext

/**
 * Карточка ленты «Сейчас идёт» на домашнем экране Android TV.
 *
 * Данные карточки не зависят от платформы: их строит [WatchNextCardsBuilder], а публикацией
 * в TvProvider занимается отдельный слой — так выбор каналов проверяется юнит-тестами.
 *
 * @param channelId идентификатор канала каталога;
 * @param title название канала;
 * @param subtitle текущая передача — она же вторая строка карточки;
 * @param logoUrl логотип канала, `null` — логотипа нет;
 * @param deepLink ссылка, открывающая этот канал сразу в плеере.
 */
data class WatchNextCard(
    val channelId: String,
    val title: String,
    val subtitle: String,
    val logoUrl: String?,
    val deepLink: String,
)
