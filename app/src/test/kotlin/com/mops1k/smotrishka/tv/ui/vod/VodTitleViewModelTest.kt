package com.mops1k.smotrishka.tv.ui.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.VodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodEvent
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodStream
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodTitlePort
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodProgress
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ToggleVodFavorite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Поведение карточки медиатеки: загрузка, подпись «Продолжить», переключение избранного и ошибка.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VodTitleViewModelTest {

    private val created = mutableListOf<VodTitleViewModel>()

    private val seasonId = VodTitleId(100)
    private val movieId = VodTitleId(200)

    private val season = VodTitleDetails(
        title = VodTitle(id = seasonId, title = "Сезон 1"),
        episodes = listOf(
            VodEpisode(VodEpisodeNumber(1), VodTitleId(101), "Серия 1", "http://host/ep1.m3u8"),
            VodEpisode(VodEpisodeNumber(2), VodTitleId(102), "Серия 2", "http://host/ep2.m3u8"),
        ),
    )

    private val movie = VodTitleDetails(
        title = VodTitle(id = movieId, title = "Фильм"),
        stream = VodStream(url = "http://host/movie.m3u8"),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun viewModel(
        titleId: VodTitleId,
        details: VodTitleDetails? = season,
        failure: VodFailure? = null,
        favorites: FakeFavoritesPort = FakeFavoritesPort(),
        progress: FakeProgressPort = FakeProgressPort(),
    ): VodTitleViewModel {
        val config = FakeConfigPort(VodPortalConfig.from("http://portal.example/", "key")!!)
        val bus = FakeEventBus()
        return VodTitleViewModel(
            savedState = SavedStateHandle(mapOf(VodTitleViewModel.ARG_TITLE_ID to titleId.value.toString())),
            loadVodTitleDetails = LoadVodTitleDetails(FakeTitlePort(details, failure), config),
            observeVodFavorites = ObserveVodFavorites(favorites),
            observeVodProgress = ObserveVodProgress(progress),
            toggleVodFavorite = ToggleVodFavorite(favorites, bus, VodClock { 1_000L }),
        ).also { created += it }
    }

    @Test
    fun `карточка сезона показывает серии и «Смотреть» без истории`() {
        val vm = viewModel(seasonId)

        val content = vm.state.value as VodTitleUiState.Content
        assertThat(content.details.episodes).hasSize(2)
        assertThat(content.favorite).isFalse()
        assertThat(content.watchLabel).isEqualTo("Смотреть")
    }

    @Test
    fun `сохранённая серия даёт подпись «Продолжить»`() {
        val progress = FakeProgressPort().apply {
            kotlinx.coroutines.runBlocking {
                save(
                    VodPlaybackProgress(
                        titleId = seasonId,
                        episodeId = VodTitleId(102),
                        episodeNumber = VodEpisodeNumber(2),
                        positionMillis = 60_000,
                        durationMillis = 600_000,
                    ),
                )
            }
        }
        val vm = viewModel(seasonId, progress = progress)

        val content = vm.state.value as VodTitleUiState.Content
        assertThat(content.watchLabel).isEqualTo("Продолжить: серия 2")
    }

    @Test
    fun `фильм с сохранённой позицией подписывает время`() {
        val progress = FakeProgressPort().apply {
            kotlinx.coroutines.runBlocking {
                save(
                    VodPlaybackProgress(
                        titleId = movieId,
                        positionMillis = 90_000,
                        durationMillis = 600_000,
                    ),
                )
            }
        }
        val vm = viewModel(movieId, details = movie, progress = progress)

        val content = vm.state.value as VodTitleUiState.Content
        assertThat(content.watchLabel).isEqualTo("Продолжить с 01:30")
    }

    @Test
    fun `качества карточки попадают в состояние`() {
        val withVariants = VodTitleDetails(
            title = VodTitle(id = movieId, title = "Фильм"),
            stream = VodStream(
                url = "http://host/master.m3u8",
                qualities = mapOf(
                    VodQuality.HD_720 to "http://host/720.m3u8",
                    VodQuality.FULL_HD_1080 to "http://host/1080.m3u8",
                ),
            ),
        )
        val vm = viewModel(titleId = movieId, details = withVariants)

        val content = vm.state.value as VodTitleUiState.Content
        assertThat(content.qualities.keys)
            .containsExactly(VodQuality.HD_720, VodQuality.FULL_HD_1080)
    }

    @Test
    fun `сердце добавляет карточку в избранное`() {
        val favorites = FakeFavoritesPort()
        val vm = viewModel(seasonId, favorites = favorites)

        vm.toggleFavorite()

        assertThat((vm.state.value as VodTitleUiState.Content).favorite).isTrue()
        assertThat(favorites.current.items.map { it.title.id.value }).containsExactly(seasonId.value)
    }

    @Test
    fun `повторное нажатие убирает карточку из избранного`() {
        val favorites = FakeFavoritesPort()
        val vm = viewModel(seasonId, favorites = favorites)
        vm.toggleFavorite()

        vm.toggleFavorite()

        assertThat((vm.state.value as VodTitleUiState.Content).favorite).isFalse()
        assertThat(favorites.current.items).isEmpty()
    }

    @Test
    fun `ошибка загрузки показывается текстом`() {
        val vm = viewModel(seasonId, details = null, failure = VodFailure.BadFormat)

        val error = vm.state.value as VodTitleUiState.Error
        assertThat(error.reason).contains("неожиданный ответ")
    }

    @Test
    fun `позиция форматируется в минуты и секунды`() {
        assertThat(formatPosition(90_000)).isEqualTo("01:30")
        assertThat(formatPosition(3_600_000)).isEqualTo("1:00:00")
    }

    /** Портал с карточкой или ошибкой. */
    private class FakeTitlePort(
        private val details: VodTitleDetails?,
        private val failure: VodFailure? = null,
    ) : VodTitlePort {
        override suspend fun loadDetails(
            config: VodPortalConfig,
            titleId: VodTitleId,
        ): VodResult<VodTitleDetails> = when {
            failure != null -> VodResult.Failure(failure)
            details != null -> VodResult.Success(details)
            else -> VodResult.Failure(VodFailure.BadFormat)
        }

        override suspend fun loadQualities(
            config: VodPortalConfig,
            titleId: VodTitleId,
        ): VodResult<Map<VodQuality, String>> = VodResult.Success(emptyMap())
    }

    /** Подключение в памяти. */
    private class FakeConfigPort(config: VodPortalConfig) : VodConfigPort {
        private val state = MutableStateFlow<VodPortalStatus>(VodPortalStatus.Connected(config))
        override val status: Flow<VodPortalStatus> = state
        override suspend fun saveConnected(config: VodPortalConfig) {
            state.value = VodPortalStatus.Connected(config)
        }

        override suspend fun saveFailed(config: VodPortalConfig, failure: VodFailure) {
            state.value = VodPortalStatus.Failed(config, failure)
        }

        override suspend fun clear() {
            state.value = VodPortalStatus.NotConfigured
        }
    }

    /** Избранное в памяти. */
    private class FakeFavoritesPort : VodFavoritesPort {
        private val state = MutableStateFlow(VodFavorites())

        /** Текущее избранное без подписки. */
        val current: VodFavorites get() = state.value

        override val favorites: Flow<VodFavorites> = state

        override suspend fun replace(favorites: VodFavorites) {
            state.value = favorites
        }

        override suspend fun clear() {
            state.value = VodFavorites()
        }
    }

    /** История просмотра в памяти. */
    private class FakeProgressPort : VodPlaybackProgressPort {
        private val state = MutableStateFlow<List<VodPlaybackProgress>>(emptyList())
        override val progress: Flow<List<VodPlaybackProgress>> = state

        override suspend fun save(progress: VodPlaybackProgress) {
            state.value = state.value.filterNot { it.titleId == progress.titleId } + progress
        }

        override suspend fun clear() {
            state.value = emptyList()
        }
    }

    /** Шина событий без подписчиков. */
    private class FakeEventBus : VodEventBus {
        /** Последнее опубликованное событие. */
        var lastEvent: VodEvent? = null
            private set

        override val events: Flow<VodEvent> = kotlinx.coroutines.flow.emptyFlow()

        override suspend fun publish(event: VodEvent) {
            lastEvent = event
        }
    }
}
