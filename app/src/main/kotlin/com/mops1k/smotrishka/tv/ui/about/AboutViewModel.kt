package com.mops1k.smotrishka.tv.ui.about

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.data.UpdateScheduler
import com.mops1k.smotrishka.tv.core.data.backup.SettingsBackupResult
import com.mops1k.smotrishka.tv.core.data.backup.SettingsBackupService
import com.mops1k.smotrishka.tv.core.data.backup.SettingsImportResult
import com.mops1k.smotrishka.tv.update.InstalledApp
import com.mops1k.smotrishka.tv.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Экран «О приложении»: версия сборки, блок обновления и перенос настроек.
 *
 * @param repository проверка и установка обновлений;
 * @param installedApp сведения об установленной сборке;
 * @param backupService экспорт и импорт настроек;
 * @param updateScheduler перезапуск обновления каталога после импорта;
 * @param context контекст приложения для чтения и записи файла настроек.
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val repository: UpdateRepository,
    private val installedApp: InstalledApp,
    private val backupService: SettingsBackupService,
    private val updateScheduler: UpdateScheduler,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val metered = MutableStateFlow(repository.isNetworkMetered())
    private val installAllowed = MutableStateFlow(repository.canInstall())

    /** Состояние экрана. */
    val state: StateFlow<AboutUiState> = combine(
        repository.state,
        repository.settings,
        metered,
        installAllowed,
    ) { update, settings, meteredNow, canInstall ->
        AboutUiState(
            versionLabel = installedApp.versionLabel,
            update = update,
            includePrereleases = settings.includePrereleases,
            wifiOnly = settings.wifiOnly,
            metered = meteredNow,
            canInstall = canInstall,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = AboutUiState(versionLabel = installedApp.versionLabel),
    )

    private val _message = MutableStateFlow<String?>(null)

    /** Сообщение для тоста: итог сохранения или восстановления настроек. */
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _pendingImport = MutableStateFlow<String?>(null)

    /** Прочитанный файл настроек, ждущий подтверждения замены. */
    val pendingImport: StateFlow<String?> = _pendingImport.asStateFlow()

    /** Обновляет сведения о сети и разрешении, затем проверяет наличие новой версии. */
    fun checkNow() {
        refreshEnvironment()
        repository.checkNow()
    }

    /** Повторная проверка со сбросом «не спрашивать об этой версии». */
    fun checkAgain() {
        refreshEnvironment()
        repository.checkAgain()
    }

    /**
     * Скачивает и ставит обновление.
     *
     * @param force качать даже по мобильной сети.
     */
    fun download(force: Boolean = false) {
        refreshEnvironment()
        repository.downloadAndInstall(forceMobile = force)
    }

    /** «Не спрашивать об этой версии». */
    fun dismiss() {
        repository.dismissCurrent()
    }

    /**
     * Включает или выключает пререлизы.
     *
     * @param value учитывать `rc`-релизы.
     */
    fun setPrereleases(value: Boolean) {
        repository.setIncludePrereleases(value)
    }

    /**
     * Включает или выключает ограничение «только Wi-Fi».
     *
     * @param value проверять и скачивать только по Wi-Fi.
     */
    fun setWifiOnly(value: Boolean) {
        repository.setWifiOnly(value)
    }

    /** Открывает системный экран разрешения установки. */
    fun openInstallPermission() {
        repository.openInstallPermission()
        installAllowed.value = repository.canInstall()
    }

    /**
     * Сохраняет скачанный APK в выбранный пользователем файл.
     *
     * @param uri адрес из системного диалога «Сохранить как».
     */
    fun saveApk(uri: Uri) {
        repository.saveDownloadedApk(uri)
    }

    /**
     * Сохраняет настройки в выбранный файл.
     *
     * @param uri адрес из системного диалога «Сохранить как».
     */
    fun exportSettings(uri: Uri) {
        viewModelScope.launch {
            when (val result = backupService.createJson()) {
                is SettingsBackupResult.Failure -> _message.value = result.reason

                is SettingsBackupResult.Success -> {
                    val written = runCatching {
                        val output = context.contentResolver.openOutputStream(uri)
                            ?: error("Не удалось открыть файл")
                        output.use { stream -> stream.write(result.json.toByteArray()) }
                    }
                    _message.value = if (written.isSuccess) {
                        "Настройки сохранены"
                    } else {
                        Log.e(TAG, "settings export failed", written.exceptionOrNull())
                        "Не удалось сохранить настройки"
                    }
                }
            }
        }
    }

    /**
     * Читает файл настроек и ждёт подтверждения замены.
     *
     * @param uri адрес выбранного файла.
     */
    fun readSettingsFile(uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Не удалось открыть файл")
            }.getOrElse { error ->
                Log.e(TAG, "settings read failed", error)
                _message.value = "Не удалось прочитать файл настроек"
                return@launch
            }
            _pendingImport.value = text
        }
    }

    /** Подтверждает замену настроек из прочитанного файла. */
    fun confirmImport() {
        val text = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch {
            when (val result = backupService.importJson(text)) {
                is SettingsImportResult.Failure -> _message.value = result.reason

                SettingsImportResult.Success -> {
                    _message.value = "Настройки восстановлены, каталог обновляется"
                    runCatching { updateScheduler.syncNow() }
                        .onFailure { error -> Log.e(TAG, "sync after import failed", error) }
                }
            }
        }
    }

    /** Отменяет замену настроек. */
    fun cancelImport() {
        _pendingImport.value = null
    }

    /** Сбрасывает показанное сообщение. */
    fun consumeMessage() {
        _message.value = null
    }

    private fun refreshEnvironment() {
        metered.value = repository.isNetworkMetered()
        installAllowed.value = repository.canInstall()
    }

    private companion object {
        const val TAG = "SmotrishkaUpdate"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
