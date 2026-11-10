package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPortalPort

/**
 * Проверка портала и его категории через корневой запрос.
 *
 * Заодно запоминает шаблоны запросов категорий: в них есть поля, без которых категория
 * на портале пуста (`vc` у «Сериалов Турции» и «С наступающим!»).
 *
 * @param client транспорт портала;
 * @param templates шаблоны запросов категорий.
 */
class VPortalPortalAdapter(
    private val client: VPortalClient,
    private val templates: VPortalRequestTemplates,
) : VodPortalPort {

    override suspend fun loadCategories(config: VodPortalConfig): VodResult<List<VodCategory>> =
        when (val response = client.post(config.url.value, config.key.value, emptyMap())) {
            is VodResult.Success -> {
                templates.rememberFrom(response.value)
                VodResult.Success(response.value.toCategories())
            }
            is VodResult.Failure -> response
        }
}
