package com.mops1k.smotrishka.tv.core.vod.domain.port

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig

/**
 * Портал медиатеки: проверка подключения и категории из корневого ответа.
 */
interface VodPortalPort {

    /**
     * Проверяет портал корневым запросом.
     *
     * @param config адрес и ключ портала;
     * @return категории, если портал принял ключ.
     */
    suspend fun loadCategories(config: VodPortalConfig): VodResult<List<VodCategory>>
}
