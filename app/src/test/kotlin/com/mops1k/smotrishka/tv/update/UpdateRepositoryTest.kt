package com.mops1k.smotrishka.tv.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.update.ApkDownloader
import com.mops1k.smotrishka.tv.core.data.update.GithubReleasesClient
import com.mops1k.smotrishka.tv.core.data.update.UpdateChecker
import com.mops1k.smotrishka.tv.core.data.update.UpdateSettingsStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.mops1k.smotrishka.tv.core.data.HttpTextClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * TDD-спецификация состояния обновления: настройки сохраняются, а неудачная проверка
 * или скачивание заканчиваются состоянием ошибки, а не исключением.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var repository: UpdateRepository
    private lateinit var cache: UpdateCache
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        server = MockWebServer()
        server.start()

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "update.preferences_pb")
        }

        val httpText = HttpTextClient(OkHttpClient())
        val settingsStore = UpdateSettingsStore(dataStore)
        cache = UpdateCache(File(tempFolder.root, "update")) { file ->
            file.takeIf { it.isFile }?.readText()
        }

        repository = UpdateRepository(
            context = context,
            checker = UpdateChecker(
                GithubReleasesClient(
                    http = httpText,
                    repository = "mops1k/smotrishka",
                    baseUrl = server.url("").toString().removeSuffix("/"),
                ),
            ),
            settingsStore = settingsStore,
            downloader = ApkDownloader(OkHttpClient()),
            cache = cache,
            installer = ApkInstaller(context),
            installedApp = InstalledApp(context),
            network = NetworkStatus(context),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    @Test
    fun `по умолчанию пререлизы выключены, а только Wi-Fi включено`() = runBlocking {
        val settings = withTimeout(TIMEOUT_MS) { repository.settings.first() }

        assertThat(settings.includePrereleases).isFalse()
        assertThat(settings.wifiOnly).isTrue()
    }

    @Test
    fun `переключатели обновления сохраняются`() = runBlocking {
        repository.setIncludePrereleases(true)
        repository.setWifiOnly(false)

        val settings = withTimeout(TIMEOUT_MS) {
            repository.settings.first { it.includePrereleases && !it.wifiOnly }
        }

        assertThat(settings.includePrereleases).isTrue()
        assertThat(settings.wifiOnly).isFalse()
    }

    @Test
    fun `неудачная проверка даёт состояние ошибки, а не падение`() = runBlocking {
        server.shutdown()

        repository.checkNow()

        val state = withTimeout(TIMEOUT_MS) { repository.state.first { it is UpdateState.Failed } }
        assertThat((state as UpdateState.Failed).reason).isNotEmpty()
    }

    @Test
    fun `скачивание без найденного обновления не бросает исключение`() = runBlocking {
        // Ответ 404 и для проверки релизов: любая ветка должна закончиться состоянием ошибки.
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))

        repository.downloadAndInstall()

        val state = withTimeout(TIMEOUT_MS) { repository.state.first { it is UpdateState.Failed } }
        assertThat((state as UpdateState.Failed).reason).isNotEmpty()
    }

    @Test
    fun `скрытие версии без найденного обновления ничего не ломает`() = runBlocking {
        repository.dismissCurrent()

        assertThat(repository.state.value).isEqualTo(UpdateState.Idle)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
