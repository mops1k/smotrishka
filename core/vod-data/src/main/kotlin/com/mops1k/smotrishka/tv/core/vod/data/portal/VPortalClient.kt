package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Транспорт портала медиатеки: единственный `POST` с JSON-телом, в которое подмешивается ключ.
 *
 * Портал отвечает `200` даже на отказы, поэтому различать успех и ошибку приходится по полю
 * `type`: `"error"` даёт доменную причину, всё остальное — успешный объект. Сеть, коды и битый
 * JSON сводятся к [VodFailure], исключения через эту границу не выходят.
 *
 * @param client HTTP-клиент приложения (таймауты и повторы общие с остальными запросами);
 * @param gson сериализация запросов.
 */
class VPortalClient(
    private val client: OkHttpClient,
    private val gson: Gson = Gson(),
) {

    /**
     * Отправляет запрос на портал.
     *
     * @param url адрес портала (уже нормализованный);
     * @param key ключ портала — подмешивается в тело;
     * @param payload поля запроса портала (`cmd`, `fid`, `offset`, `query` и т.п.).
     */
    suspend fun post(
        url: String,
        key: String,
        payload: Map<String, Any?>,
    ): VodResult<JsonObject> = postObject(
        url = url,
        key = key,
        payload = JsonObject().apply {
            payload.forEach { (name, value) -> add(name, gson.toJsonTree(value)) }
        },
    )

    /**
     * Отправляет запрос на портал готовым телом — так передаются шаблоны категорий,
     * в которых есть обязательные поля портала (например `vc`).
     *
     * @param url адрес портала (уже нормализованный);
     * @param key ключ портала — подмешивается в тело;
     * @param payload готовое тело запроса.
     */
    suspend fun post(
        url: String,
        key: String,
        payload: JsonObject,
    ): VodResult<JsonObject> = postObject(url = url, key = key, payload = payload)

    private suspend fun postObject(
        url: String,
        key: String,
        payload: JsonObject,
    ): VodResult<JsonObject> = withContext(Dispatchers.IO) {
        val body = payload.deepCopy().apply { addProperty(KEY_FIELD, key) }

        val request = try {
            Request.Builder()
                .url(url)
                .header(CONTENT_TYPE_HEADER, CONTENT_TYPE_JSON)
                .post(body.toString().toRequestBody(CONTENT_TYPE_JSON.toMediaType()))
                .build()
        } catch (e: IllegalArgumentException) {
            // Некорректный адрес — это не сеть: пользователю нужно править настройку.
            return@withContext VodResult.Failure(VodFailure.NotConfigured)
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@use VodResult.Failure(VodFailure.Http(response.code))
                }
                val text = response.body.string()
                val json = parse(text) ?: return@use VodResult.Failure(VodFailure.BadFormat)

                if (json.get(FIELD_TYPE)?.asString == TYPE_ERROR) {
                    val description = json.get(FIELD_DESCRIPTION)?.asString
                    VodResult.Failure(mapError(description))
                } else {
                    VodResult.Success(json)
                }
            }
        } catch (e: IOException) {
            VodResult.Failure(VodFailure.Network(e.message))
        }
    }

    /** Разбирает текст ответа, возвращая `null` вместо исключения. */
    private fun parse(text: String): JsonObject? = runCatching {
        JsonParser.parseString(text).asJsonObject
    }.getOrNull()

    /** Переводит описание ошибки портала в доменную причину. */
    private fun mapError(description: String?): VodFailure = when {
        description == ERROR_SUBSCRIPTION -> VodFailure.SubscriptionRequired
        else -> VodFailure.PortalRejected(description)
    }

    private companion object {
        const val KEY_FIELD = "key"
        const val FIELD_TYPE = "type"
        const val FIELD_DESCRIPTION = "description"
        const val TYPE_ERROR = "error"
        const val ERROR_SUBSCRIPTION = "subscription required"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val CONTENT_TYPE_JSON = "application/json"
    }
}
