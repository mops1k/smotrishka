package com.mops1k.smotrishka.tv.core.vod.domain.port

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle

/**
 * Поиск по медиатеке на стороне портала.
 */
interface VodSearchPort {

    /**
     * Ищет карточки по строке.
     *
     * @param config адрес и ключ портала;
     * @param query строка поиска;
     * @param offset смещение для догрузки результатов.
     */
    suspend fun search(
        config: VodPortalConfig,
        query: String,
        offset: Int,
    ): VodResult<List<VodTitle>>
}
