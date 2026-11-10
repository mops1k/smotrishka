package com.mops1k.smotrishka.tv.ui.settings

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import org.junit.Test

/**
 * Отображение состояния подключения медиатеки в настройках: что видит пользователь в строке
 * «Портал медиатеки» и когда карточка считается подключённой.
 */
class VodPortalSettingsUiStateTest {

    private val config = VodPortalConfig.from("http://portal.example/api/v1/", "key")!!

    @Test
    fun `неподключённая медиатека так и подписана`() {
        val state = VodPortalSettingsUiState(status = VodPortalStatus.NotConfigured)

        assertThat(state.connected).isFalse()
        assertThat(state.detail).isEqualTo("не подключена")
    }

    @Test
    fun `у подключённой медиатеки виден хост портала без схемы`() {
        val state = VodPortalSettingsUiState(status = VodPortalStatus.Connected(config))

        assertThat(state.connected).isTrue()
        assertThat(state.detail).isEqualTo("portal.example/api/v1")
    }

    @Test
    fun `неудачная проверка не выглядит как подключение`() {
        val state = VodPortalSettingsUiState(
            status = VodPortalStatus.Failed(config, VodFailure.SubscriptionRequired),
        )

        assertThat(state.connected).isFalse()
        assertThat(state.detail).isEqualTo("проверка не прошла")
    }
}
