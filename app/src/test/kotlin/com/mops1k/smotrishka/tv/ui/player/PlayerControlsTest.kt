package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * TDD-спецификация экранного меню плеера: OSD гаснет само, но возвращается по действию
 * пользователя; пауза и звук переключаются и отражаются в состоянии.
 */
class PlayerControlsTest {

    private val hideDelay = 4000.milliseconds

    @Test
    fun `osd видно сразу после открытия`() = runTest {
        val controls = PlayerControls(scope = this, hideDelay = hideDelay)

        assertThat(controls.state.value.osdVisible).isTrue()
    }

    @Test
    fun `osd гаснет после задержки`() = runTest {
        val controls = PlayerControls(scope = this, hideDelay = hideDelay)

        advanceTimeBy(hideDelay + 1.milliseconds)
        runCurrent()

        assertThat(controls.state.value.osdVisible).isFalse()
    }

    @Test
    fun `взаимодействие возвращает osd и перезапускает таймер`() = runTest {
        val controls = PlayerControls(scope = this, hideDelay = hideDelay)
        advanceTimeBy(hideDelay + 1.milliseconds)
        runCurrent()

        controls.onInteraction()
        advanceTimeBy(hideDelay - 1.milliseconds)
        runCurrent()

        assertThat(controls.state.value.osdVisible).isTrue()

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertThat(controls.state.value.osdVisible).isFalse()
    }

    @Test
    fun `плеер стартует с воспроизведением`() = runTest {
        val controls = PlayerControls(scope = this, hideDelay = hideDelay)

        assertThat(controls.state.value.playing).isTrue()
    }

    @Test
    fun `пауза переключается и показывает osd`() = runTest {
        val controls = PlayerControls(scope = this, hideDelay = hideDelay)
        advanceTimeBy(hideDelay + 1.milliseconds)
        runCurrent()

        controls.togglePlayback()

        assertThat(controls.state.value.playing).isFalse()
        assertThat(controls.state.value.osdVisible).isTrue()

        controls.togglePlayback()
        assertThat(controls.state.value.playing).isTrue()
    }

    @Test
    fun `звук переключается`() = runTest {
        val controls = PlayerControls(scope = this, hideDelay = hideDelay)

        controls.toggleSound()
        assertThat(controls.state.value.muted).isTrue()

        controls.toggleSound()
        assertThat(controls.state.value.muted).isFalse()
    }

    @Test
    fun `состояние синхронизируется с плеером`() = runTest {
        val controls = PlayerControls(scope = this, hideDelay = hideDelay)

        controls.sync(playing = true, muted = true)

        assertThat(controls.state.value.playing).isTrue()
        assertThat(controls.state.value.muted).isTrue()
    }

    @Test
    fun `скрыть osd можно немедленно`() = runTest {
        val controls = PlayerControls(scope = this, hideDelay = hideDelay)

        controls.hideOsd()

        assertThat(controls.state.value.osdVisible).isFalse()
    }
}
