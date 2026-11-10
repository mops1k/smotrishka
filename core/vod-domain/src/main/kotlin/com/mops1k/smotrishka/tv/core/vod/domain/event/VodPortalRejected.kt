package com.mops1k.smotrishka.tv.core.vod.domain.event

import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure

/**
 * Подключение портала отклонено: ключ не подошёл, подписка кончилась или адрес неверный.
 *
 * @property failure причина отказа;
 * @property occurredAtMillis момент события.
 */
data class VodPortalRejected(
    val failure: VodFailure,
    override val occurredAtMillis: Long,
) : VodEvent
