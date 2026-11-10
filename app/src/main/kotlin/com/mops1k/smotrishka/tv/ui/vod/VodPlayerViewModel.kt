package com.mops1k.smotrishka.tv.ui.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mops1k.smotrishka.tv.core.data.AspectMode
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.ui.player.AspectModes
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.CompleteVodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodQualities
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.LoadVodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodProgress
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.SaveVodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.StartVodPlayback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VOD-плеер медиатеки: поток карточки или серии, сохранение позиции и автопереход к следующей
 * серии с отменяемым отсчётом.
 *
 * Какая серия следующая, решает агрегат карточки; экранная модель только спрашивает его и
 * публикует событие завершения серии.
 *
 * @param savedState аргументы маршрута;
 * @param loadVodTitleDetails карточка фильма или сезона;
 * @param loadVodQualities качества записи, если портал не прислал их в карточке;
 * @param observeVodProgress сохранённая позиция;
 * @param saveVodPlaybackProgress сохранение позиции;
 * @param startVodPlayback отметка начала просмотра;
 * @param completeVodEpisode событие «серия закончилась»;
 * @param settingsStore настройки буфера и качества.
 */
@HiltViewModel
class VodPlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val loadVodTitleDetails: LoadVodTitleDetails,
    private val loadVodQualities: LoadVodQualities,
    private val observeVodProgress: ObserveVodProgress,
    private val saveVodPlaybackProgress: SaveVodPlaybackProgress,
    private val startVodPlayback: StartVodPlayback,
    private val completeVodEpisode: CompleteVodEpisode,
    private val settingsStore: PlayerSettingsStore,
) : ViewModel() {

    private val titleId = VodTitleId(
        savedState.get<String>(ARG_TITLE_ID)?.toIntOrNull() ?: 0,
    )

    /** Серия, выбранная в карточке: пусто — продолжаем с сохранённого места. */
    private val requestedEpisodeId = savedState.get<String>(ARG_EPISODE_ID)
        ?.toIntOrNull()
        ?.let { VodTitleId(it) }

    private val _state = MutableStateFlow<VodPlayerUiState>(VodPlayerUiState.Loading)

    /** Состояние плеера. */
    val state: StateFlow<VodPlayerUiState> = _state.asStateFlow()

    /** Буфер вперёд из настроек — тот же, что у живого плеера. */
    val bufferSeconds: StateFlow<Int> = settingsStore.bufferSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PlayerSettingsStore.DEFAULT_BUFFER_SECONDS,
    )

    /** Ограничение качества из настроек. */
    val videoQuality: StateFlow<VideoQuality> = settingsStore.videoQuality.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = VideoQuality.AUTO,
    )

    private var details: VodTitleDetails? = null
    private var countdownJob: Job? = null

    /** Выбранное качество: `null` — «Авто». Переживает переходы между сериями. */
    private var selectedQuality: VodQuality? = savedState.get<String>(ARG_QUALITY)
        ?.let(VodQuality::fromKey)

    /** Последняя позиция из плеера: переключение качества идёт с того же места. */
    private var lastPositionMillis: Long = 0L

    init {
        load()
    }

    /**
     * Сохраняет позицию просмотра.
     *
     * @param positionMillis текущая позиция;
     * @param durationMillis длительность из плеера.
     */
    fun saveProgress(positionMillis: Long, durationMillis: Long?) {
        val content = _state.value as? VodPlayerUiState.Content ?: return
        lastPositionMillis = positionMillis
        viewModelScope.launch {
            saveVodPlaybackProgress(
                titleId = titleId,
                positionMillis = positionMillis,
                durationMillis = durationMillis,
                episodeId = content.episodeId,
                episodeNumber = content.episodeNumber,
            )
        }
    }

    /** Плеер дошёл до конца записи: запускаем отсчёт следующей серии или просто фиксируем конец. */
    fun onPlaybackEnded() {
        val content = _state.value as? VodPlayerUiState.Content ?: return
        saveProgress(positionMillis = content.startPositionMillis, durationMillis = null)
        val nextId = content.nextEpisodeId ?: return
        val nextNumber = content.nextEpisodeNumber ?: return
        viewModelScope.launch {
            completeVodEpisode(
                titleId = titleId,
                episodeId = content.episodeId ?: titleId,
                episodeNumber = content.episodeNumber ?: nextNumber,
                nextEpisodeId = nextId,
            )
        }
        startCountdown()
    }

    /** Отменяет автопереход: пользователь нажал «Отмена» или «назад». */
    fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        updateContent { it.copy(countdownSeconds = null) }
    }

    /** Проверяет, есть ли следующая серия, — для подсказки на экране. */
    fun hasNextEpisode(): Boolean =
        (_state.value as? VodPlayerUiState.Content)?.nextEpisodeId != null

    /** Переходит к следующей серии немедленно. */
    fun playNextEpisode() {
        cancelCountdown()
        advanceToNext()
    }

    /** Переходит к предыдущей серии немедленно. */
    fun playPreviousEpisode() {
        cancelCountdown()
        val content = _state.value as? VodPlayerUiState.Content ?: return
        val loaded = details ?: return
        val previous = content.previousEpisodeId?.let { id -> loaded.episodes.firstOrNull { it.id == id } }
            ?: return
        emit(loaded, previous, positionMillis = 0L)
    }

    /** Загружает карточку и запускает нужную серию. */
    private fun load() {
        viewModelScope.launch {
            when (val result = loadVodTitleDetails(titleId)) {
                is VodResult.Failure ->
                    _state.value = VodPlayerUiState.Error(result.failure.toMessage())

                is VodResult.Success -> {
                    details = result.value
                    val saved = observeVodProgress().first().firstOrNull { it.titleId == titleId }
                    val episode = requestedEpisodeId?.let { result.value.episode(it) }
                        ?: saved?.episodeId?.let { result.value.episode(it) }
                        ?: result.value.firstEpisode
                    // Выбранная в карточке серия стартует с начала; продолжение — с сохранённого
                    // места, если это та же серия.
                    val startAt = when {
                        requestedEpisodeId == null -> saved?.positionMillis ?: 0L
                        saved?.episodeId == requestedEpisodeId -> saved.positionMillis
                        else -> 0L
                    }
                    emit(result.value, episode, startAt)
                    ensureQualities(episode)
                    startVodPlayback(
                        titleId = titleId,
                        episodeId = episode?.id,
                        episodeNumber = episode?.number,
                    )
                }
            }
        }
    }

    /**
     * Переключает качество на ходу.
     *
     * Позиция сохраняется: плеер получает новое состояние с тем же местом и другим адресом.
     *
     * @param quality выбранное качество, `null` — «Авто».
     */
    fun selectQuality(quality: VodQuality?) {
        val content = _state.value as? VodPlayerUiState.Content ?: return
        if (content.selectedQuality == quality) return
        val loaded = details ?: return
        selectedQuality = quality
        val episode = content.episodeId?.let { loaded.episode(it) }
        emit(
            details = loaded,
            episode = episode,
            positionMillis = lastPositionMillis,
            countdownSeconds = content.countdownSeconds,
        )
    }

    /**
     * Достаёт качества запросом, если портал не прислал варианты в карточке.
     *
     * @param episode текущая серия, `null` — фильм.
     */
    private fun ensureQualities(episode: VodEpisode?) {
        val current = _state.value as? VodPlayerUiState.Content ?: return
        if (current.qualities.isNotEmpty()) return
        viewModelScope.launch {
            val target = episode?.id ?: titleId
            when (val result = loadVodQualities(target)) {
                is VodResult.Success -> updateContent { it.copy(qualities = result.value) }
                // Список не пришёл — запись играет, переключателя просто не будет.
                is VodResult.Failure -> Unit
            }
        }
    }

    /** Публикует состояние для конкретной серии. */
    private fun emit(
        details: VodTitleDetails,
        episode: VodEpisode?,
        positionMillis: Long,
        countdownSeconds: Int? = null,
    ) {
        val url = details.streamUrl(episode?.id, selectedQuality)
            ?: episode?.streamUrl
            ?: details.stream?.url
        if (url.isNullOrBlank()) {
            _state.value = VodPlayerUiState.Error("У этой карточки нет доступного потока")
            return
        }
        val next = episode?.let { details.nextEpisode(it.id) }
        val previous = episode?.let { details.previousEpisode(it.id) }
        _state.value = VodPlayerUiState.Content(
            title = details.title.title,
            streamUrl = url,
            episodeTitle = episode?.title,
            episodeId = episode?.id,
            episodeNumber = episode?.number,
            previousEpisodeId = previous?.id,
            nextEpisodeId = next?.id,
            nextEpisodeNumber = next?.number,
            startPositionMillis = positionMillis,
            countdownSeconds = countdownSeconds,
            qualities = details.qualities(episode?.id),
            selectedQuality = selectedQuality,
        )
    }

    /** Режим кадра — общая настройка с эфирным плеером. */
    val aspectMode: StateFlow<AspectMode> = settingsStore.aspectMode("vod:${titleId.value}")
        .stateIn(viewModelScope, SharingStarted.Eagerly, AspectMode.FIT)

    /** Переключает режим кадра: вписать → растянуть → обрезать → 1:1. */
    fun cycleAspectMode(current: AspectMode) {
        viewModelScope.launch {
            settingsStore.setAspectMode("vod:${titleId.value}", AspectModes.next(current))
        }
    }

    /** Отсчёт до следующей серии. */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (seconds in COUNTDOWN_SECONDS downTo 1) {
                updateContent { it.copy(countdownSeconds = seconds) }
                delay(COUNTDOWN_TICK_MILLIS)
            }
            advanceToNext()
        }
    }

    /** Включает следующую серию сезона. */
    private fun advanceToNext() {
        val content = _state.value as? VodPlayerUiState.Content ?: return
        val loaded = details ?: return
        val next = content.nextEpisodeId?.let { id -> loaded.episodes.firstOrNull { it.id == id } }
            ?: return
        emit(loaded, next, positionMillis = 0L)
        viewModelScope.launch {
            startVodPlayback(titleId = titleId, episodeId = next.id, episodeNumber = next.number)
        }
    }

    /** Точечно меняет текущее состояние содержимого. */
    private fun updateContent(transform: (VodPlayerUiState.Content) -> VodPlayerUiState.Content) {
        val content = _state.value as? VodPlayerUiState.Content ?: return
        _state.value = transform(content)
    }

    companion object {

        /** Аргумент маршрута: идентификатор карточки. */
        const val ARG_TITLE_ID = "titleId"

        /** Аргумент маршрута: выбранная серия, пустая строка — продолжение с места. */
        const val ARG_EPISODE_ID = "episode"

        /** Аргумент маршрута: выбранное качество (`auto`, `1080`), пусто — «Авто». */
        const val ARG_QUALITY = "quality"

        private const val COUNTDOWN_SECONDS = 10
        private const val COUNTDOWN_TICK_MILLIS = 1_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
