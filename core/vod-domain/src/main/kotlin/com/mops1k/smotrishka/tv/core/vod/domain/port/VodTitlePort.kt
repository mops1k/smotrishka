package com.mops1k.smotrishka.tv.core.vod.domain.port

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Карточка медиатеки: поток и серии по идентификатору.
 */
interface VodTitlePort {

    /**
     * Загружает детали карточки.
     *
     * @param config адрес и ключ портала;
     * @param titleId идентификатор карточки.
     */
    suspend fun loadDetails(
        config: VodPortalConfig,
        titleId: VodTitleId,
    ): VodResult<VodTitleDetails>

    /**
     * Варианты качества карточки или серии.
     *
     * Портал отдаёт их только отдельным запросом карточки, поэтому список качеств
     * запрашивается по требованию, а не при каждом открытии.
     *
     * @param config подключение портала;
     * @param titleId карточка или серия;
     * @return варианты качества по ключам портала.
     */
    suspend fun loadQualities(
        config: VodPortalConfig,
        titleId: VodTitleId,
    ): VodResult<Map<VodQuality, String>>
}
