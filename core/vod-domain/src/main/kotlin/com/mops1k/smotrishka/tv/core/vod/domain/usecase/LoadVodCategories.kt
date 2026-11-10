package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPortalPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.connectedConfig

/**
 * Перечитывает категории медиатеки у портала.
 *
 * Нужен для обновления списка без переподключения, например когда портал добавил категорию.
 *
 * @param portalPort портал;
 * @param configPort хранилище подключения.
 */
class LoadVodCategories(
    private val portalPort: VodPortalPort,
    private val configPort: VodConfigPort,
) {

    /** Запрашивает категории у подключённого портала. */
    suspend operator fun invoke(): VodResult<List<VodCategory>> =
        when (val config = configPort.connectedConfig()) {
            is VodResult.Success -> portalPort.loadCategories(config.value)
            is VodResult.Failure -> config
        }
}
