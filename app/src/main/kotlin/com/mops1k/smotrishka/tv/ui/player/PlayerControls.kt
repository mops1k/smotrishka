package com.mops1k.smotrishka.tv.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Управление плеером и его экранным меню (OSD).
 *
 * Логика вынесена из Compose: OSD показывается по действию пользователя и сам гаснет через
 * заданную задержку, пауза и звук переключаются здесь же. Экран только отражает состояние
 * и передаёт события.
 *
 * @param scope область для таймера автоскрытия;
 * @param hideDelay через сколько OSD гаснет без действий пользователя.
 */
class PlayerControls(
    private val scope: CoroutineScope,
    hideDelay: Duration = DEFAULT_HIDE_DELAY,
) {

    private val hideAfter = hideDelay

    private val _state = MutableStateFlow(ControlsState())

    /** Состояние управления для экрана. */
    val state: StateFlow<ControlsState> = _state.asStateFlow()

    private var hideJob: Job? = null

    init {
        scheduleHide()
    }

    /** Показывает OSD и перезапускает таймер автоскрытия. */
    fun onInteraction() {
        _state.update { it.copy(osdVisible = true) }
        scheduleHide()
    }

    /** Скрывает OSD немедленно (например, при старте воспроизведения). */
    fun hideOsd() {
        hideJob?.cancel()
        _state.update { it.copy(osdVisible = false) }
    }

    /** Переключает паузу. */
    fun togglePlayback() {
        _state.update { it.copy(playing = !it.playing) }
        onInteraction()
    }

    /** Переключает звук. */
    fun toggleSound() {
        _state.update { it.copy(muted = !it.muted) }
        onInteraction()
    }

    /**
     * Синхронизирует состояние с плеером.
     *
     * @param playing играет ли поток сейчас;
     * @param muted выключен ли звук.
     */
    fun sync(playing: Boolean, muted: Boolean) {
        _state.update { it.copy(playing = playing, muted = muted) }
    }

    private fun scheduleHide() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(hideAfter)
            _state.update { it.copy(osdVisible = false) }
        }
    }

    /**
     * Состояние управления плеером.
     *
     * @param osdVisible видно ли экранное меню;
     * @param playing идёт ли воспроизведение;
     * @param muted выключен ли звук.
     */
    data class ControlsState(
        val osdVisible: Boolean = true,
        val playing: Boolean = true,
        val muted: Boolean = false,
    )

    private companion object {
        val DEFAULT_HIDE_DELAY = 4.seconds
    }
}
