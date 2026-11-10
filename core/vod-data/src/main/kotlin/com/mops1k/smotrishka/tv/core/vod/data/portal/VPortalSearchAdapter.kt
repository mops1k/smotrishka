package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodSearchPort

/**
 * Поиск по медиатеке на стороне портала.
 *
 * @param client транспорт портала.
 */
class VPortalSearchAdapter(private val client: VPortalClient) : VodSearchPort {

    override suspend fun search(
        config: VodPortalConfig,
        query: String,
        offset: Int,
    ): VodResult<List<VodTitle>> {
        val payload = mapOf(
            FIELD_CMD to CMD_SEARCH,
            FIELD_QUERY to query,
            FIELD_OFFSET to offset,
        )
        return when (val response = client.post(config.url.value, config.key.value, payload)) {
            is VodResult.Success -> VodResult.Success(response.value.toTitles())
            is VodResult.Failure -> response
        }
    }

    private companion object {
        const val FIELD_CMD = "cmd"
        const val FIELD_QUERY = "query"
        const val FIELD_OFFSET = "offset"
        const val CMD_SEARCH = "search"
    }
}
