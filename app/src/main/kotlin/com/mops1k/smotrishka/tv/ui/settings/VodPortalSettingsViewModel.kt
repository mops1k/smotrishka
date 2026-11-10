package com.mops1k.smotrishka.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalLink
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalLinkParseResult
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ConnectVodPortal
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.DisconnectVodPortal
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodConnection
import com.mops1k.smotrishka.tv.ui.vod.toMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Карточка «Медиатека» в настройках: подключение портала и его отключение.
 *
 * Подключение проверяется порталом до сохранения: непроверенная пара не сохраняется, поэтому
 * пункт «Медиатека» в меню не появляется от случайной опечатки.
 *
 * @param observeVodConnection состояние подключения;
 * @param connectVodPortal проверка и сохранение портала;
 * @param disconnectVodPortal отключение вместе с избранным и историей.
 */
@HiltViewModel
class VodPortalSettingsViewModel @Inject constructor(
    observeVodConnection: ObserveVodConnection,
    private val connectVodPortal: ConnectVodPortal,
    private val disconnectVodPortal: DisconnectVodPortal,
) : ViewModel() {

    private val checking = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val dialogOpen = MutableStateFlow(false)

    /** Состояние карточки. */
    val state: StateFlow<VodPortalSettingsUiState> = combine(
        observeVodConnection(),
        checking,
        message,
        dialogOpen,
    ) { status, isChecking, currentMessage, isDialogOpen ->
        VodPortalSettingsUiState(
            status = status,
            checking = isChecking,
            message = currentMessage,
            dialogOpen = isDialogOpen,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = VodPortalSettingsUiState(status = VodPortalStatus.NotConfigured),
    )

    /** Открывает диалог ввода ссылки. */
    fun openPortalDialog() {
        message.value = null
        dialogOpen.value = true
    }

    /** Закрывает диалог без сохранения. */
    fun dismissPortalDialog() {
        dialogOpen.value = false
        message.value = null
    }

    /**
     * Проверяет и сохраняет портал.
     *
     * @param link строка подключения из личного кабинета — целиком, как её выдал провайдер.
     */
    fun connect(link: String) {
        if (checking.value) return
        val config = when (val parsed = VodPortalLink.parse(link)) {
            is VodPortalLinkParseResult.Parsed -> parsed.config
            VodPortalLinkParseResult.MissingKey -> {
                message.value = MISSING_KEY_MESSAGE
                return
            }

            VodPortalLinkParseResult.BadUrl -> {
                message.value = BAD_LINK_MESSAGE
                return
            }
        }
        viewModelScope.launch {
            checking.value = true
            message.value = null
            when (val result = connectVodPortal(config)) {
                is VodResult.Success -> {
                    // Успех — диалог закрывается сам, подтверждение остаётся в карточке.
                    message.value = CONNECTED_MESSAGE
                    dialogOpen.value = false
                }

                is VodResult.Failure -> message.value = result.failure.toMessage()
            }
            checking.value = false
        }
    }

    /** Отключает медиатеку: портал, избранное и история удаляются. */
    fun disconnect() {
        viewModelScope.launch {
            disconnectVodPortal()
            message.value = DISCONNECTED_MESSAGE
        }
    }

    /** Убирает сообщение после ухода с экрана. */
    fun clearMessage() {
        message.value = null
    }

    private companion object {
        const val CONNECTED_MESSAGE = "Медиатека подключена"
        const val DISCONNECTED_MESSAGE = "Медиатека отключена"
        const val MISSING_KEY_MESSAGE = "В ссылке нет ключа — скопируйте её из личного кабинета целиком"
        const val BAD_LINK_MESSAGE = "Ссылка не похожа на адрес портала"
    }
}
