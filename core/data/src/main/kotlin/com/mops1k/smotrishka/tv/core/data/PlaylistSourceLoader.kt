package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.playlist.M3uParser

/**
 * Загружает плейлист для любого настроенного источника.
 *
 * Каждый тип источника — свой способ получения каталога: M3U по ссылке, код провайдера,
 * Xtream-логин (через M3U-выдачу провайдера, чтобы каталог собирался одним парсером).
 *
 * @param httpText текстовый HTTP-клиент;
 * @param iedem загрузчик провайдера по коду;
 * @param fileReader чтение плейлиста из файловой системы;
 * @param parser разбор M3U.
 */
class PlaylistSourceLoader(
    private val httpText: HttpTextClient,
    private val iedem: IedemPlaylistLoader,
    private val fileReader: PlaylistFileReader,
    private val parser: M3uParser = M3uParser(),
) {

    /**
     * Загружает плейлист источника.
     *
     * @param source настроенный источник.
     */
    suspend fun load(source: PlaylistSource): M3uParseResult =
        when (source) {
            is PlaylistSource.M3uUrl -> loadUrl(source.url)
            is PlaylistSource.IedemCode -> iedem.load(source.code)
            is PlaylistSource.File -> fileReader.read(source.uri)
            is PlaylistSource.Xtream -> XtreamClient(
                client = httpText,
                serverUrl = source.server,
                username = source.username,
                password = source.password,
                parser = parser,
            ).m3uPlaylist()
        }

    private suspend fun loadUrl(url: String): M3uParseResult =
        when (val response = httpText.fetch(url)) {
            is HttpTextResult.Success -> parser.parse(text = response.text, baseUrl = url)
            is HttpTextResult.HttpError -> M3uParseResult.Failure("Источник ответил кодом ${response.code}")
            is HttpTextResult.NetworkError -> M3uParseResult.Failure("Нет связи с источником: проверьте сеть")
            HttpTextResult.EmptyBody -> M3uParseResult.Failure("Источник вернул пустой плейлист")
        }
}
