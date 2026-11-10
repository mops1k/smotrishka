package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.google.gson.JsonObject

/**
 * Сборка запроса страницы каталога.
 *
 * За основу берётся шаблон категории из корневого ответа — в нём есть поля, которые портал
 * считает обязательными (`vc` у двух категорий). Смещение и размер страницы подставляются
 * свои, остальное переносится из шаблона без изменений.
 *
 * @param template шаблон из корневого ответа, `null` — категорию ещё не загружали;
 * @param fid идентификатор категории;
 * @param offset первая карточка страницы;
 * @param limit сколько карточек запросить.
 */
internal fun pageRequest(template: JsonObject?, fid: Int, offset: Int, limit: Int): JsonObject {
    val payload = template?.deepCopy() ?: JsonObject()
    payload.addProperty(FIELD_CMD, CMD_FLICKS)
    payload.addProperty(FIELD_FID, fid)
    payload.addProperty(FIELD_OFFSET, offset)
    payload.addProperty(FIELD_LIMIT, limit)
    return payload
}

/**
 * Запоминает шаблоны запросов всех категорий из корневого ответа.
 *
 * @param root корневой ответ портала.
 */
internal fun VPortalRequestTemplates.rememberFrom(root: JsonObject) {
    root.getAsJsonArray(FIELD_ITEMS)?.forEach { element ->
        val request = element.asJsonObject.getAsJsonObject(FIELD_REQUEST) ?: return@forEach
        val fid = request.get(FIELD_FID)?.takeIf { !it.isJsonNull }?.asInt ?: return@forEach
        remember(fid, request)
    }
}

private const val FIELD_REQUEST = "request"
private const val FIELD_ITEMS = "items"
private const val FIELD_CMD = "cmd"
private const val FIELD_FID = "fid"
private const val FIELD_OFFSET = "offset"
private const val FIELD_LIMIT = "limit"
private const val CMD_FLICKS = "flicks"
