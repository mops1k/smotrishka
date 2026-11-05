package com.mops1k.smotrishka.tv.ui.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.AesGcmSecretCipher
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.CatalogUpdater
import com.mops1k.smotrishka.tv.core.data.EpgLoader
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.HttpBytesClient
import com.mops1k.smotrishka.tv.core.data.HttpTextClient
import com.mops1k.smotrishka.tv.core.data.IedemPlaylistLoader
import com.mops1k.smotrishka.tv.core.data.PlaylistFileReader
import com.mops1k.smotrishka.tv.core.data.PlaylistSourceLoader
import com.mops1k.smotrishka.tv.core.data.SecretStore
import com.mops1k.smotrishka.tv.core.data.SmotrishkaDatabase
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.SourceRepository
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.playlist.M3uParser
import com.mops1k.smotrishka.tv.core.playlist.XmltvParser
import com.mops1k.smotrishka.tv.epg.EpgUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import kotlinx.coroutines.test.setMain
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
import javax.crypto.KeyGenerator

/**
 * TDD-спецификация онбординга: ввод проверяется без сети, а источник сохраняется
 * только после успешной загрузки каталога.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var database: SmotrishkaDatabase
    private lateinit var sourceRepository: SourceRepository
    private lateinit var updater: CatalogUpdater
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server = MockWebServer()
        server.start()

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "secrets.preferences_pb")
        }

        val catalogStore = CatalogStore(database)
        sourceRepository = SourceRepository(catalogStore, SecretStore(dataStore, AesGcmSecretCipher(key)))

        val httpText = HttpTextClient(OkHttpClient())
        updater = CatalogUpdater(
            catalogStore = catalogStore,
            epgStore = EpgStore(database),
            playlistLoader = PlaylistSourceLoader(
                httpText = httpText,
                iedem = IedemPlaylistLoader(httpText),
                fileReader = PlaylistFileReader { M3uParseResult.Failure("файл не читается в тесте") },
                parser = M3uParser(),
            ),
            epgLoader = EpgLoader(HttpBytesClient(OkHttpClient()), XmltvParser()),
            settings = PlayerSettingsStore(dataStore),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        scope.cancel()
        server.shutdown()
        database.close()
    }

    private fun viewModel() = OnboardingViewModel(sourceRepository, updater, EpgUpdater(updater))

    @Test
    fun `онбординг завершается после каталога, не дожидаясь программы передач`() = runBlocking {
        val base = server.url("/").toString().trimEnd('/')
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path.orEmpty().contains("epg")) {
                    // Гид «висит»: старое поведение держало бы экран до таймаута загрузки.
                    MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
                } else {
                    MockResponse().setBody(
                        "#EXTM3U url-tvg=\"$base/epg.xml.gz\"\n#EXTINF:-1,Первый\n$base/one.m3u8\n",
                    )
                }
        }
        val model = viewModel()
        val started = System.currentTimeMillis()

        model.submit(PlaylistSource.M3uUrl(url = "$base/list.m3u"))
        val state = model.state.first { it !is OnboardingUiState.Loading && it !is OnboardingUiState.Idle }

        assertThat(state).isInstanceOf(OnboardingUiState.Success::class.java)
        assertThat(System.currentTimeMillis() - started).isLessThan(5_000)
    }

    @Test
    fun `пустой адрес отвергается без запроса в сеть`() = runTest {
        val model = viewModel()

        model.submit(PlaylistSource.M3uUrl(url = "   "))

        val state = model.state.first { it is OnboardingUiState.Error }
        assertThat((state as OnboardingUiState.Error).reason).contains("адрес")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `успешное подключение сохраняет источник`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(
            MockResponse().setBody(
                """
                #EXTM3U
                #EXTINF:-1,Первый
                $base/one.m3u8
                """.trimIndent(),
            ),
        )

        val model = viewModel()
        model.submit(PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        model.state.first { it is OnboardingUiState.Success }
        assertThat(sourceRepository.active()).isInstanceOf(PlaylistSource.M3uUrl::class.java)
    }

    @Test
    fun `ошибка сервера показывается текстом`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(MockResponse().setResponseCode(403))

        val model = viewModel()
        model.submit(PlaylistSource.M3uUrl(url = "$base/list.m3u"))

        val state = model.state.first { it is OnboardingUiState.Error } as OnboardingUiState.Error
        assertThat(state.reason).contains("403")
        assertThat(sourceRepository.active()).isNull()
    }

    @Test
    fun `сохранённый источник подставляется для изменения`() = runTest {
        sourceRepository.save(
            PlaylistSource.M3uUrl(url = "http://example.com/list.m3u", name = "Домашний"),
            "Домашний",
        )

        val initial = viewModel().initial.first { it != null } as PlaylistSource.M3uUrl

        assertThat(initial.url).isEqualTo("http://example.com/list.m3u")
    }

    @Test
    fun `без сохранённого источника подстановки нет`() = runTest {
        assertThat(viewModel().initial.first()).isNull()
    }

    @Test
    fun `повторное сохранение заменяет активный источник`() = runTest {
        val base = server.url("/").toString().trimEnd('/')
        val body = """
            #EXTM3U
            #EXTINF:-1,Первый
            $base/one.m3u8
        """.trimIndent()

        sourceRepository.save(PlaylistSource.M3uUrl(url = "$base/old.m3u"), "Плейлист по ссылке")
        server.enqueue(MockResponse().setBody(body))

        val model = viewModel()
        model.submit(PlaylistSource.M3uUrl(url = "$base/new.m3u"))

        model.state.first { it is OnboardingUiState.Success }
        val active = sourceRepository.active() as PlaylistSource.M3uUrl
        assertThat(active.url).isEqualTo("$base/new.m3u")
    }
}
