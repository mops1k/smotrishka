package com.mops1k.smotrishka.tv.ui.vod

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodEvent
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodStream
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodTitlePort
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.CompleteVodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodQualities
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodProgress
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.SaveVodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.StartVodPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Поведение VOD-плеера: выбор серии при входе, продолжение с сохранённого места и автопереход
 * к следующей серии с отменяемым отсчётом.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VodPlayerViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val created = mutableListOf<VodPlayerViewModel>()
    private val scope = CoroutineScope(SupervisorJob())
    private val dispatcher = StandardTestDispatcher()

    private val titleId = VodTitleId(100)
    private val episodes = listOf(
        VodEpisode(VodEpisodeNumber(1), VodTitleId(101), "Серия 1", "http://host/ep1.m3u8"),
        VodEpisode(VodEpisodeNumber(2), VodTitleId(102), "Серия 2", "http://host/ep2.m3u8"),
    )
    private val details = VodTitleDetails(
        title = VodTitle(id = titleId, title = "Сезон 1"),
        episodes = episodes,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        scope.cancel()
        Dispatchers.resetMain()
    }

    private fun settingsStore(): PlayerSettingsStore {
        // Файл должен отсутствовать: пустой файл DataStore считает повреждённым.
        val file = java.io.File(tempFolder.root, "vod-${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        return PlayerSettingsStore(dataStore)
    }

    private fun viewModel(
        progressPort: FakeProgressPort = FakeProgressPort(),
        titlePort: VodTitlePort = FakeTitlePort(details),
        quality: String? = null,
    ): VodPlayerViewModel {
        val configPort = FakeConfigPort(VodPortalConfig.from("http://portal.example/", "key")!!)
        val bus = FakeEventBus()
        val clock = VodClock { 1_000L }
        return VodPlayerViewModel(
            savedState = SavedStateHandle(
                buildMap {
                    put(VodPlayerViewModel.ARG_TITLE_ID, titleId.value.toString())
                    quality?.let { put(VodPlayerViewModel.ARG_QUALITY, it) }
                },
            ),
            loadVodTitleDetails = LoadVodTitleDetails(titlePort, configPort),
            loadVodQualities = LoadVodQualities(titlePort, configPort),
            observeVodProgress = ObserveVodProgress(progressPort),
            saveVodPlaybackProgress = SaveVodPlaybackProgress(progressPort, bus, clock),
            startVodPlayback = StartVodPlayback(progressPort, bus, clock),
            completeVodEpisode = CompleteVodEpisode(bus, clock),
            settingsStore = settingsStore(),
        ).also { created += it }
    }

    @Test
    fun `без сохранённой позиции начинается первая серия`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceTimeBy(100)

        val content = vm.state.value as VodPlayerUiState.Content
        assertThat(content.episodeNumber?.value).isEqualTo(1)
        assertThat(content.streamUrl).isEqualTo("http://host/ep1.m3u8")
        assertThat(content.nextEpisodeId?.value).isEqualTo(102)
        assertThat(content.startPositionMillis).isEqualTo(0L)
    }

    @Test
    fun `сохранённая серия и позиция восстанавливаются`() = runTest(dispatcher) {
        val progress = FakeProgressPort().apply {
            save(
                VodPlaybackProgress(
                    titleId = titleId,
                    episodeId = VodTitleId(102),
                    episodeNumber = VodEpisodeNumber(2),
                    positionMillis = 5_000,
                ),
            )
        }
        val vm = viewModel(progressPort = progress)
        advanceTimeBy(100)

        val content = vm.state.value as VodPlayerUiState.Content
        assertThat(content.episodeNumber?.value).isEqualTo(2)
        assertThat(content.streamUrl).isEqualTo("http://host/ep2.m3u8")
        assertThat(content.startPositionMillis).isEqualTo(5_000L)
    }

    @Test
    fun `предыдущая серия есть со второй и её нет у первой`() = runTest(dispatcher) {
        val progress = FakeProgressPort().apply {
            save(
                VodPlaybackProgress(
                    titleId = titleId,
                    episodeId = VodTitleId(102),
                    episodeNumber = VodEpisodeNumber(2),
                    positionMillis = 0L,
                ),
            )
        }
        val vm = viewModel(progressPort = progress)
        advanceTimeBy(100)

        val second = vm.state.value as VodPlayerUiState.Content
        assertThat(second.episodeNumber?.value).isEqualTo(2)
        assertThat(second.previousEpisodeId).isEqualTo(VodTitleId(101))

        vm.playPreviousEpisode()
        advanceTimeBy(100)

        val first = vm.state.value as VodPlayerUiState.Content
        assertThat(first.episodeNumber?.value).isEqualTo(1)
        assertThat(first.previousEpisodeId).isNull()
    }

    @Test
    fun `выбранное качество даёт адрес варианта и сохраняет позицию`() = runTest(dispatcher) {
        val movie = VodTitleDetails(
            title = VodTitle(id = titleId, title = "Фильм"),
            stream = VodStream(
                url = "http://host/master.m3u8",
                qualities = mapOf(
                    VodQuality.HD_720 to "http://host/720.m3u8",
                    VodQuality.FULL_HD_1080 to "http://host/1080.m3u8",
                ),
            ),
        )
        val vm = viewModel(titlePort = FakeTitlePort(movie))
        advanceTimeBy(100)
        vm.saveProgress(positionMillis = 30_000, durationMillis = 600_000)
        advanceTimeBy(100)

        vm.selectQuality(VodQuality.FULL_HD_1080)
        advanceTimeBy(100)

        val content = vm.state.value as VodPlayerUiState.Content
        assertThat(content.streamUrl).isEqualTo("http://host/1080.m3u8")
        assertThat(content.startPositionMillis).isEqualTo(30_000L)
        assertThat(content.selectedQuality).isEqualTo(VodQuality.FULL_HD_1080)
        assertThat(content.qualities.keys)
            .containsExactly(VodQuality.HD_720, VodQuality.FULL_HD_1080)
    }

    @Test
    fun `у сериала переключается вариант серии`() = runTest(dispatcher) {
        val season = VodTitleDetails(
            title = VodTitle(id = titleId, title = "Сезон"),
            episodes = listOf(
                VodEpisode(
                    number = VodEpisodeNumber(1),
                    id = VodTitleId(101),
                    title = "Серия 1",
                    streamUrl = "http://host/ep1.m3u8",
                    qualities = mapOf(VodQuality.SD_480 to "http://host/ep1-480.m3u8"),
                ),
            ),
        )
        val vm = viewModel(titlePort = FakeTitlePort(season))
        advanceTimeBy(100)

        vm.selectQuality(VodQuality.SD_480)
        advanceTimeBy(100)

        val content = vm.state.value as VodPlayerUiState.Content
        assertThat(content.streamUrl).isEqualTo("http://host/ep1-480.m3u8")
        assertThat(content.episodeNumber?.value).isEqualTo(1)
    }

    @Test
    fun `качество из маршрута применяется сразу`() = runTest(dispatcher) {
        val movie = VodTitleDetails(
            title = VodTitle(id = titleId, title = "Фильм"),
            stream = VodStream(
                url = "http://host/master.m3u8",
                qualities = mapOf(VodQuality.FULL_HD_1080 to "http://host/1080.m3u8"),
            ),
        )
        val vm = viewModel(titlePort = FakeTitlePort(movie), quality = "1080")
        advanceTimeBy(100)

        val content = vm.state.value as VodPlayerUiState.Content
        assertThat(content.streamUrl).isEqualTo("http://host/1080.m3u8")
        assertThat(content.selectedQuality).isEqualTo(VodQuality.FULL_HD_1080)
    }

    @Test
    fun `по окончании серии идёт отсчёт и включается следующая`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceTimeBy(100)

        vm.onPlaybackEnded()
        advanceTimeBy(100)

        assertThat((vm.state.value as VodPlayerUiState.Content).countdownSeconds).isEqualTo(10)

        advanceTimeBy(10_500)

        val content = vm.state.value as VodPlayerUiState.Content
        assertThat(content.episodeNumber?.value).isEqualTo(2)
        assertThat(content.countdownSeconds).isNull()
    }

    @Test
    fun `отмена отсчёта оставляет текущую серию`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceTimeBy(100)
        vm.onPlaybackEnded()
        advanceTimeBy(100)

        vm.cancelCountdown()
        advanceTimeBy(20_000)

        val content = vm.state.value as VodPlayerUiState.Content
        assertThat(content.episodeNumber?.value).isEqualTo(1)
        assertThat(content.countdownSeconds).isNull()
    }

    @Test
    fun `у последней серии автоперехода нет`() = runTest(dispatcher) {
        val progress = FakeProgressPort().apply {
            save(VodPlaybackProgress(titleId = titleId, episodeId = VodTitleId(102), episodeNumber = VodEpisodeNumber(2)))
        }
        val vm = viewModel(progressPort = progress)
        advanceTimeBy(100)

        vm.onPlaybackEnded()
        advanceTimeBy(100)

        assertThat((vm.state.value as VodPlayerUiState.Content).countdownSeconds).isNull()
    }

    @Test
    fun `ошибка карточки показывается текстом`() = runTest(dispatcher) {
        val vm = viewModel(titlePort = FakeTitlePort(details = null, failure = VodFailure.Network(null)))
        advanceTimeBy(100)

        val error = vm.state.value as VodPlayerUiState.Error
        assertThat(error.reason).contains("Нет связи с порталом медиатеки")
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
        val published = mutableListOf<VodEvent>()
        override val events: Flow<VodEvent> = MutableStateFlow<VodEvent?>(null)
            .let { kotlinx.coroutines.flow.emptyFlow() }

        override suspend fun publish(event: VodEvent) {
            published += event
        }
    }
}
