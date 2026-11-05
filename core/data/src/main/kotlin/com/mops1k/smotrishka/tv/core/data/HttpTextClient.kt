package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.playlist.M3uTextDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Загружает текстовые ответы провайдеров (плейлисты, EPG) и сразу приводит их к строке.
 *
 * Тело декодируется [M3uTextDecoder]: провайдеры отдают и UTF-8, и cp1251.
 * Любая сетевая проблема возвращается значением [HttpTextResult], а не исключением.
 *
 * @param client HTTP-клиент OkHttp;
 * @param decoder определение кодировки тела.
 */
class HttpTextClient(
    private val client: OkHttpClient,
    private val decoder: M3uTextDecoder = M3uTextDecoder(),
) {

    /**
     * Загружает текст по адресу.
     *
     * @param url адрес источника;
     * @param headers дополнительные заголовки (Referer, Cookie и т.п.).
     */
    suspend fun fetch(url: String, headers: Map<String, String> = emptyMap()): HttpTextResult =
        withContext(Dispatchers.IO) {
            val request = try {
                Request.Builder()
                    .url(url)
                    .header(USER_AGENT, DEFAULT_USER_AGENT)
                    .apply {
                        headers.forEach { (name, value) -> header(name, value) }
                    }
                    .build()
            } catch (e: IllegalArgumentException) {
                return@withContext HttpTextResult.NetworkError("Некорректный адрес источника")
            }

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use HttpTextResult.HttpError(response.code)
                    }

                    val bytes = response.body.bytes()
                    if (bytes.isEmpty()) {
                        HttpTextResult.EmptyBody
                    } else {
                        HttpTextResult.Success(decoder.decode(bytes))
                    }
                }
            } catch (e: IOException) {
                HttpTextResult.NetworkError(e.message ?: "нет соединения")
            }
        }

    private companion object {
        const val USER_AGENT = "User-Agent"
        const val DEFAULT_USER_AGENT = "SmotrishkaTV/0.1 (Android)"
    }
}
