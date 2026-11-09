package com.mops1k.smotrishka.tv.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.AesGcmSecretCipher
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.CatalogUpdater
import com.mops1k.smotrishka.tv.core.data.EpgLoader
import com.mops1k.smotrishka.tv.core.data.EpgGuideRepository
import com.mops1k.smotrishka.tv.core.data.EpgGuideValidator
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.HttpBytesClient
import com.mops1k.smotrishka.tv.core.data.HttpTextClient
import com.mops1k.smotrishka.tv.core.data.IedemPlaylistLoader
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.PlaylistFileReader
import com.mops1k.smotrishka.tv.core.data.PlaylistSourceLoader
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.data.SecretStore
import com.mops1k.smotrishka.tv.core.data.SmotrishkaDatabase
import com.mops1k.smotrishka.tv.core.data.SourceRepository
import com.mops1k.smotrishka.tv.core.data.SyncStatusStore
import com.mops1k.smotrishka.tv.core.model.Category
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Playlist
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import com.mops1k.smotrishka.tv.ui.home.RecordingWatchNextSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
import javax.crypto.KeyGenerator

/**
 * TDD-спецификация экрана настроек: он показывает активный источник понятным текстом,
 * и по нему можно перейти к смене плейлиста.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: SmotrishkaDatabase
    private lateinit var repository: SourceRepository
    private lateinit var settingsStore: PlayerSettingsStore
    private lateinit var catalogStore: CatalogStore
    private lateinit var epgStore: EpgStore
    private lateinit var syncStatusStore: SyncStatusStore
    private lateinit var catalogUpdater: CatalogUpdater
    private lateinit var guideRepository: EpgGuideRepository
    private lateinit var guideValidator: EpgGuideValidator
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var watchNextSync: RecordingWatchNextSync

    /** Созданные ViewModel: их скоупы гасим до resetMain, иначе корутины переживут тест. */
    private val viewModels = mutableListOf<ViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        catalogStore = CatalogStore(database)
        epgStore = EpgStore(database)

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "secrets.preferences_pb")
        }
        repository = SourceRepository(
            catalogStore,
            SecretStore(dataStore, AesGcmSecretCipher(key)),
        )
        settingsStore = PlayerSettingsStore(dataStore)
        syncStatusStore = SyncStatusStore(dataStore)

        server = MockWebServer()
        server.start()
        val httpClient = OkHttpClient()
        val httpText = HttpTextClient(httpClient)
        val loader = PlaylistSourceLoader(
            httpText = httpText,
            iedem = IedemPlaylistLoader(httpText),
            fileReader = PlaylistFileReader { M3uParseResult.Failure("файл не читается в тесте") },
        )
        catalogUpdater = CatalogUpdater(
            catalogStore = catalogStore,
            epgStore = epgStore,
            playlistLoader = loader,
            epgLoader = EpgLoader(HttpBytesClient(httpClient)),
            settings = settingsStore,
        )
        guideRepository = EpgGuideRepository(database)
        guideValidator = EpgGuideValidator(HttpBytesClient(httpClient))
        watchNextSync = RecordingWatchNextSync()
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        server.shutdown()
        scope.cancel()
        database.close()
    }

    private fun viewModel() = SettingsViewModel(
        sourceRepository = repository,
        playerSettingsStore = settingsStore,
        catalogUpdater = catalogUpdater,
        epgStore = epgStore,
        syncStatusStore = syncStatusStore,
        catalogStore = catalogStore,
        parser = com.mops1k.smotrishka.tv.core.playlist.M3uParser(),
        playlistImporter = PlaylistImporter(
            catalogStore = catalogStore,
            epgStore = epgStore,
            parser = com.mops1k.smotrishka.tv.core.playlist.M3uParser(),
            catalogUpdater = catalogUpdater,
            settings = settingsStore,
            sourceRepository = repository,
        ),
        epgGuideRepository = guideRepository,
        epgGuideValidator = guideValidator,
        watchNextSync = watchNextSync,
    ).also { viewModels += it }

    @Test
    fun `буфер по умолчанию пять секунд`() = runTest {
        assertThat(viewModel().bufferSeconds.first()).isEqualTo(5)
    }

    @Test
    fun `выбранный буфер сохраняется в настройках`() = runTest {
        val viewModel = viewModel()

        viewModel.setBufferSeconds(10)

        assertThat(viewModel.bufferSeconds.first { it == 10 }).isEqualTo(10)
    }

    @Test
    fun `выключение ленты на домашнем экране убирает канал из лаунчера`() = runTest {
        val viewModel = viewModel()

        viewModel.setWatchNextEnabled(false)

        // Запись настройки и очистка канала идут на реальном диспетчере DataStore:
        // ждём события, а не фиксируем его сразу после вызова.
        withContext(Dispatchers.Default) {
            withTimeout(AWAIT_MS) {
                while (watchNextSync.cleared == 0) delay(5)
            }
        }
        assertThat(settingsStore.watchNextEnabled.first()).isFalse()
        assertThat(watchNextSync.cleared).isAtLeast(1)
    }

    @Test
    fun `показывает плейлист по ссылке с его именем и хостом`() = runTest {
        repository.save(
            PlaylistSource.M3uUrl(url = "https://iptv-org.github.io/iptv/countries/ru.m3u", name = null),
            "Плейлист по ссылке",
        )

        val state = viewModel().state.first { it is SettingsUiState.Content } as SettingsUiState.Content

        assertThat(state.sourceLabel).isEqualTo("Плейлист по ссылке")
        assertThat(state.sourceDetail).isEqualTo("iptv-org.github.io")
    }

    @Test
    fun `показывает xtream с сервером и логином`() = runTest {
        repository.save(
            PlaylistSource.Xtream(server = "http://example.com:8080", username = "user", password = "pass"),
            "Xtream",
        )

        val state = viewModel().state.first { it is SettingsUiState.Content } as SettingsUiState.Content

        assertThat(state.sourceLabel).isEqualTo("Xtream")
        assertThat(state.sourceDetail).contains("example.com:8080")
        assertThat(state.sourceDetail).contains("user")
    }

    @Test
    fun `код провайдера показывается замаскированным`() = runTest {
        repository.save(PlaylistSource.IedemCode(code = "987654"), "iedem.tv по коду")

        val state = viewModel().state.first { it is SettingsUiState.Content } as SettingsUiState.Content

        assertThat(state.sourceLabel).isEqualTo("iedem.tv по коду")
        assertThat(state.sourceDetail).isEqualTo("••••7654")
    }

    @Test
    fun `без сохранённого источника состояние пустое`() = runTest {
        val state = viewModel().state.first { it !is SettingsUiState.Loading }

        assertThat(state).isEqualTo(SettingsUiState.Empty)
    }

    @Test
    fun `обновление показывает результат и заменяет старый каталог`() = runTest {
        catalogStore.replaceCatalog(
            Playlist(
                categories = listOf(Category(id = "старое", name = "Старое")),
                channels = listOf(
                    Channel(
                        id = ChannelId("old"),
                        name = "Старый канал",
                        streamUrl = "http://example.com/old/index.m3u8",
                        logoUrl = null,
                        categoryId = "старое",
                    ),
                ),
            ),
        )
        repository.save(PlaylistSource.M3uUrl(url = server.url("/list.m3u").toString(), name = null), "Ссылка")
        server.enqueue(
            MockResponse()
                .setBody(
                    """
                    #EXTM3U
                    #EXTINF:-1,Новый канал
                    http://example.com/new/index.m3u8
                    """.trimIndent(),
                ),
        )

        val viewModel = viewModel()
        viewModel.updateData()

        assertThat(viewModel.status.first { it != null }).contains("Каталог обновлён")

        val playlist = catalogStore.playlist()
        assertThat(playlist.channels.map { it.name }).containsExactly("Новый канал")
    }

    @Test
    fun `без источника обновление сообщает об этом`() = runTest {
        // Прогрев: первое чтение поднимает DataStore, иначе оно случается внутри корутины
        // обновления, и на медленном раннере тест не успевает дождаться статуса.
        assertThat(repository.active()).isNull()

        val viewModel = viewModel()

        viewModel.updateData()

        assertThat(viewModel.status.first { it != null }).contains("Источник не настроен")
    }

    @Test
    fun `доступный гид сохраняется после проверки`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                <tv>
                  <channel id="9052"><display-name>Первый</display-name></channel>
                </tv>
                """.trimIndent(),
            ),
        )
        val viewModel = viewModel()

        viewModel.addGuide(server.url("/epg.xml").toString())

        viewModel.guideBusy.first { !it }
        assertThat(viewModel.guides.value.map { it.url })
            .containsExactly(server.url("/epg.xml").toString())
        assertThat(viewModel.guideMessage.value).contains("Гид добавлен")
    }

    @Test
    fun `недоступный гид не сохраняется и объясняет причину`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        val viewModel = viewModel()

        viewModel.addGuide(server.url("/epg.xml").toString())

        viewModel.guideBusy.first { !it }
        assertThat(viewModel.guides.value).isEmpty()
        assertThat(viewModel.guideMessage.value).contains("404")
    }

    private companion object {
        /** Сколько реального времени ждём очистки ленты на диспетчере DataStore. */
        const val AWAIT_MS = 2_000L
    }
}
