package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodPortalConnected
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodPortalRejected
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPortalPort
import kotlinx.coroutines.flow.first

/**
 * Подключает портал медиатеки: проверяет адрес и ключ корневым запросом.
 *
 * Порядок важен: сначала проверка порталом, и только потом сохранение. Сохранять непроверенную
 * пару нельзя — по сохранённому состоянию интерфейс показывает пункт «Медиатека».
 * При смене портала избранное и история просмотра сбрасываются: идентификаторы принадлежат
 * конкретному порталу, и от другой подписки они означали бы чужие фильмы.
 *
 * @param portalPort проверка портала;
 * @param configPort хранилище подключения;
 * @param favoritesPort избранное медиатеки;
 * @param progressPort история просмотра;
 * @param eventBus шина доменных событий;
 * @param clock время для событий.
 */
class ConnectVodPortal(
    private val portalPort: VodPortalPort,
    private val configPort: VodConfigPort,
    private val favoritesPort: VodFavoritesPort,
    private val progressPort: VodPlaybackProgressPort,
    private val eventBus: VodEventBus,
    private val clock: VodClock,
) {

    /**
     * Проверяет и сохраняет подключение.
     *
     * @param config разобранное подключение из строки личного кабинета;
     * @return категории портала при успехе, иначе причина отказа.
     */
    suspend operator fun invoke(config: VodPortalConfig): VodResult<List<VodCategory>> {
        return when (val result = portalPort.loadCategories(config)) {
            is VodResult.Success -> {
                if (isOtherPortal(config)) {
                    favoritesPort.clear()
                    progressPort.clear()
                }
                configPort.saveConnected(config)
                eventBus.publish(
                    VodPortalConnected(
                        url = config.url,
                        categories = result.value,
                        occurredAtMillis = clock.nowMillis(),
                    ),
                )
                result
            }

            is VodResult.Failure -> {
                configPort.saveFailed(config, result.failure)
                eventBus.publish(
                    VodPortalRejected(failure = result.failure, occurredAtMillis = clock.nowMillis()),
                )
                result
            }
        }
    }

    /** Отличается ли новый портал от уже подключённого. */
    private suspend fun isOtherPortal(config: VodPortalConfig): Boolean {
        val current = configPort.status.first()
        val connectedUrl = when (current) {
            is VodPortalStatus.Connected -> current.config.url
            is VodPortalStatus.Failed -> current.config.url
            VodPortalStatus.NotConfigured -> null
        }
        return connectedUrl != null && connectedUrl != config.url
    }
}
