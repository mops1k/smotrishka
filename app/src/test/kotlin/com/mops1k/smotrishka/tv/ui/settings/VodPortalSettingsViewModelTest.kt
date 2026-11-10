package com.mops1k.smotrishka.tv.ui.settings

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ConnectVodPortal
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.DisconnectVodPortal
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Поведение карточки медиатеки в настройках: диалог закрывается только по успешной проверке,
 * а отказ оставляет его открытым с причиной.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VodPortalSettingsViewModelTest {

    private val link =
        "portal::[key:e8179fbc74ceedf-731012aca5e3898]http://a2c998438402.stremersons.net/api/v1/"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settings(
        portal: SettingsTestPortalPort = SettingsTestPortalPort(),
    ): Triple<VodPortalSettingsViewModel, SettingsTestConfigPort, SettingsTestFavoritesPort> {
        val config = SettingsTestConfigPort()
        val favorites = SettingsTestFavoritesPort()
        val connect = ConnectVodPortal(
            portalPort = portal,
            configPort = config,
            favoritesPort = favorites,
            progressPort = SettingsTestProgressPort(),
            eventBus = SettingsTestEventBus(),
            clock = SettingsTestClock(),
        )
        val viewModel = VodPortalSettingsViewModel(
            observeVodConnection = ObserveVodConnection(config),
            connectVodPortal = connect,
            disconnectVodPortal = DisconnectVodPortal(config, favorites, SettingsTestProgressPort()),
        )
        return Triple(viewModel, config, favorites)
    }

    @Test
    fun `успешная проверка закрывает диалог и подтверждает подключение в карточке`() = runTest {
        val (viewModel, _, _) = settings()

        viewModel.openPortalDialog()
        assertThat(viewModel.state.value.dialogOpen).isTrue()

        viewModel.connect(link)

        assertThat(viewModel.state.value.dialogOpen).isFalse()
        assertThat(viewModel.state.value.connected).isTrue()
        assertThat(viewModel.state.value.message).isEqualTo("Медиатека подключена")
    }

    @Test
    fun `отказ портала оставляет диалог открытым с причиной`() = runTest {
        val portal = SettingsTestPortalPort(VodResult.Failure(VodFailure.SubscriptionRequired))
        val (viewModel, _, _) = settings(portal)

        viewModel.openPortalDialog()
        viewModel.connect(link)

        assertThat(viewModel.state.value.dialogOpen).isTrue()
        assertThat(viewModel.state.value.connected).isFalse()
        assertThat(viewModel.state.value.message).contains("Подписка медиатеки не найдена")
    }

    @Test
    fun `ссылка без ключа не отправляется на портал`() = runTest {
        val (viewModel, config, _) = settings()

        viewModel.openPortalDialog()
        viewModel.connect("http://portal.example/api/v1/")

        assertThat(viewModel.state.value.dialogOpen).isTrue()
        assertThat(viewModel.state.value.message).contains("нет ключа")
        assertThat(config.status.first()).isEqualTo(VodPortalStatus.NotConfigured)
    }

    @Test
    fun `закрытие диалога без сохранения сбрасывает сообщение`() = runTest {
        val (viewModel, _, _) = settings()
        viewModel.openPortalDialog()
        viewModel.connect("мусор")

        viewModel.dismissPortalDialog()

        assertThat(viewModel.state.value.dialogOpen).isFalse()
        assertThat(viewModel.state.value.message).isNull()
    }
}
