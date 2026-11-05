package com.mops1k.smotrishka.tv.ui.programme

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.SmotrishkaDatabase
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * TDD-спецификация экрана программы: прошедшие, текущая и будущие передачи канала.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgrammeViewModelTest {

    private lateinit var database: SmotrishkaDatabase
    private lateinit var catalogStore: CatalogStore
    private lateinit var epgStore: EpgStore
    private lateinit var dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var settingsScope: kotlinx.coroutines.CoroutineScope

    @get:org.junit.Rule
    val tempFolder = org.junit.rules.TemporaryFolder()

    private val now: Instant = Instant.parse("2026-11-03T20:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        catalogStore = CatalogStore(database)
        epgStore = EpgStore(database)
        settingsScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = settingsScope) {
            java.io.File(tempFolder.root, "programme.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        settingsScope.cancel()
        database.close()
    }

    private fun viewModel(channelId: String) = ProgrammeViewModel(
        catalogStore = catalogStore,
        epgStore = epgStore,
        epgMatcher = EpgMatcher(),
        settingsStore = PlayerSettingsStore(dataStore),
        clock = Clock.fixed(now, ZoneOffset.UTC),
        savedStateHandle = SavedStateHandle(mapOf(ProgrammeViewModel.ARG_CHANNEL_ID to channelId)),
    )

    private suspend fun playlist() {
        catalogStore.replaceCatalog(
            Playlist(
                categories = listOf(Category(id = "кино", name = "Кино")),
                channels = listOf(
                    Channel(
                        id = ChannelId("one"),
                        name = "Первый",
                        streamUrl = "http://example.com/one.m3u8",
                        logoUrl = null,
                        categoryId = "кино",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `лента содержит прошедшие текущую и будущие передачи`() = runTest {
        playlist()
        epgStore.replace(
            Epg(
                channels = listOf(EpgChannel(id = "one", displayName = "Первый")),
                programmes = listOf(
                    Programme(
                        channelId = "one",
                        title = "Прошедшая",
                        start = now.minusSeconds(7200),
                        end = now.minusSeconds(3600),
                    ),
                    Programme(
                        channelId = "one",
                        title = "Текущая",
                        start = now.minusSeconds(1800),
                        end = now.plusSeconds(1800),
                    ),
                    Programme(
                        channelId = "one",
                        title = "Будущая",
                        start = now.plusSeconds(3600),
                        end = now.plusSeconds(7200),
                    ),
                ),
            ),
        )

        val state = viewModel("one").state.first { it !is ProgrammeUiState.Loading } as ProgrammeUiState.Content

        assertThat(state.items.map { it.title }).containsExactly("Прошедшая", "Текущая", "Будущая").inOrder()
        assertThat(state.items[0].isPast).isTrue()
        assertThat(state.items[1].isCurrent).isTrue()
        assertThat(state.items[2].isCurrent).isFalse()
    }

    @Test
    fun `канал без программы сообщает об этом`() = runTest {
        playlist()

        val state = viewModel("one").state.first { it !is ProgrammeUiState.Loading }

        assertThat(state).isInstanceOf(ProgrammeUiState.Error::class.java)
    }

    @Test
    fun `неизвестный канал даёт понятную ошибку`() = runTest {
        playlist()

        val state = viewModel("missing").state.first { it !is ProgrammeUiState.Loading }

        assertThat((state as ProgrammeUiState.Error).reason).contains("не найден")
    }
}
