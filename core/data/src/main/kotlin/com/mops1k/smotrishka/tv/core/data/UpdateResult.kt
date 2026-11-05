package com.mops1k.smotrishka.tv.core.data

/**
 * Итог обновления данных.
 */
sealed interface UpdateResult {

    /**
     * Обновлены и каталог, и программа передач.
     *
     * @param channels сколько каналов сохранено;
     * @param categories сколько категорий;
     * @param programmes сколько передач.
     */
    data class Success(val channels: Int, val categories: Int, val programmes: Int) : UpdateResult

    /**
     * Каталог обновился, программа передач — нет (или её адреса нет в плейлисте).
     *
     * @param reason что именно не получилось;
     * @param channels сколько каналов сохранено;
     * @param categories сколько категорий;
     * @param programmes сколько передач дали пользовательские гиды.
     */
    data class Partial(
        val reason: String,
        val channels: Int,
        val categories: Int,
        val programmes: Int = 0,
    ) : UpdateResult

    /**
     * Обновить не удалось: прежний каталог оставлен как есть.
     *
     * @param reason причина.
     */
    data class Failure(val reason: String) : UpdateResult
}
