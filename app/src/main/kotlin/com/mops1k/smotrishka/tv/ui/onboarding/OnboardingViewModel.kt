package com.mops1k.smotrishka.tv.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.data.CatalogUpdateResult
import com.mops1k.smotrishka.tv.core.data.CatalogUpdater
import com.mops1k.smotrishka.tv.core.data.SourceRepository
import com.mops1k.smotrishka.tv.epg.EpgUpdater
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Экран подключения источника.
 *
 * Сначала проверяется ввод (без сети), затем плейлист загружается и разбирается,
 * и только успешный результат сохраняется как активный источник — так пользователь
 * не остаётся с нерабочей настройкой.
 *
 * @param sourceRepository хранение активного источника;
 * @param updater загрузка каталога;
 * @param epgUpdater фоновая догрузка программы передач.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val updater: CatalogUpdater,
    private val epgUpdater: EpgUpdater,
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Idle)

    /** Состояние экрана. */
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _initial = MutableStateFlow<PlaylistSource?>(null)

    /**
     * Сохранённый источник для подстановки в форму.
     *
     * При первом запуске остаётся `null`; при смене плейлиста из настроек экран открывается
     * с уже заполненными полями.
     */
    val initial: StateFlow<PlaylistSource?> = _initial.asStateFlow()

    init {
        viewModelScope.launch { _initial.value = sourceRepository.active() }
    }

    /**
     * Подключает источник.
     *
     * @param source настроенный источник.
     */
    fun submit(source: PlaylistSource) {
        val invalid = validate(source)
        if (invalid != null) {
            _state.value = OnboardingUiState.Error(invalid)
            return
        }

        viewModelScope.launch {
            _state.value = OnboardingUiState.Loading

            // Любой сбой на устройстве (сеть, шифрование, база) должен показываться текстом,
            // а не ронять приложение.
            runCatching {
                when (val result = updater.updateCatalogOnly(source)) {
                    is CatalogUpdateResult.Success -> {
                        sourceRepository.save(source, displayName(source))
                        _state.value = OnboardingUiState.Success
                        // Гид не ждём: он догружается в фоне и виден на главном экране.
                        epgUpdater.update(result.epgUrl)
                    }

                    is CatalogUpdateResult.Failure -> _state.value = OnboardingUiState.Error(result.reason)
                }
            }.onFailure { error ->
                _state.value = OnboardingUiState.Error(
                    "Не удалось подключить источник: ${error.message ?: "неизвестная ошибка"}",
                )
            }
        }
    }

    /** Возвращает экран в исходное состояние (например, после ошибки). */
    fun reset() {
        _state.value = OnboardingUiState.Idle
    }

    private fun validate(source: PlaylistSource): String? =
        when (source) {
            is PlaylistSource.M3uUrl -> when {
                source.url.isBlank() -> "Введите адрес плейлиста"
                !source.url.isHttpUrl() -> "Адрес должен начинаться с http:// или https://"
                else -> null
            }

            is PlaylistSource.Xtream -> when {
                !source.server.isHttpUrl() -> "Адрес сервера должен начинаться с http:// или https://"
                source.username.isBlank() -> "Введите логин"
                source.password.isBlank() -> "Введите пароль"
                else -> null
            }

            is PlaylistSource.IedemCode ->
                if (source.code.isBlank()) "Введите код из личного кабинета iedem.tv" else null

            is PlaylistSource.File ->
                if (source.uri.isBlank()) "Выберите файл плейлиста" else null
        }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private fun displayName(source: PlaylistSource): String =
        when (source) {
            is PlaylistSource.M3uUrl -> source.name?.takeIf(String::isNotBlank) ?: "Плейлист по ссылке"
            is PlaylistSource.Xtream -> "Xtream"
            is PlaylistSource.IedemCode -> "iedem.tv по коду"
            is PlaylistSource.File -> source.name?.takeIf(String::isNotBlank) ?: "Плейлист из файла"
        }
}
