package com.mops1k.smotrishka.tv.ui.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodFavorite
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodFavorites
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Избранное медиатеки для раздела «Избранное».
 *
 * Состояние читается из хранилища: избранное показывается и без связи с порталом, потому что
 * хранится снапшотом карточек.
 *
 * @param observeVodFavorites наблюдение за избранным.
 */
@HiltViewModel
class VodFavoritesViewModel @Inject constructor(
    observeVodFavorites: ObserveVodFavorites,
) : ViewModel() {

    /** Избранные карточки в порядке добавления. */
    val favorites: StateFlow<List<VodFavorite>> = observeVodFavorites()
        .map { it.items }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = emptyList(),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
