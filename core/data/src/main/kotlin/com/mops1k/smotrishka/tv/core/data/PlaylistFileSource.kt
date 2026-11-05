package com.mops1k.smotrishka.tv.core.data

import android.content.ContentResolver
import android.net.Uri
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.playlist.M3uParser
import com.mops1k.smotrishka.tv.core.playlist.M3uTextDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Чтение плейлиста из файла, выбранного пользователем (Storage Access Framework).
 *
 * Класс — тонкая обёртка над `ContentResolver`: вся логика разбора и определения
 * кодировки лежит в `:core:playlist` и покрыта юнит-тестами, здесь только ввод-вывод.
 *
 * @param contentResolver резолвер, через который открывается `content://`-адрес;
 * @param parser парсер M3U;
 * @param decoder декодер кодировки.
 */
class PlaylistFileSource(
    private val contentResolver: ContentResolver,
    private val parser: M3uParser = M3uParser(),
    private val decoder: M3uTextDecoder = M3uTextDecoder(),
) : PlaylistFileReader {

    /**
     * Читает и разбирает плейлист по строковому адресу.
     *
     * @param uri адрес файла из системного выбора (`content://`).
     */
    override suspend fun read(uri: String): M3uParseResult = read(Uri.parse(uri))

    /**
     * Читает и разбирает плейлист.
     *
     * @param uri адрес файла, полученный от системы (SAF или `file://`);
     * @return результат разбора либо явная ошибка чтения.
     */
    suspend fun read(uri: Uri): M3uParseResult = withContext(Dispatchers.IO) {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return@withContext M3uParseResult.Failure("Не удалось открыть файл плейлиста")

        parser.parse(text = decoder.decode(bytes), baseUrl = uri.toString())
    }
}
