package com.mops1k.smotrishka.tv.core.vod.domain.event

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalUrl

/**
 * Портал медиатеки подключён: корневой запрос прошёл, категории получены.
 *
 * Именно по этому событию пункт «Медиатека» появляется в меню — состояние подключения уже
 * сохранено, событие лишь сообщает об изменении.
 *
 * @property url адрес портала;
 * @property categories категории из корневого ответа;
 * @property occurredAtMillis момент события.
 */
data class VodPortalConnected(
    val url: VodPortalUrl,
    val categories: List<VodCategory>,
    override val occurredAtMillis: Long,
) : VodEvent
