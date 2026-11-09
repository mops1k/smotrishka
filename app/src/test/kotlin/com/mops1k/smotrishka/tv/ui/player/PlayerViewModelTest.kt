package com.mops1k.smotrishka.tv.ui.player

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.ChannelEpgStore
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.SmotrishkaDatabase
import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import com.mops1k.smotrishka.tv.core.model.Category
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Epg
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Playlist
import com.mops1k.smotrishka.tv.core.model.Programme
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * TDD-спецификация плеера: по id канала экран получает название и адрес потока из каталога,
 * а для неизвестного канала показывает понятную ошибку, а не пустой экран.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerViewModelTest {

    private lateinit var database: SmotrishkaDatabase
    private lateinit var catalogStore: CatalogStore
    private lateinit var epgStore: EpgStore
    private lateinit var settingsStore: PlayerSettingsStore
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var settingsScope: CoroutineScope

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val now: Instant = Instant.parse("2026-11-02T20:00:00Z")

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
        settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = settingsScope) {
            File(tempFolder.root, "player.preferences_pb")
        }
        settingsStore = PlayerSettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        settingsScope.cancel()
        database.close()
    }

    private fun viewModel(channelId: String, startEpoch: Long = PlayerViewModel.NO_START) = PlayerViewModel(
        catalogStore = catalogStore,
        epgStore = epgStore,
        epgMatcher = EpgMatcher(),
        channelEpgStore = ChannelEpgStore(
            dao = database.channelEpgDao(),
            epgStore = epgStore,
            epgMatcher = EpgMatcher(),
        ),
        settingsStore = settingsStore,
        recentChannels = com.mops1k.smotrishka.tv.core.data.RecentChannelsStore(dataStore),
        favoritesStore = com.mops1k.smotrishka.tv.core.data.FavoritesStore(dataStore),
        clock = Clock.fixed(now, ZoneOffset.UTC),
        savedStateHandle = SavedStateHandle(
            mapOf(
                PlayerViewModel.ARG_CHANNEL_ID to channelId,
                PlayerViewModel.ARG_START to startEpoch,
            ),
        ),
    ).also { viewModels += it }

    private suspend fun playlist() {
        catalogStore.replaceCatalog(
            Playlist(
                categories = listOf(Category(id = "новости", name = "Новости")),
                channels = listOf(
                    Channel(
                        id = ChannelId("one"),
                        name = "Первый",
                        streamUrl = "http://example.com/one/index.m3u8",
                        logoUrl = "http://example.com/one.png",
                        categoryId = "новости",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `канал из каталога открывается по своему адресу`() = runTest {
        playlist()

        val state = viewModel("one").state.first { it !is PlayerUiState.Loading } as PlayerUiState.Content

        assertThat(state.title).isEqualTo("Первый")
        assertThat(state.streamUrl).isEqualTo("http://example.com/one/index.m3u8")
        assertThat(state.logoUrl).isEqualTo("http://example.com/one.png")
    }

    @Test
    fun `канал с url вместо идентификатора открывается после декодирования`() = runTest {
        val urlId = "url:http://localhost/iptv/00000000000000/16070/index.m3u8"
        catalogStore.replaceCatalog(
            Playlist(
                categories = listOf(Category(id = "кино", name = "Кино")),
                channels = listOf(
                    Channel(
                        id = ChannelId(urlId),
                        name = "Горящий камин",
                        streamUrl = "http://example.com/16070/index.m3u8",
                        logoUrl = null,
                        categoryId = "кино",
                    ),
                ),
            ),
        )

        val state = viewModel(Uri.encode(urlId))
            .state
            .first { it !is PlayerUiState.Loading } as PlayerUiState.Content

        assertThat(state.title).isEqualTo("Горящий камин")
    }

    @Test
    fun `архивная передача открывается с параметрами utc и lutc`() = runTest {
        val start = now.minusSeconds(7200)
        catalogStore.replaceCatalog(
            Playlist(
                categories = listOf(Category(id = "кино", name = "Кино")),
                channels = listOf(
                    Channel(
                        id = ChannelId("arch"),
                        name = "Архивный",
                        streamUrl = "http://example.com/arch/index.m3u8",
                        logoUrl = null,
                        categoryId = "кино",
                        archive = ArchiveInfo(mode = CatchupMode.DEFAULT, days = 7),
                    ),
                ),
            ),
        )

        val state = viewModel("arch", startEpoch = start.epochSecond)
            .state
            .first { it !is PlayerUiState.Loading } as PlayerUiState.Content

        // Адрес стабильный: только начало передачи. lutc подставляется в момент запроса
        // в ArchiveRecorder (иначе обновление программы перезапускало архив).
        assertThat(state.streamUrl).contains("utc=${start.epochSecond}")
        assertThat(state.streamUrl).doesNotContain("lutc=")
    }

    @Test
    fun `архив без поддержки канала даёт понятную ошибку`() = runTest {
        playlist()

        val state = viewModel("one", startEpoch = now.minusSeconds(7200).epochSecond)
            .state
            .first { it !is PlayerUiState.Loading }

        assertThat(state).isInstanceOf(PlayerUiState.Error::class.java)
        assertThat((state as PlayerUiState.Error).reason).contains("архива")
    }

    @Test
    fun `неизвестный канал даёт понятную ошибку`() = runTest {
        playlist()

        val state = viewModel("missing").state.first { it !is PlayerUiState.Loading }

        assertThat(state).isInstanceOf(PlayerUiState.Error::class.java)
        assertThat((state as PlayerUiState.Error).reason).contains("Канал")
    }

    @Test
    fun `пустой каталог даёт понятную ошибку`() = runTest {
        val state = viewModel("one").state.first { it !is PlayerUiState.Loading }

        assertThat(state).isInstanceOf(PlayerUiState.Error::class.java)
    }

    @Test
    fun `в osd показывается текущая передача`() = runTest {
        playlist()
        epgStore.replace(
            Epg(
                channels = listOf(EpgChannel(id = "one", displayName = "Первый")),
                programmes = listOf(
                    Programme(
                        channelId = "one",
                        title = "Идёт сейчас",
                        start = now.minusSeconds(1800),
                        end = now.plusSeconds(1800),
                    ),
                ),
            ),
        )

        val state = viewModel("one").state.first { it !is PlayerUiState.Loading } as PlayerUiState.Content

        assertThat(state.programmeTitle).isEqualTo("Идёт сейчас")
        assertThat(state.archiveAvailable).isFalse()
    }

    @Test
    fun `без программы в epg название передачи пустое`() = runTest {
        playlist()

        val state = viewModel("one").state.first { it !is PlayerUiState.Loading } as PlayerUiState.Content

        assertThat(state.programmeTitle).isNull()
    }

    @Test
    fun `список для оверлея идёт в том же порядке, что и переключение`() = runTest {
        catalogStore.replaceCatalog(
            Playlist(
                categories = listOf(
                    Category(id = "новости", name = "Новости"),
                    Category(id = "кино", name = "Кино"),
                ),
                channels = listOf(
                    Channel(
                        id = ChannelId("b"),
                        name = "Бета",
                        streamUrl = "http://example.test/b/index.m3u8",
                        logoUrl = null,
                        categoryId = "новости",
                    ),
                    Channel(
                        id = ChannelId("a"),
                        name = "Альфа",
                        streamUrl = "http://example.test/a/index.m3u8",
                        logoUrl = null,
                        categoryId = "новости",
                    ),
                    Channel(
                        id = ChannelId("c"),
                        name = "Гамма",
                        streamUrl = "http://example.test/c/index.m3u8",
                        logoUrl = null,
                        categoryId = "новости",
                    ),
                    Channel(
                        id = ChannelId("z"),
                        name = "Чужой",
                        streamUrl = "http://example.test/z/index.m3u8",
                        logoUrl = null,
                        categoryId = "кино",
                    ),
                ),
            ),
        )
        settingsStore.setLastCategory("новости")
        val model = viewModel("b")

        val scope = model.channelsInScope.first()
        val pair = model.neighbours.first()

        // В оверлее только каналы своей категории и в том же порядке, что у переключения.
        assertThat(scope.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(pair).isEqualTo("a" to "c")
    }

    @Test
    fun `в оверлее каналов у строки есть архив, описание и время передачи`() = runTest {
        catalogStore.replaceCatalog(
            Playlist(
                categories = listOf(Category(id = "кино", name = "Кино")),
                channels = listOf(
                    Channel(
                        id = ChannelId("arch"),
                        name = "Архивный",
                        streamUrl = "http://example.test/arch/index.m3u8",
                        logoUrl = null,
                        categoryId = "кино",
                        archive = ArchiveInfo(mode = CatchupMode.DEFAULT, days = 7),
                    ),
                ),
            ),
        )
        epgStore.replace(
            Epg(
                channels = listOf(EpgChannel(id = "arch", displayName = "Архивный")),
                programmes = listOf(
                    Programme(
                        channelId = "arch",
                        title = "Идёт сейчас",
                        description = "Рассказ о канале",
                        start = now.minusSeconds(1800),
                        end = now.plusSeconds(1800),
                    ),
                ),
            ),
        )

        val scope = viewModel("arch")
            .channelsInScope
            .first { list -> list.any { it.programmeDescription != null } }
        val row = scope.first { it.id == "arch" }

        assertThat(row.hasArchive).isTrue()
        assertThat(row.programmeTitle).isEqualTo("Идёт сейчас")
        assertThat(row.programmeDescription).isEqualTo("Рассказ о канале")
        assertThat(row.programmeStart).isEqualTo(now.minusSeconds(1800))
        assertThat(row.programmeEnd).isEqualTo(now.plusSeconds(1800))
    }

    @Test
    fun `в недавних каналах у строки есть описание текущей передачи`() = runTest {
        playlist()
        epgStore.replace(
            Epg(
                channels = listOf(EpgChannel(id = "one", displayName = "Первый")),
                programmes = listOf(
                    Programme(
                        channelId = "one",
                        title = "Идёт сейчас",
                        description = "Рассказ о канале",
                        start = now.minusSeconds(1800),
                        end = now.plusSeconds(1800),
                    ),
                ),
            ),
        )

        val recents = viewModel("one")
            .recents
            .first { list -> list.any { it.programmeDescription != null } }
        val row = recents.first { it.id == "one" }

        assertThat(row.hasArchive).isFalse()
        assertThat(row.programmeTitle).isEqualTo("Идёт сейчас")
        assertThat(row.programmeDescription).isEqualTo("Рассказ о канале")
        assertThat(row.programmeStart).isEqualTo(now.minusSeconds(1800))
    }
}
