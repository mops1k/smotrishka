package com.mops1k.smotrishka.tv.ui.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.SearchVodTitles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Поиск по медиатеке: запрос уходит на портал с задержкой ввода, как поиск каналов на главном.
 *
 * @param searchVodTitles поиск по порталу.
 */
@HiltViewModel
class VodSearchViewModel @Inject constructor(
    private val searchVodTitles: SearchVodTitles,
) : ViewModel() {

    private val _state = MutableStateFlow(VodSearchUiState())

    /** Состояние поиска. */
    val state: StateFlow<VodSearchUiState> = _state.asStateFlow()

    private val queries = MutableStateFlow("")

    init {
        observeQueries()
    }

    /**
     * Меняет строку поиска.
     *
     * @param query введённый текст.
     */
    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        queries.value = query
    }

    /** Очищает результаты — при закрытии поиска. */
    fun clear() {
        queries.value = ""
        _state.value = VodSearchUiState()
    }

    @OptIn(FlowPreview::class)
    private fun observeQueries() {
        viewModelScope.launch {
            queries
                .debounce(SEARCH_DEBOUNCE_MILLIS)
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query.isBlank()) {
                        _state.update { it.copy(results = emptyList(), loading = false, error = null) }
                        return@collectLatest
                    }
                    _state.update { it.copy(loading = true, error = null) }
                    when (val result = searchVodTitles(query)) {
                        is VodResult.Success -> _state.update {
                            it.copy(results = result.value, loading = false)
                        }

                        is VodResult.Failure -> _state.update {
                            it.copy(loading = false, error = result.failure.toMessage())
                        }
                    }
                }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 350L
    }
}
