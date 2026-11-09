package com.mops1k.smotrishka.tv.update

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mops1k.smotrishka.tv.core.data.update.ApkDownloadResult
import com.mops1k.smotrishka.tv.core.data.update.ApkDownloader
import com.mops1k.smotrishka.tv.core.data.update.UpdateCheckResult
import com.mops1k.smotrishka.tv.core.data.update.UpdateChecker
import com.mops1k.smotrishka.tv.core.data.update.UpdateSettings
import com.mops1k.smotrishka.tv.core.data.update.UpdateSettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Проверка обновлений и установка новой версии — одна на всё приложение.
 *
 * Экран About, тост при запуске и маркер в сайдбаре читают одно и то же состояние.
 * Любая ошибка превращается в [UpdateState.Failed] или сообщение: падать из-за
 * обновления приложение не должно.
 *
 * @param context контекст приложения;
 * @param checker сравнение с релизами GitHub;
 * @param settingsStore настройки обновления;
 * @param downloader загрузка APK;
 * @param cache кэш скачанных APK;
 * @param installer системный установщик;
 * @param installedApp сведения об установленной сборке;
 * @param network состояние сети.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checker: UpdateChecker,
    private val settingsStore: UpdateSettingsStore,
    private val downloader: ApkDownloader,
    private val cache: UpdateCache,
    private val installer: ApkInstaller,
    private val installedApp: InstalledApp,
    private val network: NetworkStatus,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)

    /** Текущее состояние обновления. */
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Настройки обновления: пререлизы, «только Wi-Fi», кэш проверки. */
    val settings: StateFlow<UpdateSettings> = settingsStore.settings
        .stateIn(scope, SharingStarted.Eagerly, UpdateSettings())

    // replay = 1: проверка при запуске успевает закончиться раньше, чем навигация
    // начнёт слушать поток, и без повтора тост «Доступно обновление» терялся бы.
    private val _messages = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 8)

    /** Короткие сообщения для тоста: «Доступно обновление 0.2.0» и подобные. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val startupChecked = AtomicBoolean(false)

    init {
        // Самообновление убивает процесс раньше, чем приёмник статуса удалит APK:
        // файлы уже установленных версий чистим при следующем запуске приложения.
        runCatching { cache.clearUpTo(installedApp.version()) }
            .onFailure { error -> Log.w(TAG, "очистка установленных APK не удалась", error) }
    }

    /** Мобильная ли текущая сеть: по ней автоматически ничего не скачиваем. */
    fun isNetworkMetered(): Boolean = !network.isUnmetered()

    /** Разрешена ли установка из этого приложения. */
    fun canInstall(): Boolean = installer.canInstall()

    /** Открывает системный экран «неизвестные источники» для приложения. */
    fun openInstallPermission() {
        runCatching { installer.openInstallPermission() }
            .onFailure { error -> Log.e(TAG, "open install permission failed", error) }
    }

    /**
     * Проверка при запуске: не чаще раза в сутки, только по Wi-Fi и при включённом
     * ограничении, повторно за процесс не выполняется.
     */
    fun checkOnStartup() {
        if (!startupChecked.compareAndSet(false, true)) return
        scope.launch {
            runCatching { checkInternal(manual = false) }
                .onFailure { error -> Log.e(TAG, "startup check failed", error) }
        }
    }

    /** Ручная проверка: сеть не ограничена, ошибки показываются. */
    fun checkNow() {
        scope.launch {
            runCatching { checkInternal(manual = true) }
                .onFailure { error ->
                    Log.e(TAG, "manual check failed", error)
                    _state.value = UpdateState.Failed("Не удалось проверить обновления")
                }
        }
    }

    /** Ручная проверка со сбросом «не спрашивать об этой версии». */
    fun checkAgain() {
        scope.launch {
            runCatching {
                settingsStore.clearDismissed()
                checkInternal(manual = true)
            }.onFailure { error ->
                Log.e(TAG, "repeat check failed", error)
                _state.value = UpdateState.Failed("Не удалось проверить обновления")
            }
        }
    }

    /** Пользователь выбрал «Не спрашивать об этой версии». */
    fun dismissCurrent() {
        val available = _state.value as? UpdateState.Available ?: return
        val version = available.release.version.toString()
        scope.launch {
            runCatching { settingsStore.dismiss(version) }
                .onSuccess { _state.value = available.copy(dismissed = true) }
                .onFailure { error -> Log.e(TAG, "dismiss failed", error) }
        }
    }

    /**
     * Включает или выключает пререлизы.
     *
     * @param value учитывать `rc`-релизы.
     */
    fun setIncludePrereleases(value: Boolean) {
        scope.launch {
            runCatching { settingsStore.setIncludePrereleases(value) }
                .onFailure { error -> Log.e(TAG, "prereleases save failed", error) }
        }
    }

    /**
     * Включает или выключает ограничение «только Wi-Fi».
     *
     * @param value проверять и скачивать автоматически только по Wi-Fi.
     */
    fun setWifiOnly(value: Boolean) {
        scope.launch {
            runCatching { settingsStore.setWifiOnly(value) }
                .onFailure { error -> Log.e(TAG, "wifi only save failed", error) }
        }
    }

    /**
     * Скачивает APK и запускает установку.
     *
     * @param forceMobile качать даже по мобильной сети (нажата кнопка «Скачать всё равно»).
     */
    fun downloadAndInstall(forceMobile: Boolean = false) {
        scope.launch {
            runCatching { downloadInternal(forceMobile) }
                .onFailure { error ->
                    Log.e(TAG, "download and install failed", error)
                    _state.value = UpdateState.Failed("Не удалось скачать обновление")
                }
        }
    }

    /**
     * Сохраняет скачанный APK туда, куда укажет пользователь (сценарий переустановки).
     *
     * @param uri адрес из системного диалога «Сохранить как».
     */
    fun saveDownloadedApk(uri: Uri) {
        val file = (_state.value as? UpdateState.NeedsReinstall)?.apkFile ?: return
        scope.launch {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Не удалось открыть файл")
                output.use { stream -> file.inputStream().use { input -> input.copyTo(stream) } }
            }.onSuccess {
                _messages.tryEmit("APK сохранён")
            }.onFailure { error ->
                Log.e(TAG, "save apk failed", error)
                _messages.tryEmit("Не удалось сохранить APK")
            }
        }
    }

    private suspend fun checkInternal(manual: Boolean) {
        val settings = settingsStore.settings.first()

        if (!manual) {
            if (settings.wifiOnly && !network.isUnmetered()) {
                Log.d(TAG, "автопроверка пропущена: сеть мобильная")
                return
            }
            val since = settings.lastCheckedAtMillis
            if (since != null && System.currentTimeMillis() - since < CHECK_INTERVAL_MS) {
                Log.d(TAG, "автопроверка пропущена: проверяли недавно")
                return
            }
        }

        if (!network.isOnline()) {
            if (manual) _state.value = UpdateState.Failed("Нет подключения к интернету")
            return
        }

        _state.value = UpdateState.Checking
        val checkedAt = System.currentTimeMillis()
        val result = checker.check(
            current = installedApp.version(),
            includePrereleases = settings.includePrereleases,
            debugBuild = installedApp.isDebugBuild,
        )

        _state.value = when (result) {
            is UpdateCheckResult.Available -> {
                settingsStore.markChecked(checkedAt)
                val version = result.release.version.toString()
                if (!manual && settings.notifiedVersion != version) {
                    settingsStore.markNotified(version)
                    _messages.tryEmit("Доступно обновление $version")
                }
                UpdateState.Available(
                    release = result.release,
                    apk = result.apk,
                    dismissed = version == settings.dismissedVersion,
                    cachedApk = cache.cached(version),
                )
            }

            is UpdateCheckResult.UpToDate -> {
                settingsStore.markChecked(checkedAt)
                UpdateState.UpToDate(checkedAt)
            }

            is UpdateCheckResult.Failure -> {
                if (manual) {
                    UpdateState.Failed(result.reason)
                } else {
                    Log.w(TAG, "автопроверка не удалась: ${result.reason}")
                    UpdateState.Idle
                }
            }
        }
    }

    private suspend fun downloadInternal(forceMobile: Boolean) {
        var available = _state.value as? UpdateState.Available
        if (available == null) {
            checkInternal(manual = true)
            available = _state.value as? UpdateState.Available ?: return
        }

        val settings = settingsStore.settings.first()
        val version = available.release.version.toString()

        // Файл этой версии уже скачан: второй раз не качаем, просто ставим заново —
        // пользователь мог случайно закрыть системный установщик.
        val cached = available.cachedApk ?: cache.cached(version)
        if (cached != null) {
            Log.i(TAG, "используем скачанный ранее APK $version")
            startInstall(cached, version)
            return
        }

        if (settings.wifiOnly && !network.isUnmetered() && !forceMobile) {
            _messages.tryEmit("Обновление скачивается только по Wi-Fi")
            return
        }

        _state.value = UpdateState.Downloading(0)

        val download = downloader.download(available.apk.downloadUrl, cache.file(version)) { percent ->
            _state.value = UpdateState.Downloading(percent.coerceIn(0, 100))
        }

        when (download) {
            is ApkDownloadResult.Failure -> _state.value = UpdateState.Failed(download.reason)

            is ApkDownloadResult.Success -> startInstall(download.file, version)
        }
    }

    /**
     * Проверяет скачанный файл и запускает системный установщик.
     *
     * @param file скачанный APK;
     * @param version версия релиза.
     */
    private suspend fun startInstall(file: File, version: String) {
        if (!installedApp.isOwnApk(file)) {
            _state.value = UpdateState.Failed("Скачанный файл не похож на APK этого приложения")
            return
        }

        if (!installedApp.hasSameSignature(file)) {
            _state.value = UpdateState.NeedsReinstall(version = version, apkFile = file)
            return
        }

        _state.value = when (val install = installer.install(file)) {
            InstallResult.Started -> UpdateState.Installing
            InstallResult.NeedsPermission -> UpdateState.NeedsPermission
            is InstallResult.Failed -> UpdateState.Failed(install.reason)
        }
    }

    private companion object {
        const val TAG = "SmotrishkaUpdate"

        /** Проверять обновления автоматически не чаще раза в сутки. */
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
