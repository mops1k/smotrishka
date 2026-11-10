package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodTitlePort

/**
 * Детали карточки медиатеки: поток фильма или серии сезона.
 *
 * @param client транспорт портала.
 */
class VPortalTitleAdapter(private val client: VPortalClient) : VodTitlePort {

    override suspend fun loadDetails(
        config: VodPortalConfig,
        titleId: VodTitleId,
    ): VodResult<VodTitleDetails> {
        val payload = mapOf(FIELD_CMD to CMD_FLICK, FIELD_FID to titleId.value)
        return when (val response = client.post(config.url.value, config.key.value, payload)) {
            is VodResult.Success -> {
                // Карточка в ответе повторяет список, но если портал её не прислал —
                // показываем хотя бы название, чтобы экран не остался пустым.
                val card = response.value.toTitle()
                    ?: VodTitle(id = titleId, title = UNKNOWN_TITLE)
                VodResult.Success(response.value.toTitleDetails(fallback = card, listTitleId = titleId))
            }

            is VodResult.Failure -> response
        }
    }

    override suspend fun loadQualities(
        config: VodPortalConfig,
        titleId: VodTitleId,
    ): VodResult<Map<VodQuality, String>> {
        val payload = mapOf(FIELD_CMD to CMD_FLICK, FIELD_FID to titleId.value)
        return when (val response = client.post(config.url.value, config.key.value, payload)) {
            is VodResult.Success -> VodResult.Success(response.value.toQualityMap())
            is VodResult.Failure -> response
        }
    }

    private companion object {
        const val FIELD_CMD = "cmd"
        const val FIELD_FID = "fid"
        const val CMD_FLICK = "flick"
        const val UNKNOWN_TITLE = "Без названия"
    }
}
