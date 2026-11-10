package com.mops1k.smotrishka.tv.core.vod.domain.port

import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import kotlinx.coroutines.flow.Flow

/**
 * Хранилище данных подключения медиатеки.
 *
 * Адрес лежит открыто (это не секрет), ключ шифруется адаптером. Наружу состояние отдаётся
 * только как [VodPortalStatus]: по нему решается, показывать ли пункт «Медиатека».
 */
interface VodConfigPort {

    /** Текущее состояние подключения. */
    val status: Flow<VodPortalStatus>

    /** Сохраняет проверенную конфигурацию. */
    suspend fun saveConnected(config: VodPortalConfig)

    /** Сохраняет конфигурацию вместе с причиной неудачной проверки. */
    suspend fun saveFailed(config: VodPortalConfig, failure: VodFailure)

    /** Забывает портал целиком. */
    suspend fun clear()
}
