package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.mops1k.smotrishka.tv.core.vod.domain.VodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodCatalogPort

/**
 * Страницы каталога медиатеки.
 *
 * Запрос страницы собирается на основе шаблона категории: портал присылает в нём обязательные
 * поля, которые нельзя терять.
 *
 * @param client транспорт портала;
 * @param templates шаблоны запросов категорий.
 */
class VPortalCatalogAdapter(
    private val client: VPortalClient,
    private val templates: VPortalRequestTemplates,
) : VodCatalogPort {

    override suspend fun loadPage(
        config: VodPortalConfig,
        categoryId: VodCategoryId,
        offset: Int,
        limit: Int,
    ): VodResult<VodCatalogPage> {
        val payload = pageRequest(
            template = templates.template(categoryId.value),
            fid = categoryId.value,
            offset = offset,
            limit = limit,
        )
        return when (val response = client.post(config.url.value, config.key.value, payload)) {
            is VodResult.Success -> VodResult.Success(response.value.toCatalogPage())
            is VodResult.Failure -> response
        }
    }

}
