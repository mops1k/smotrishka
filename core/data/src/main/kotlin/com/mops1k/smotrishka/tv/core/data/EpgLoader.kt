package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.playlist.XmltvParseResult
import com.mops1k.smotrishka.tv.core.playlist.XmltvParser
import java.time.Instant

/**
 * Загрузка программы передач по адресу.
 *
 * Файл читается потоком и сразу обрезается окном: у крупных провайдеров XMLTV занимает
 * десятки мегабайт в gzip и сотни после распаковки, поэтому в память он не загружается.
 *
 * @param client HTTP-клиент с потоковой загрузкой;
 * @param parser разбор XMLTV.
 */
class EpgLoader(
    private val client: HttpBytesClient,
    private val parser: XmltvParser = XmltvParser(),
) {

    /**
     * Загружает и разбирает EPG.
     *
     * @param url адрес файла программы передач (xml или xml.gz);
     * @param from начало окна, `null` — без ограничения;
     * @param to конец окна, `null` — без ограничения.
     */
    suspend fun load(url: String, from: Instant? = null, to: Instant? = null): XmltvParseResult =
        when (
            val result = client.withStream(url) { stream ->
                parser.parseStream(stream, from = from, to = to)
            }
        ) {
            is HttpStreamResult.Success -> result.value

            is HttpStreamResult.HttpError -> XmltvParseResult.Failure(
                "Источник программы передач ответил кодом ${result.code}",
            )

            is HttpStreamResult.NetworkError -> XmltvParseResult.Failure(
                "Нет связи с источником программы передач: проверьте сеть",
            )
        }
}
