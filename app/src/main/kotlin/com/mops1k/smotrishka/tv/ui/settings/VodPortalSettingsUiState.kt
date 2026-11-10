package com.mops1k.smotrishka.tv.ui.settings

import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus

/**
 * Состояние карточки «Медиатека» в настройках.
 *
 * @property status состояние подключения портала;
 * @property checking идёт проверка ссылки;
 * @property message сообщение последней проверки — успех или причина отказа;
 * @property dialogOpen открыт ли диалог ввода ссылки.
 */
data class VodPortalSettingsUiState(
    val status: VodPortalStatus,
    val checking: Boolean = false,
    val message: String? = null,
    val dialogOpen: Boolean = false,
) {

    /** Портал подключён и проверен. */
    val connected: Boolean
        get() = status is VodPortalStatus.Connected

    /** Что показать в строке настроек: хост портала или состояние. */
    val detail: String
        get() = when (val current = status) {
            VodPortalStatus.NotConfigured -> "не подключена"
            is VodPortalStatus.Connected -> current.config.url.value
                .substringAfter("://")
                .trimEnd('/')

            is VodPortalStatus.Failed -> "проверка не прошла"
        }
}
