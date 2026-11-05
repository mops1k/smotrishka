package com.mops1k.smotrishka.tv.core.data

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import com.mops1k.smotrishka.tv.core.model.Category
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.playlist.M3uParser
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Клиент Xtream Codes: вход, категории, каналы, EPG и M3U-выдача.
 *
 * Адреса строятся от `serverUrl` (например `http://example.com:8080`), логин и пароль
 * передаются query-параметрами. Ответы разбираются gson; любое расхождение с ожидаемым
 * форматом возвращается как явная ошибка, а не как исключение.
 *
 * @param client HTTP-клиент;
 * @param serverUrl адрес сервера провайдера без завершающего слэша;
 * @param username логин Xtream;
 * @param password пароль Xtream;
 * @param parser разбор M3U для режима `get.php`;
 * @param gson JSON-парсер.
 */
class XtreamClient(
    private val client: HttpTextClient,
    private val serverUrl: String,
    private val username: String,
    private val password: String,
    private val parser: M3uParser = M3uParser(),
    private val gson: Gson = Gson(),
) {

    /**
     * Проверяет вход и возвращает сведения об аккаунте.
     */
    suspend fun authenticate(): XtreamAuthResult {
        val json = when (val body = fetchBody(apiUrl())) {
            is Body.Http -> return XtreamAuthResult.HttpError(body.code)
            is Body.Network -> return XtreamAuthResult.NetworkError(body.reason)
            Body.Empty -> return XtreamAuthResult.Malformed("Пустой ответ сервера")
            is Body.Text -> body.value
        }

        val dto = try {
            gson.fromJson(json, XtreamAuthDto::class.java)
        } catch (e: JsonParseException) {
            null
        } ?: return XtreamAuthResult.Malformed("Сервер ответил не JSON — проверьте адрес сервера")

        val userInfo = dto.userInfo
            ?: return XtreamAuthResult.Malformed("В ответе нет данных аккаунта (user_info)")

        if (userInfo.auth != 1) {
            return XtreamAuthResult.InvalidCredentials
        }

        if (userInfo.status.equals("Expired", ignoreCase = true)) {
            return XtreamAuthResult.Expired(userInfo.expDate)
        }

        return XtreamAuthResult.Success(
            XtreamAccount(
                username = userInfo.username ?: username,
                isTrial = userInfo.isTrial == "1",
                expiresAt = userInfo.expDate,
            ),
        )
    }

    /**
     * Категории линейных каналов.
     */
    suspend fun categories(): XtreamResult<List<Category>> =
        when (val body = fetchBody(apiUrl("action" to "get_live_categories"))) {
            is Body.Text -> parseArray(body.value, Array<XtreamCategoryDto>::class.java) { items ->
                items.mapNotNull { dto ->
                    val id = dto.categoryId?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                    val name = dto.categoryName?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
                    Category(id = id, name = name)
                }
            }

            is Body.Http -> XtreamResult.Failure("Сервер ответил кодом ${body.code}")
            is Body.Network -> XtreamResult.Failure("Нет связи с сервером: ${body.reason}")
            Body.Empty -> XtreamResult.Failure("Пустой ответ сервера")
        }

    /**
     * Каналы линейного вещания с адресами потоков и параметрами архива.
     */
    suspend fun liveStreams(): XtreamResult<List<Channel>> =
        when (val body = fetchBody(apiUrl("action" to "get_live_streams"))) {
            is Body.Text -> parseArray(body.value, Array<XtreamStreamDto>::class.java) { items ->
                items.mapNotNull(::toChannel)
            }

            is Body.Http -> XtreamResult.Failure("Сервер ответил кодом ${body.code}")
            is Body.Network -> XtreamResult.Failure("Нет связи с сервером: ${body.reason}")
            Body.Empty -> XtreamResult.Failure("Пустой ответ сервера")
        }

    /** Адрес программы передач Xtream (`xmltv.php`). */
    fun epgUrl(): String = baseUrl.newBuilder()
        .addPathSegment("xmltv.php")
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .build()
        .toString()

    /** Адрес M3U-выдачи провайдера (`get.php`). */
    fun m3uUrl(): String = baseUrl.newBuilder()
        .addPathSegment("get.php")
        .addQueryParameter("username", username)
        .addQueryParameter("password", password)
        .addQueryParameter("type", "m3u_plus")
        .addQueryParameter("output", "m3u8")
        .build()
        .toString()

    /** Адрес прямого потока канала. */
    fun streamUrl(streamId: Long): String = baseUrl.newBuilder()
        .addPathSegment("live")
        .addPathSegment(username)
        .addPathSegment(password)
        .addPathSegment("$streamId.m3u8")
        .build()
        .toString()

    /**
     * Загружает M3U-выдачу провайдера и разбирает её тем же парсером, что и обычные плейлисты.
     */
    suspend fun m3uPlaylist(): M3uParseResult {
        val url = m3uUrl()
        return when (val body = fetchBody(url)) {
            is Body.Text -> parser.parse(text = body.value, baseUrl = url)
            is Body.Http -> M3uParseResult.Failure("Сервер ответил кодом ${body.code}")
            is Body.Network -> M3uParseResult.Failure("Нет связи с сервером: ${body.reason}")
            Body.Empty -> M3uParseResult.Failure("Провайдер вернул пустой плейлист")
        }
    }

    private val baseUrl: HttpUrl get() = serverUrl.toHttpUrl()

    private fun apiUrl(vararg params: Pair<String, String>): String =
        baseUrl.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", username)
            .addQueryParameter("password", password)
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()
            .toString()

    /** Разбирает JSON-массив сервера в доменные объекты. */
    private fun <T, R> parseArray(
        json: String,
        type: Class<Array<T>>,
        mapper: (List<T>) -> R,
    ): XtreamResult<R> =
        try {
            val items = gson.fromJson(json, type)
                ?: return XtreamResult.Failure("Сервер ответил не JSON — проверьте адрес сервера")
            XtreamResult.Success(mapper(items.toList()))
        } catch (e: JsonParseException) {
            XtreamResult.Failure("Сервер ответил не JSON — проверьте адрес сервера")
        }

    /** Переводит поток Xtream в доменный канал. */
    private fun toChannel(dto: XtreamStreamDto): Channel? {
        val streamId = dto.streamId ?: return null
        val name = dto.name?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val epgId = dto.epgChannelId?.trim()?.takeIf(String::isNotEmpty)

        return Channel(
            id = epgId?.let(::ChannelId) ?: ChannelId("xtream:$streamId"),
            name = name,
            streamUrl = streamUrl(streamId),
            logoUrl = dto.streamIcon?.trim()?.takeIf(String::isNotEmpty),
            categoryId = dto.categoryId?.trim()?.takeIf(String::isNotEmpty),
            epgId = epgId,
            archive = if (dto.tvArchive == 1) {
                ArchiveInfo(mode = CatchupMode.DEFAULT, days = dto.tvArchiveDuration ?: 0)
            } else {
                null
            },
        )
    }

    /** Загружает тело ответа, различая HTTP, сеть и пустой ответ. */
    private suspend fun fetchBody(url: String): Body =
        when (val result = client.fetch(url)) {
            is HttpTextResult.Success -> Body.Text(result.text)
            is HttpTextResult.HttpError -> Body.Http(result.code)
            is HttpTextResult.NetworkError -> Body.Network(result.reason)
            HttpTextResult.EmptyBody -> Body.Empty
        }

    private sealed interface Body {
        data class Text(val value: String) : Body
        data class Http(val code: Int) : Body
        data class Network(val reason: String) : Body
        data object Empty : Body
    }
}
