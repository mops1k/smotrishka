package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult

/**
 * Чтение плейлиста по адресу `content://` — файл, выбранный системным проводником.
 *
 * Интерфейс отделяет загрузчик источников от `ContentResolver`: разбор и кодировка живут
 * в `:core:playlist`, ввод-вывод — в реализации, а тесты подставляют свои байты.
 */
fun interface PlaylistFileReader {

    /**
     * Читает и разбирает плейлист.
     *
     * @param uri адрес файла из системного выбора.
     */
    suspend fun read(uri: String): M3uParseResult
}
