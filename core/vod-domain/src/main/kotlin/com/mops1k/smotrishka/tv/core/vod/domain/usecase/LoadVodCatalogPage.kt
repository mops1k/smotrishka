package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodCatalogPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.connectedConfig

/**
 * Загружает страницу каталога медиатеки.
 *
 * @param catalogPort каталог портала;
 * @param configPort хранилище подключения.
 */
class LoadVodCatalogPage(
    private val catalogPort: VodCatalogPort,
    private val configPort: VodConfigPort,
) {

    /**
     * Загружает страницу категории.
     *
     * @param categoryId категория;
     * @param offset смещение от начала категории;
     * @param limit размер страницы.
     */
    suspend operator fun invoke(
        categoryId: VodCategoryId,
        offset: Int,
        limit: Int = VodCatalogPort.DEFAULT_PAGE_SIZE,
    ): VodResult<VodCatalogPage> =
        when (val config = configPort.connectedConfig()) {
            is VodResult.Success -> catalogPort.loadPage(
                config = config.value,
                categoryId = categoryId,
                offset = offset,
                limit = limit,
            )

            is VodResult.Failure -> config
        }
}
