package com.mops1k.smotrishka.tv.core.data

/**
 * Список недавно просмотренных каналов.
 *
 * Показывается по долгому удержанию кнопки «назад» в плеере: десять последних каналов, которые
 * действительно включали, свежие — первыми. Чистая логика без ввода-вывода: её удобно проверять
 * тестом, а хранилище только сохраняет результат.
 */
object RecentChannels {

    /** Сколько каналов помним. */
    const val LIMIT = 10

    /**
     * Добавляет канал в начало списка.
     *
     * Повторный выбор поднимает канал наверх, а не дублирует его; лишние отбрасываются.
     *
     * @param current текущий список, свежие первыми;
     * @param channelId выбранный канал;
     * @param limit сколько каналов хранить.
     */
    fun add(current: List<String>, channelId: String, limit: Int = LIMIT): List<String> {
        if (channelId.isBlank()) return current
        val withoutDuplicate = current.filterNot { it == channelId }
        return (listOf(channelId) + withoutDuplicate).take(limit)
    }
}
