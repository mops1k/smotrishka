package com.mops1k.smotrishka.tv.core.vod.domain.port

import com.mops1k.smotrishka.tv.core.vod.domain.VodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig

/**
 * Каталог медиатеки: страницы категории с курсором.
 */
interface VodCatalogPort {

    /**
     * Загружает страницу категории.
     *
     * @param config адрес и ключ портала;
     * @param categoryId категория;
     * @param offset смещение от начала категории;
     * @param limit размер страницы.
     */
    suspend fun loadPage(
        config: VodPortalConfig,
        categoryId: VodCategoryId,
        offset: Int,
        limit: Int,
    ): VodResult<VodCatalogPage>

    companion object {

        /**
         * Размер страницы по умолчанию: столько портал отдаёт без явного `limit`.
         * Сетка на экране шире, поэтому догрузка идёт страницами портала.
         */
        const val DEFAULT_PAGE_SIZE: Int = 31
    }
}
