package com.mops1k.smotrishka.tv.ui.onboarding

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.data.CatalogUpdateResult
import com.mops1k.smotrishka.tv.core.data.CatalogUpdater
import com.mops1k.smotrishka.tv.core.data.SourceRepository
import com.mops1k.smotrishka.tv.core.data.backup.SettingsBackupService
import com.mops1k.smotrishka.tv.core.data.backup.SettingsImportResult
import com.mops1k.smotrishka.tv.epg.EpgUpdater
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * @param epgUpdater фоновая догрузка программы передач;
 * @param backupService восстановление настроек из файла;
 * @param context контекст приложения для чтения файла настроек.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val sourceRepository: SourceRepository,
    private val updater: CatalogUpdater,
    private val epgUpdater: EpgUpdater,
    private val backupService: SettingsBackupService,
    @ApplicationContext private val context: Context,
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

    /**
     * Восстанавливает настройки из файла: источник, гиды, плеер и избранное.
     *
     * После импорта каталог перечитывается тем же путём, что и при обычном подключении:
     * так пользователь попадает на главный экран уже с каналами.
     *
     * @param uri адрес выбранного файла настроек.
     */
    fun restore(uri: Uri) {
        viewModelScope.launch {
            _state.value = OnboardingUiState.Loading

            // Ошибка чтения файла, разбора и сети — текст на экране, а не падение.
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("не удалось открыть файл")

                when (val imported = backupService.importJson(text)) {
                    is SettingsImportResult.Failure -> {
                        _state.value = OnboardingUiState.Error(imported.reason)
                    }

                    SettingsImportResult.Success -> {
                        val source = sourceRepository.active()
                        if (source == null) {
                            _state.value = OnboardingUiState.Error("В файле нет источника каналов")
                            return@runCatching
                        }
                        when (val updated = updater.updateCatalogOnly(source)) {
                            is CatalogUpdateResult.Success -> {
                                _state.value = OnboardingUiState.Success
                                epgUpdater.update(updated.epgUrl)
                            }

                            is CatalogUpdateResult.Failure -> {
                                _state.value = OnboardingUiState.Error(updated.reason)
                            }
                        }
                    }
                }
            }.onFailure { error ->
                _state.value = OnboardingUiState.Error(
                    "Не удалось восстановить настройки: ${error.message ?: "неизвестная ошибка"}",
                )
            }
        }
    }

    private fun validate(source: PlaylistSource): String? =
        when (source) {
            is PlaylistSource.M3uUrl -> when {
                source.url.isBlank() -> "Укажите адрес плейлиста"
                !source.url.isHttpUrl() -> "Не удалось загрузить плейлист: проверьте адрес"
                else -> null
            }

            is PlaylistSource.Xtream -> when {
                source.server.isBlank() || source.username.isBlank() -> "Укажите сервер и логин"
                !source.server.isHttpUrl() -> "Не удалось загрузить плейлист: проверьте адрес"
                source.password.isBlank() -> "Укажите пароль"
                else -> null
            }

            is PlaylistSource.IedemCode ->
                if (source.code.isBlank()) "Укажите код из личного кабинета iedem.tv" else null

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
