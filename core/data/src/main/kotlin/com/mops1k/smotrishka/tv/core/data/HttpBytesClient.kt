package com.mops1k.smotrishka.tv.core.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

/**
 * Загружает тела ответов как байты или как поток.
 *
 * Байтовый вариант нужен для небольших файлов, потоковый — для программы передач: она бывает
 * в десятки мегабайт, и держать её в памяти целиком нельзя, иначе приложение падает по OOM
 * (проверено на файле 49 МБ). Поток отдаётся в блок и закрывается автоматически.
 *
 * @param client HTTP-клиент OkHttp.
 */
class HttpBytesClient(private val client: OkHttpClient) {

    /**
     * Загружает байты по адресу.
     *
     * @param url адрес источника;
     * @param headers дополнительные заголовки.
     */
    suspend fun fetch(url: String, headers: Map<String, String> = emptyMap()): HttpBytesResult =
        withContext(Dispatchers.IO) {
            val request = buildRequest(url, headers)
                ?: return@withContext HttpBytesResult.NetworkError("Некорректный адрес программы передач")

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use HttpBytesResult.HttpError(response.code)
                    }

                    val bytes = response.body.bytes()
                    if (bytes.isEmpty()) HttpBytesResult.EmptyBody else HttpBytesResult.Success(bytes)
                }
            } catch (e: IOException) {
                HttpBytesResult.NetworkError(e.message ?: "нет соединения")
            }
        }

    /**
     * Читает ответ потоком, не загружая его в память целиком.
     *
     * @param url адрес источника;
     * @param headers дополнительные заголовки;
     * @param block что сделать с потоком; поток закрывается сразу после блока.
     */
    suspend fun <T> withStream(
        url: String,
        headers: Map<String, String> = emptyMap(),
        block: (InputStream) -> T,
    ): HttpStreamResult<T> = withContext(Dispatchers.IO) {
        val request = buildRequest(url, headers)
            ?: return@withContext HttpStreamResult.NetworkError("Некорректный адрес программы передач")

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use HttpStreamResult.HttpError(response.code)
                }

                val body = response.body.byteStream()
                HttpStreamResult.Success(block(body))
            }
        } catch (e: IOException) {
            HttpStreamResult.NetworkError(e.message ?: "нет соединения")
        }
    }

    private fun buildRequest(url: String, headers: Map<String, String>): Request? =
        try {
            Request.Builder()
                .url(url)
                .header(USER_AGENT, DEFAULT_USER_AGENT)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
        } catch (e: IllegalArgumentException) {
            null
        }

    private companion object {
        const val USER_AGENT = "User-Agent"
        const val DEFAULT_USER_AGENT = "SmotrishkaTV/0.1 (Android)"
    }
}
