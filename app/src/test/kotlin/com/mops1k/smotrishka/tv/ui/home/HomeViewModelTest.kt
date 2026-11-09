package com.mops1k.smotrishka.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.CatalogStore
import com.mops1k.smotrishka.tv.core.data.ChannelEpgStore
import com.mops1k.smotrishka.tv.core.data.EpgProvider
import com.mops1k.smotrishka.tv.core.data.EpgStore
import com.mops1k.smotrishka.tv.core.data.SearchRepository
import com.mops1k.smotrishka.tv.core.data.FavoritesStore
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.RecentChannelsStore
import com.mops1k.smotrishka.tv.core.data.SmotrishkaDatabase
import com.mops1k.smotrishka.tv.core.data.SyncStatusStore
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
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
 * TDD-спецификация главного экрана: разделы, категории, поиск, карточка и телепрограмма.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: SmotrishkaDatabase
    private lateinit var catalogStore: CatalogStore
    private lateinit var epgStore: EpgStore
    private lateinit var favoritesStore: FavoritesStore
    private lateinit var recentChannels: RecentChannelsStore
    private lateinit var settingsStore: PlayerSettingsStore
    private lateinit var syncStatusStore: SyncStatusStore
    private lateinit var settingsScope: CoroutineScope
    private lateinit var watchNextSync: RecordingWatchNextSync
    private lateinit var mainDispatcher: TestDispatcher

    private val now: Instant = Instant.parse("2026-11-02T20:00:00Z")

    /** Созданные ViewModel: их скоупы гасим до resetMain, иначе корутины переживут тест. */
    private val viewModels = mutableListOf<ViewModel>()

    @Before
    fun setUp() {
        mainDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        catalogStore = CatalogStore(database)
        epgStore = EpgStore(database)
        settingsScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = settingsScope) {
            File(tempFolder.root, "home.preferences_pb")
        }
        favoritesStore = FavoritesStore(dataStore)
        recentChannels = RecentChannelsStore(dataStore)
        settingsStore = PlayerSettingsStore(dataStore)
        syncStatusStore = SyncStatusStore(dataStore)
        watchNextSync = RecordingWatchNextSync()
    }

    @After
    fun tearDown() {
        // Отложенные публикации ленты и подписки ViewModel не должны пережить тест:
        // иначе их продолжения попадают в следующий тест и ломают его планировщик.
        mainDispatcher.scheduler.advanceUntilIdle()
        viewModels.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        settingsScope.cancel()
        database.close()
    }

    private val searchRepository by lazy {
        SearchRepository(
            catalogDao = database.catalogDao(),
            epgDao = database.epgDao(),
            searchDao = database.searchDao(),
        )
    }

    private fun viewModel() = HomeViewModel(
        catalogStore = catalogStore,
        epgStore = epgStore,
        channelEpgStore = channelEpgStore(),
        settingsStore = settingsStore,
        favoritesStore = favoritesStore,
        recentChannels = recentChannels,
        syncStatusStore = syncStatusStore,
        clock = Clock.fixed(now, ZoneOffset.UTC),
        epgUpdater = epgUpdater(),
        searchRepository = searchRepository,
        watchNextSync = watchNextSync,
        epgProvider = EpgProvider(channelEpgStore = channelEpgStore(), epgStore = epgStore),
    ).also { viewModels += it }

    /** Связи каналов с гидом: на них же опирается лента «Сейчас идёт». */
    private fun channelEpgStore() = ChannelEpgStore(
        dao = database.channelEpgDao(),
        epgStore = epgStore,
        epgMatcher = EpgMatcher(),
    )

    /** Настоящий EpgUpdater на тестовых хранилищах: состояние читается, сеть не нужна. */
    private fun epgUpdater(): com.mops1k.smotrishka.tv.epg.EpgUpdater {
        val httpText = com.mops1k.smotrishka.tv.core.data.HttpTextClient(okhttp3.OkHttpClient())
        return com.mops1k.smotrishka.tv.epg.EpgUpdater(
            com.mops1k.smotrishka.tv.core.data.CatalogUpdater(
                catalogStore = catalogStore,
                epgStore = epgStore,
                playlistLoader = com.mops1k.smotrishka.tv.core.data.PlaylistSourceLoader(
                    httpText = httpText,
                    iedem = com.mops1k.smotrishka.tv.core.data.IedemPlaylistLoader(httpText),
                    fileReader = com.mops1k.smotrishka.tv.core.data.PlaylistFileReader {
                        com.mops1k.smotrishka.tv.core.playlist.M3uParseResult.Failure("нет файла")
                    },
                ),
                epgLoader = com.mops1k.smotrishka.tv.core.data.EpgLoader(
                    com.mops1k.smotrishka.tv.core.data.HttpBytesClient(okhttp3.OkHttpClient()),
                ),
                settings = settingsStore,
            ),
        )
    }

    private suspend fun playlist() {
        catalogStore.replaceCatalog(
            Playlist(
                categories = listOf(
                    Category(id = "основные", name = "Основные"),
                    Category(id = "кино", name = "Кино"),
                ),
                // Логотипы обязательны: лента «Сейчас идёт» публикует только каналы с постером
                // (TvProvider не копирует картинку, и карточка без логотипа выглядит пустой).
                channels = listOf(
                    Channel(
                        id = ChannelId("one"),
                        name = "Первый",
                        streamUrl = "http://example.com/one.m3u8",
                        logoUrl = "http://example.com/one.png",
                        categoryId = "основные",
                        epgId = "one",
                    ),
                    Channel(
                        id = ChannelId("two"),
                        name = "Киноканал",
                        streamUrl = "http://example.com/two.m3u8",
                        logoUrl = "http://example.com/two.png",
                        categoryId = "кино",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `пустой каталог даёт экран без каналов`() = runTest {
        val state = viewModel().state.first { it !is HomeUiState.Loading }

        assertThat(state).isEqualTo(HomeUiState.Empty)
    }

    @Test
    fun `повторное обновление не показывает загрузку`() = runTest {
        playlist()
        val viewModel = viewModel()
        viewModel.state.first { it is HomeUiState.Content }

        val seen = mutableListOf<HomeUiState>()
        val job = launch { viewModel.state.collect { seen += it } }
        viewModel.refresh()
        advanceUntilIdle()
        job.cancel()

        assertThat(seen.filterIsInstance<HomeUiState.Loading>()).isEmpty()
        assertThat(seen.last()).isInstanceOf(HomeUiState.Content::class.java)
    }

    @Test
    fun `плашка категорий начинается с пункта Все каналы`() = runTest {
        playlist()

        val content = viewModel().state.first { it is HomeUiState.Content } as HomeUiState.Content

        // Избранное и Недавние — разделы сайдбара, а не категории канала.
        assertThat(content.categories.map { it.name })
            .containsExactly("Все каналы", "Основные", "Кино")
            .inOrder()
        assertThat(content.channels).hasSize(2)
    }

    @Test
    fun `сейчас и далее берутся из программы передач`() = runTest {
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
                    Programme(
                        channelId = "one",
                        title = "Будет дальше",
                        start = now.plusSeconds(1800),
                        end = now.plusSeconds(5400),
                    ),
                ),
            ),
        )

        // Экран сначала отдаёт каналы без гида (быстрый срез), передачи подставляются
        // следующим проходом: ждём состояние именно с программой.
        val content = viewModel().state.first {
            it is HomeUiState.Content && it.channels.any { channel -> channel.nowTitle != null }
        } as HomeUiState.Content
        val channel = content.channels.first { it.channel.id == ChannelId("one") }

        assertThat(channel.nowTitle).isEqualTo("Идёт сейчас")
        assertThat(channel.nextTitle).isEqualTo("Будет дальше")
    }

    @Test
    fun `канал без программы помечается пустыми значениями`() = runTest {
        playlist()

        val content = viewModel().state.first { it is HomeUiState.Content } as HomeUiState.Content
        val channel = content.channels.first { it.channel.id == ChannelId("two") }

        assertThat(channel.nowTitle).isNull()
        assertThat(channel.nextTitle).isNull()
    }

    @Test
    fun `выбор категории фильтрует каналы`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }

        model.selectCategory("кино")

        val content = model.state.first {
            it is HomeUiState.Content && it.selectedCategoryId == "кино"
        } as HomeUiState.Content
        assertThat(content.channels.map { it.channel.name }).containsExactly("Киноканал")
    }

    @Test
    fun `до выбора канала карточка пуста`() = runTest {
        playlist()

        val content = viewModel().state.first { it is HomeUiState.Content } as HomeUiState.Content

        assertThat(content.selectedChannelId).isNull()
        assertThat(content.previewChannel).isNull()
    }

    @Test
    fun `выбор канала показывает его в карточке`() = runTest {
        playlist()
        val model = viewModel()
        awaitContent(model, "экран загружен") { true }

        model.selectChannel(ChannelId("two"))
        advanceUntilIdle()

        val content = model.state.first { it is HomeUiState.Content } as HomeUiState.Content
        assertThat(content.selectedChannelId).isEqualTo(ChannelId("two"))
        assertThat(content.previewChannel?.channel?.id).isEqualTo(ChannelId("two"))
    }

    @Test
    fun `быстрая прокрутка оставляет выбранным последний канал`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }

        model.selectChannel(ChannelId("one"))
        model.selectChannel(ChannelId("two"))
        advanceUntilIdle()

        val content = model.state.first { it is HomeUiState.Content } as HomeUiState.Content
        assertThat(content.selectedChannelId).isEqualTo(ChannelId("two"))
    }

    @Test
    fun `смена категории переносит выбор на канал новой категории`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }
        model.selectChannel(ChannelId("two"))
        advanceUntilIdle()

        model.selectCategory("основные")

        // Выбранного канала в новой категории нет: выбор уходит на её первый канал,
        // иначе фокус оставался бы на невидимом канале.
        val content = model.state.first {
            it is HomeUiState.Content && it.selectedCategoryId == "основные"
        } as HomeUiState.Content
        assertThat(content.selectedChannelId).isEqualTo(ChannelId("one"))
        assertThat(content.previewChannel?.channel?.id).isEqualTo(ChannelId("one"))
    }

    @Test
    fun `раздел избранного показывает только избранные каналы`() = runTest {
        playlist()
        favoritesStore.toggle("two")
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }

        model.selectSection(HomeSection.FAVORITES)

        val content = model.state.first {
            it is HomeUiState.Content && it.section == HomeSection.FAVORITES
        } as HomeUiState.Content
        assertThat(content.channels.map { it.channel.name }).containsExactly("Киноканал")
    }

    @Test
    fun `раздел недавних идёт в порядке просмотра`() = runTest {
        playlist()
        recentChannels.remember("two")
        recentChannels.remember("one")
        // Запись в DataStore идёт на реальном IO: дожидаемся её, иначе порядок читается
        // до сохранения и тест падает в CI (09.11.2026).
        awaitRecents("one", "two")
        val model = viewModel()
        // Подписка на историю асинхронная: дожидаемся, пока модель прочитает оба канала,
        // иначе раздел «Недавние» соберётся пустым (флейк 09.11.2026).
        awaitContent(model, "история прочитана") { it.recent.size == 2 }
        // Каталог должен быть в памяти: иначе selectSection уходит в асинхронную ветку
        // и раздел собирается позже, чем тест начинает его ждать.
        awaitContent(model, "каталог загружен") { it.channels.isNotEmpty() }

        model.selectSection(HomeSection.RECENT)

        val content = awaitContent(model, "раздел «Недавние» с двумя каналами") {
            it.section == HomeSection.RECENT && it.channels.size == 2
        }
        assertThat(content.channels.map { it.channel.name })
            .containsExactly("Первый", "Киноканал")
            .inOrder()
    }

    @Test
    fun `возврат из плеера открывает категорию игравшего канала`() = runTest {
        playlist()
        guide(
            EpgChannel(id = "one", displayName = "Первый") to "Новости",
            EpgChannel(id = "two", displayName = "Киноканал") to "Фильм",
        )
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }

        // В плеере переключились на канал из другой категории: на возврате список должен
        // показать именно его, а не первый канал прежней категории.
        recentChannels.remember("two")
        model.refresh(force = true)

        val content = model.state.first {
            it is HomeUiState.Content && it.selectedChannelId == ChannelId("two")
        } as HomeUiState.Content
        assertThat(content.selectedCategoryId).isEqualTo("кино")
        assertThat(content.channels.map { it.channel.id }).contains(ChannelId("two"))
    }

    @Test
    fun `поиск фильтрует каналы по подстроке без учёта регистра`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }

        model.setQuery("кино")

        val content = model.state.first {
            it is HomeUiState.Content && it.query == "кино"
        } as HomeUiState.Content
        assertThat(content.channels.map { it.channel.name }).containsExactly("Киноканал")
    }

    @Test
    fun `закрытие поиска сбрасывает запрос и фильтр`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }
        model.setQuery("кино")

        model.closeSearch()

        val content = model.state.first {
            it is HomeUiState.Content && it.query.isEmpty()
        } as HomeUiState.Content
        assertThat(content.channels).hasSize(2)
    }

    @Test
    fun `смена раздела сбрасывает категорию и сохраняет выбранный канал`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }
        model.selectChannel(ChannelId("two"))
        advanceUntilIdle()
        model.selectCategory("кино")

        model.selectSection(HomeSection.RECENT)

        val content = model.state.first {
            it is HomeUiState.Content && it.section == HomeSection.RECENT
        } as HomeUiState.Content
        assertThat(content.selectedCategoryId).isNull()
        assertThat(content.selectedChannelId).isEqualTo(ChannelId("two"))
    }

    @Test
    fun `разворот телепрограммы отдаёт расписание выбранного канала`() = runTest {
        playlist()
        epgStore.replace(
            Epg(
                channels = listOf(EpgChannel(id = "one", displayName = "Первый")),
                programmes = listOf(
                    Programme(
                        channelId = "one",
                        title = "Прошлая",
                        start = now.minusSeconds(7200),
                        end = now.minusSeconds(3600),
                    ),
                    Programme(
                        channelId = "one",
                        title = "Идёт сейчас",
                        start = now.minusSeconds(1800),
                        end = now.plusSeconds(1800),
                    ),
                ),
            ),
        )
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }
        model.selectChannel(ChannelId("one"))
        advanceUntilIdle()

        model.openProgramme()
        advanceUntilIdle()

        val content = model.state.first {
            it is HomeUiState.Content && it.programmeOpen
        } as HomeUiState.Content
        assertThat(content.schedule.map { it.programme.title })
            .containsExactly("Прошлая", "Идёт сейчас")
            .inOrder()
        assertThat(content.schedule.map { it.status })
            .containsExactly(ProgrammeStatus.PAST, ProgrammeStatus.NOW)
            .inOrder()
    }

    @Test
    fun `разворот берёт свежий гид, если привязанный перестал обновляться`() = runTest {
        playlist()
        // Привязанный гид канала «Первый» кончился сутки назад: в развороте должна быть
        // программа свежего канала того же бренда, а не пустое окно.
        epgStore.replace(
            Epg(
                channels = listOf(
                    EpgChannel(id = "old", displayName = "Первый"),
                    EpgChannel(id = "fresh", displayName = "Первый HD"),
                ),
                programmes = listOf(
                    Programme(
                        channelId = "old",
                        title = "Старая передача",
                        start = now.minusSeconds(48 * 3600),
                        end = now.minusSeconds(47 * 3600),
                    ),
                    Programme(
                        channelId = "fresh",
                        title = "Свежая передача",
                        start = now.minusSeconds(600),
                        end = now.plusSeconds(600),
                    ),
                ),
            ),
        )
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }
        model.selectChannel(ChannelId("one"))
        advanceUntilIdle()

        model.openProgramme()
        advanceUntilIdle()

        val content = model.state.first {
            it is HomeUiState.Content && it.programmeOpen
        } as HomeUiState.Content
        assertThat(content.schedule.map { it.programme.title }).contains("Свежая передача")
    }

    @Test
    fun `сворачивание телепрограммы очищает расписание`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }
        model.selectChannel(ChannelId("one"))
        advanceUntilIdle()
        model.openProgramme()
        advanceUntilIdle()

        model.closeProgramme()

        val content = model.state.first {
            it is HomeUiState.Content && !it.programmeOpen
        } as HomeUiState.Content
        assertThat(content.schedule).isEmpty()
    }

    @Test
    fun `избранное переключается и видно в состоянии`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }

        model.toggleFavorite(ChannelId("one"))

        val content = model.state.first {
            it is HomeUiState.Content && it.favorites.isNotEmpty()
        } as HomeUiState.Content
        assertThat(content.favorites).contains("one")
    }

    @Test
    fun `лента публикует каналы с текущей передачей`() = runTest {
        playlist()
        guide(
            EpgChannel(id = "one", displayName = "Первый") to "Идёт сейчас",
        )
        // Связи каналов с гидом строятся при обновлении каталога: без них первый проход
        // экрана идёт без передач, и лента публикуется только следующим проходом.
        channelEpgStore().rebuild(catalogStore.playlist().channels)
        val model = viewModel()
        model.state.first { it is HomeUiState.Content && it.channels.any { c -> c.nowTitle != null } }

        val cards = awaitPublished()

        assertThat(cards.map { it.channelId }).containsExactly("one")
        assertThat(cards.single().subtitle).isEqualTo("Идёт сейчас")
    }

    @Test
    fun `смена категории поднимает её каналы в начало ленты`() = runTest {
        playlist()
        guide(
            EpgChannel(id = "one", displayName = "Первый") to "Новости",
            EpgChannel(id = "two", displayName = "Киноканал") to "Фильм",
        )
        channelEpgStore().rebuild(catalogStore.playlist().channels)
        val model = viewModel()
        model.state.first { it is HomeUiState.Content && it.channels.any { c -> c.nowTitle != null } }

        model.selectCategory("кино")

        // Категория — третий приоритет: её канал идёт первым, остальные могут добраться дальше.
        val cards = awaitPublished { it.isNotEmpty() && it.first().channelId == "two" }
        assertThat(cards.first().channelId).isEqualTo("two")
        assertThat(cards.map { it.channelId }).containsNoDuplicates()
    }

    @Test
    fun `избранный канал идёт в ленте первым`() = runTest {
        playlist()
        guide(
            EpgChannel(id = "one", displayName = "Первый") to "Новости",
            EpgChannel(id = "two", displayName = "Киноканал") to "Фильм",
        )
        channelEpgStore().rebuild(catalogStore.playlist().channels)
        val model = viewModel()
        model.state.first { it is HomeUiState.Content && it.channels.any { c -> c.nowTitle != null } }

        model.toggleFavorite(ChannelId("two"))
        model.refresh(force = true)

        val cards = awaitPublished { it.firstOrNull()?.channelId == "two" }
        assertThat(cards.first().subtitle).isEqualTo("Фильм")
        assertThat(cards.map { it.channelId }).containsNoDuplicates()
    }

    @Test
    fun `нет каналов с передачей — лента убирается из лаунчера`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }

        model.refresh(force = true)
        awaitCleared()
    }

    @Test
    fun `выключенная лента убирается при обновлении экрана`() = runTest {
        playlist()
        val model = viewModel()
        model.state.first { it is HomeUiState.Content }

        settingsStore.setWatchNextEnabled(false)
        assertThat(settingsStore.watchNextEnabled.first()).isFalse()

        model.refresh(force = true)
        awaitCleared()
    }

    /**
     * Дожидается публикации ленты.
     *
     * Публикация отложена на [HomeViewModel.WATCH_NEXT_DEBOUNCE_MS], а тумблер читается из
     * DataStore на реальном диспетчере: виртуальное время прокручивается, а между шагами
     * даётся несколько миллисекунд реального времени, иначе ввод-вывод не успевает.
     */
    private suspend fun TestScope.awaitPublished(
        predicate: (List<com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextCard>) -> Boolean = { it.isNotEmpty() },
    ): List<com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextCard> {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            watchNextSync.last?.takeIf(predicate)?.let { return it }
            yield()
            // DataStore пишет на реальном IO: даём ему время между прокрутками времени.
            withContext(Dispatchers.Default) { delay(AWAIT_REAL_MS) }
        }
        throw AssertionError("лента не опубликована: ${watchNextSync.published}")
    }

    /** Дожидается, что лента убрана из лаунчера. */
    private suspend fun TestScope.awaitCleared() {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            if (watchNextSync.cleared > 0) return
            yield()
            withContext(Dispatchers.Default) { delay(AWAIT_REAL_MS) }
        }
        throw AssertionError("лента не убрана из лаунчера")
    }

    /** Дожидается, что недавние каналы сохранены в DataStore в указанном порядке. */
    private suspend fun awaitRecents(vararg expectedIds: String) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val stored = runCatching { recentChannels.channels.first() }.getOrDefault(emptyList())
            if (stored == expectedIds.toList()) return
            withContext(Dispatchers.Default) { delay(AWAIT_REAL_MS) }
        }
        throw AssertionError("недавние не сохранились: ожидалось ${expectedIds.toList()}")
    }

    /** Гид с текущими передачами: по передаче на каждый канал. */
    private suspend fun guide(vararg programmes: Pair<EpgChannel, String>) {
        epgStore.replace(
            Epg(
                channels = programmes.map { it.first },
                programmes = programmes.map { (channel, title) ->
                    Programme(
                        channelId = channel.id,
                        title = title,
                        start = now.minusSeconds(1800),
                        end = now.plusSeconds(1800),
                    )
                },
            ),
        )
    }

    @Test
    fun `открытие категории не запускает живое превью без истории`() = runTest {
        playlist()
        // Передача уже закончилась: экран пересобирается сам по тику, как на устройстве.
        epgStore.replace(
            Epg(
                channels = listOf(EpgChannel("one", "Первый")),
                programmes = listOf(
                    Programme(
                        channelId = "one",
                        title = "Новости",
                        start = now.minusSeconds(7200),
                        end = now.minusSeconds(60),
                    ),
                ),
            ),
        )
        val model = viewModel()
        awaitContent(model, "экран загружен") { true }

        model.selectCategory("основные")
        advanceUntilIdle()
        val opened = model.state.first() as HomeUiState.Content
        assertThat(opened.selectedChannelId).isEqualTo(ChannelId("one"))

        // Пересборка экрана: превью не подтверждается само, пока канал не открыли.
        model.tick()
        advanceUntilIdle()

        val after = model.state.first() as HomeUiState.Content
        assertThat(after.previewChannel).isNotNull()
        assertThat(after.previewVideoChannelId).isNull()
        assertThat(after.previewVideoChannel).isNull()
    }

    @Test
    fun `превью открывается на последнем открытом канале из истории`() = runTest {
        playlist()
        recentChannels.remember("two")
        awaitRecents("two")
        val model = viewModel()

        // Ждём состояние, в котором превью уже переведено на канал из истории.
        val content = awaitContent(model, "превью на канале из истории") {
            it.previewVideoChannelId?.value == "two"
        }

        assertThat(content.previewVideoChannel?.channel?.name).isEqualTo("Киноканал")
    }

    @Test
    fun `фокус на канале не запускает превью до нажатия OK`() = runTest {
        playlist()
        val model = viewModel()
        awaitContent(model, "экран загружен") { true }

        model.selectChannel(ChannelId("two"))
        advanceUntilIdle()

        val after = model.state.first() as HomeUiState.Content
        assertThat(after.selectedChannelId).isEqualTo(ChannelId("two"))
        assertThat(after.previewVideoChannelId).isNull()
    }

    /** Ждёт состояние экрана, уступая реальное время: DataStore и Room работают вне виртуальных часов. */
    private suspend fun awaitContent(
        model: HomeViewModel,
        reason: String,
        predicate: (HomeUiState.Content) -> Boolean,
    ): HomeUiState.Content {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val content = model.state.value as? HomeUiState.Content
            if (content != null && predicate(content)) return content
            // Рекомендованный способ уступить реальное время внутри runTest: один поток
            // Default, без конкуренции с подменой Dispatchers.Main между тестами.
            withContext(Dispatchers.Default.limitedParallelism(1)) { delay(AWAIT_REAL_MS) }
        }
        throw AssertionError("не дождались состояния экрана: $reason")
    }

    private companion object {
        /** Сколько реального времени ждём ввода-вывода между шагами. */
        const val AWAIT_REAL_MS = 25L

        /**
         * Предел ожидания по реальному времени.
         *
         * Раньше ожидание было фиксированным (100 шагов по 25 мс) и на загруженной машине
         * не хватало: тесты падали вразнобой — каждый прогон на разных (09.11.2026).
         */
        const val AWAIT_TIMEOUT_MS = 10_000L
    }
}
