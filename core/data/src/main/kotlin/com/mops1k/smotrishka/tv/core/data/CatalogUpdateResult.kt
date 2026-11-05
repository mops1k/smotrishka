package com.mops1k.smotrishka.tv.core.data

/**
 * Результат загрузки только каталога каналов.
 *
 * Программа передач грузится отдельно: гид бывает в десятки мегабайт, и ждать его разбора
 * на экране подключения нельзя — каналы должны появляться сразу.
 */
sealed interface CatalogUpdateResult {

    /**
     * Каталог заменён.
     *
     * @param channels сколько каналов в каталоге;
     * @param categories сколько категорий;
     * @param epgUrl адрес программы передач из плейлиста, если он там указан.
     */
    data class Success(
        val channels: Int,
        val categories: Int,
        val epgUrl: String?,
    ) : CatalogUpdateResult

    /**
     * Каталог не загружен, прежний остался как был.
     *
     * @param reason причина для пользователя.
     */
    data class Failure(val reason: String) : CatalogUpdateResult
}
