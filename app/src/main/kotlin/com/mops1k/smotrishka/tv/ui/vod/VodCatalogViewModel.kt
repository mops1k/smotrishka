package com.mops1k.smotrishka.tv.ui.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.vod.domain.VodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.VodLibrary
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Экран медиатеки: категории портала и страницы каталога.
 *
 * Курсор страниц берётся из агрегата [VodLibrary]: экран только просит «дальше», а решение,
 * есть ли дальше, принимает агрегат. Ошибка первой загрузки показывается вместо содержимого,
 * ошибка догрузки — не стирает уже показанные карточки.
 *
 * @param loadVodCategories категории портала;
 * @param loadVodCatalogPage страница категории.
 */
@HiltViewModel
class VodCatalogViewModel @Inject constructor(
    private val loadVodCategories: LoadVodCategories,
    private val loadVodCatalogPage: LoadVodCatalogPage,
) : ViewModel() {

    private val _state = MutableStateFlow<VodCatalogUiState>(VodCatalogUiState.Loading)

    /** Состояние экрана. */
    val state: StateFlow<VodCatalogUiState> = _state.asStateFlow()

    private var pageJob: Job? = null

    init {
        reload()
    }

    /** Перечитывает категории и первую страницу — по входу на экран и по «Повторить». */
    fun reload() {
        pageJob?.cancel()
        _state.value = VodCatalogUiState.Loading
        viewModelScope.launch {
            when (val result = loadVodCategories()) {
                is VodResult.Failure -> _state.value = VodCatalogUiState.Error(result.failure.toMessage())
                is VodResult.Success -> {
                    val library = VodLibrary().withCategories(result.value)
                    _state.value = VodCatalogUiState.Content(library = library)
                    loadFirstPage()
                }
            }
        }
    }

    /**
     * Переключает категорию и загружает её первую страницу.
     *
     * @param categoryId выбранная категория.
     */
    fun selectCategory(categoryId: VodCategoryId) {
        val content = _state.value as? VodCatalogUiState.Content ?: return
        _state.value = content.copy(
            library = content.library.select(categoryId),
            selected = null,
            loadingMore = false,
        )
        loadFirstPage()
    }

    /** Догружает следующую страницу, если курсор есть и загрузка уже не идёт. */
    fun loadMore() {
        val content = _state.value as? VodCatalogUiState.Content ?: return
        if (content.loadingMore || !content.library.hasMore) return
        val offset = content.library.nextOffset ?: return
        val categoryId = content.library.selectedCategoryId ?: return
        _state.value = content.copy(loadingMore = true)
        pageJob = viewModelScope.launch { fetchPage(categoryId, offset, reset = false) }
    }

    /**
     * Запоминает карточку под фокусом для панели превью.
     *
     * @param title карточка, на которой стоит пульт.
     */
    fun focusTitle(title: VodTitle) {
        val content = _state.value as? VodCatalogUiState.Content ?: return
        if (content.selected?.id == title.id) return
        _state.value = content.copy(selected = title)
    }

    /** Загружает первую страницу выбранной категории. */
    private fun loadFirstPage() {
        val content = _state.value as? VodCatalogUiState.Content ?: return
        val categoryId = content.library.selectedCategoryId ?: return
        pageJob?.cancel()
        pageJob = viewModelScope.launch { fetchPage(categoryId, offset = 0, reset = true) }
    }

    /** Загружает страницу и складывает её в агрегат. */
    private suspend fun fetchPage(categoryId: VodCategoryId, offset: Int, reset: Boolean) {
        when (val result = loadVodCatalogPage(categoryId, offset)) {
            is VodResult.Failure -> onPageFailure(result, reset)
            is VodResult.Success -> onPageLoaded(result.value, reset)
        }
    }

    private fun onPageLoaded(page: VodCatalogPage, reset: Boolean) {
        _state.update { current ->
            val content = current as? VodCatalogUiState.Content ?: return@update current
            // При reset карточки прошлой категории уже убраны в selectCategory, поэтому
            // в обоих случаях страница просто добавляется к загруженному.
            val library = content.library.append(page)
            content.copy(
                library = library,
                loadingMore = false,
                selected = content.selected ?: library.titles.firstOrNull(),
            )
        }
    }

    private fun onPageFailure(failure: VodResult.Failure, reset: Boolean) {
        if (reset) {
            _state.value = VodCatalogUiState.Error(failure.failure.toMessage())
            return
        }
        // Догрузка не удалась: показанное не стираем, просто отпускаем флаг загрузки.
        _state.update { current ->
            (current as? VodCatalogUiState.Content)?.copy(loadingMore = false) ?: current
        }
    }
}
