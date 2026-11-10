package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.google.gson.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Шаблоны запросов категорий, которые портал присылает вместе с корневым ответом.
 *
 * Запрос категории нельзя собирать «с нуля»: портал кладёт в него свои поля (например `vc: 1`
 * для «Сериалов Турции» и «С наступающим!»). Потеря такого поля делает категорию пустой,
 * поэтому шаблон запоминается при чтении корня и повторяется при листании.
 */
@Singleton
class VPortalRequestTemplates @Inject constructor() {

    private val templates = mutableMapOf<Int, JsonObject>()

    /**
     * Запоминает шаблон запроса категории.
     *
     * @param fid идентификатор категории;
     * @param request запрос, который прислал портал.
     */
    fun remember(fid: Int, request: JsonObject) {
        templates[fid] = request
    }

    /**
     * Шаблон запроса категории.
     *
     * @param fid идентификатор категории;
     * @return шаблон либо `null`, если портал его ещё не присылал.
     */
    fun template(fid: Int): JsonObject? = templates[fid]
}
