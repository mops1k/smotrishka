package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.Playlist
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.playlist.M3uParser

/**
 * Загрузка плейлиста по коду провайдера.
 *
 * Провайдер отдаёт общий каталог каналов, где вместо настоящих адресов лежат заглушки.
 * После разбора в ссылки подставляются хост провайдера и код пользователя — без этого
 * каналы видны, но не играют.
 *
 * Все неприятности (пустой код, отказ сервера, отсутствие сети, пустой ответ)
 * возвращаются как [M3uParseResult.Failure] с понятным пользователю текстом.
 *
 * @param client HTTP-клиент;
 * @param provider профиль провайдера;
 * @param parser разбор M3U.
 */
class IedemPlaylistLoader(
    private val client: HttpTextClient,
    private val provider: IedemProvider = IedemProvider(),
    private val parser: M3uParser = M3uParser(),
) {

    /**
     * Загружает каталог и подставляет в каналы код пользователя.
     *
     * @param code код провайдера.
     */
    suspend fun load(code: String): M3uParseResult {
        val accepted = when (val codeResult = provider.validate(code)) {
            is IedemCodeResult.Invalid -> return M3uParseResult.Failure(codeResult.reason)
            is IedemCodeResult.Success -> codeResult.code
        }

        val url = provider.channelListUrl()

        return when (val response = client.fetch(url)) {
            is HttpTextResult.Success -> when (val parsed = parser.parse(text = response.text, baseUrl = url)) {
                is M3uParseResult.Success -> M3uParseResult.Success(parsed.playlist.withProviderStreams(accepted))
                is M3uParseResult.Failure -> parsed
            }

            is HttpTextResult.HttpError -> M3uParseResult.Failure(
                "Код не подошёл или iedem.tv недоступен (ответ ${response.code})",
            )

            is HttpTextResult.NetworkError -> M3uParseResult.Failure(
                "Нет связи с iedem.tv: проверьте сеть на устройстве",
            )

            HttpTextResult.EmptyBody -> M3uParseResult.Failure(
                "iedem.tv вернул пустой список каналов — проверьте код",
            )
        }
    }

    /**
     * Переписывает ссылки каналов на настоящие.
     *
     * Канал, из ссылки которого не удалось достать идентификатор, остаётся как есть:
     * лучше показать канал с нерабочей ссылкой, чем потерять его из каталога.
     */
    private fun Playlist.withProviderStreams(code: String): Playlist = copy(
        channels = channels.map { channel -> channel.withProviderStream(code) },
    )

    private fun Channel.withProviderStream(code: String): Channel {
        val channelId = provider.channelIdOf(streamUrl) ?: return this
        return copy(streamUrl = provider.streamUrl(code = code, channelId = channelId))
    }
}
