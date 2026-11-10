package com.mops1k.smotrishka.tv.ui.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Признак «медиатека подключена» для меню и навигации.
 *
 * Пункт «Медиатека» появляется только у состояния [VodPortalStatus.Connected]: сохранённые,
 * но отвергнутые порталом данные пункт не показывают.
 *
 * @param observeVodConnection наблюдение за состоянием подключения.
 */
@HiltViewModel
class VodAvailabilityViewModel @Inject constructor(
    observeVodConnection: ObserveVodConnection,
) : ViewModel() {

    /** Подключена ли медиатека — по этому флагу строится меню. */
    val connected: StateFlow<Boolean> = observeVodConnection()
        .map { it is VodPortalStatus.Connected }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = false,
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
