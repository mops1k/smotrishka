package com.mops1k.smotrishka.tv.core.vod.domain.port

import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import kotlinx.coroutines.flow.first

/**
 * Берёт конфигурацию подключённого портала — единственное место, где это решается.
 *
 * Нужно всем операциям каталога и карточки: без неё обращаться к порталу нечем. Если последняя
 * проверка не проходила ([VodPortalStatus.Failed]), конфигурация всё равно отдаётся: сеть могла
 * восстановиться, а свежую ошибку вернёт сама операция.
 *
 * @return конфигурация либо доменная ошибка.
 */
suspend fun VodConfigPort.connectedConfig(): VodResult<VodPortalConfig> =
    when (val current = status.first()) {
        is VodPortalStatus.Connected -> VodResult.Success(current.config)
        is VodPortalStatus.Failed -> VodResult.Success(current.config)
        VodPortalStatus.NotConfigured -> VodResult.Failure(VodFailure.NotConfigured)
    }
