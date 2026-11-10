package com.mops1k.smotrishka.tv.core.vod.domain

import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig

/**
 * Состояние подключения медиатеки.
 *
 * Именно [Connected] разрешает показать пункт «Медиатека» в меню: сохранённых адреса и ключа
 * недостаточно, нужна успешная проверка порталом.
 */
sealed interface VodPortalStatus {

    /** Портал не настроен: адреса или ключа нет. */
    data object NotConfigured : VodPortalStatus

    /** Портал сохранён и проверен: категории получены. */
    data class Connected(val config: VodPortalConfig) : VodPortalStatus

    /** Портал сохранён, но последняя проверка не прошла. */
    data class Failed(val config: VodPortalConfig, val failure: VodFailure) : VodPortalStatus {
        /** Признак «пропал доступ», а не «не настроено». */
        val isConfigured: Boolean
            get() = true
    }
}
