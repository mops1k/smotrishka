package com.mops1k.smotrishka.tv.ui.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodProgress
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ToggleVodFavorite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Карточка медиатеки: описание, серии и отметка избранного.
 *
 * Избранное и история просмотра приходят потоками: сердце на карточке, плашка «Продолжить»
 * и список избранного реагируют на одно и то же состояние, а не на копии.
 *
 * @param savedState аргументы маршрута;
 * @param loadVodTitleDetails загрузка карточки;
 * @param observeVodFavorites избранное медиатеки;
 * @param observeVodProgress история просмотра;
 * @param toggleVodFavorite переключение избранного.
 */
@HiltViewModel
class VodTitleViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loadVodTitleDetails: LoadVodTitleDetails,
    observeVodFavorites: ObserveVodFavorites,
    observeVodProgress: ObserveVodProgress,
    private val toggleVodFavorite: ToggleVodFavorite,
) : ViewModel() {

    private val titleId = VodTitleId(
        savedState.get<String>(ARG_TITLE_ID)?.toIntOrNull() ?: 0,
    )

    private val details = MutableStateFlow<VodTitleDetails?>(null)
    private val failure = MutableStateFlow<String?>(null)

    /** Состояние карточки. */
    val state: StateFlow<VodTitleUiState> = combine(
        details,
        failure,
        observeVodFavorites(),
        observeVodProgress(),
    ) { loaded, error, favorites, progress ->
        when {
            error != null -> VodTitleUiState.Error(error)
            loaded == null -> VodTitleUiState.Loading
            else -> VodTitleUiState.Content(
                details = loaded,
                favorite = favorites.contains(loaded.title.id),
                progress = progress.firstOrNull { it.titleId == loaded.title.id },
                qualities = loaded.qualities(),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        // Карточка живёт один экран: считаем состояние сразу, без ожидания подписчика.
        started = SharingStarted.Eagerly,
        initialValue = VodTitleUiState.Loading,
    )

    init {
        load()
    }

    /** Переключает карточку в избранном. */
    fun toggleFavorite() {
        val title = details.value?.title ?: return
        viewModelScope.launch { toggleVodFavorite(title) }
    }

    /** Перечитывает карточку — по кнопке «Повторить». */
    fun reload() {
        load()
    }

    private fun load() {
        failure.value = null
        viewModelScope.launch {
            when (val result = loadVodTitleDetails(titleId)) {
                is VodResult.Success -> details.value = result.value
                is VodResult.Failure -> failure.value = result.failure.toMessage()
            }
        }
    }

    companion object {

        /** Аргумент маршрута: идентификатор карточки. */
        const val ARG_TITLE_ID = "titleId"

        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
