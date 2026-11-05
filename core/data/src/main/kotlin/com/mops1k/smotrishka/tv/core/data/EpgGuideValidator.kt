package com.mops1k.smotrishka.tv.core.data

import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Проверка гида до сохранения: адрес должен отвечать и отдавать XMLTV.
 *
 * Гида нельзя записывать в базу без проверки: иначе фоновое обновление каждые 6 часов
 * ходит в мёртвый адрес и молча получает 404 или HTML-страницу вместо программы.
 * Читается только начало файла (первые байты), поэтому проверка не зависит от размера
 * гида: 47 МБ не скачиваются целиком.
 *
 * @param client HTTP-клиент с потоковой загрузкой.
 */
class EpgGuideValidator(private val client: HttpBytesClient) {

    /**
     * Проверяет адрес гида.
     *
     * @param url адрес XMLTV.
     */
    suspend fun check(url: String): EpgGuideCheck {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return EpgGuideCheck.Unavailable("Адрес должен начинаться с http:// или https://")
        }

        return when (val result = client.withStream(url) { stream -> isXmltvHead(stream) }) {
            is HttpStreamResult.Success ->
                if (result.value) EpgGuideCheck.Available
                else EpgGuideCheck.Unavailable("По адресу не XMLTV-гид")

            is HttpStreamResult.HttpError ->
                EpgGuideCheck.Unavailable("Источник ответил кодом ${result.code}")

            is HttpStreamResult.NetworkError ->
                EpgGuideCheck.Unavailable("Гид недоступен: ${result.reason}")
        }
    }

    private fun isXmltvHead(stream: InputStream): Boolean {
        val buffered = stream.buffered()
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()

        val input = if (first == GZIP_FIRST && second == GZIP_SECOND) {
            GZIPInputStream(buffered)
        } else {
            buffered
        }

        val head = ByteArray(HEAD_BYTES)
        val read = try {
            input.read(head)
        } catch (e: IOException) {
            -1
        }
        if (read <= 0) return false

        val text = String(head, 0, read, Charsets.UTF_8)
        return text.contains("<tv") || text.contains("<programme") || text.contains("<?xml")
    }

    private companion object {
        const val HEAD_BYTES = 512
        const val GZIP_FIRST = 0x1f
        const val GZIP_SECOND = 0x8b
    }
}
