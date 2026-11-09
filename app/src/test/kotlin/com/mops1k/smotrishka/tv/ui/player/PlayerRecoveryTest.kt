package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * TDD-спецификация восстановления потока: пауза растёт от секунды до потолка,
 * попытки не заканчиваются, каждая третья пересоздаёт плеер.
 */
class PlayerRecoveryTest {

    @Test
    fun `пауза начинается с секунды и удваивается`() {
        assertThat(PlayerRecovery.delayBefore(1)).isEqualTo(1.seconds)
        assertThat(PlayerRecovery.delayBefore(2)).isEqualTo(2.seconds)
        assertThat(PlayerRecovery.delayBefore(3)).isEqualTo(4.seconds)
        assertThat(PlayerRecovery.delayBefore(4)).isEqualTo(8.seconds)
    }

    @Test
    fun `пауза упирается в потолок и не растёт дальше`() {
        assertThat(PlayerRecovery.delayBefore(5)).isEqualTo(10.seconds)
        assertThat(PlayerRecovery.delayBefore(10)).isEqualTo(10.seconds)
        assertThat(PlayerRecovery.delayBefore(100)).isEqualTo(10.seconds)
    }

    @Test
    fun `потолок берётся из настроек`() {
        assertThat(PlayerRecovery.delayBefore(1, maxDelay = 5.seconds)).isEqualTo(1.seconds)
        assertThat(PlayerRecovery.delayBefore(4, maxDelay = 5.seconds)).isEqualTo(5.seconds)
        assertThat(PlayerRecovery.delayBefore(5, maxDelay = 15.seconds)).isEqualTo(15.seconds)
    }

    @Test
    fun `нулевая попытка не ломает паузу`() {
        assertThat(PlayerRecovery.delayBefore(0)).isEqualTo(1.seconds)
    }

    @Test
    fun `каждая третья попытка пересоздаёт плеер`() {
        assertThat(PlayerRecovery.shouldRecreate(1)).isFalse()
        assertThat(PlayerRecovery.shouldRecreate(2)).isFalse()
        assertThat(PlayerRecovery.shouldRecreate(3)).isTrue()
        assertThat(PlayerRecovery.shouldRecreate(6)).isTrue()
        assertThat(PlayerRecovery.shouldRecreate(9)).isTrue()
    }

    @Test
    fun `короткая буферизация не считается зависанием`() {
        assertThat(PlayerRecovery.shouldRestart(5.seconds)).isFalse()
    }

    @Test
    fun `долгая буферизация считается зависанием`() {
        assertThat(PlayerRecovery.shouldRestart(15.seconds)).isTrue()
        assertThat(PlayerRecovery.shouldRestart(30.seconds)).isTrue()
    }

    @Test
    fun `плеер без готовности после создания пересоздаётся`() {
        assertThat(PlayerRecovery.firstFrameTimedOut(20.seconds)).isFalse()
        assertThat(PlayerRecovery.firstFrameTimedOut(25.seconds)).isTrue()
    }

    @Test
    fun `прогресс ожидания растёт от нуля до единицы`() {
        assertThat(PlayerRecovery.retryProgress(0, 4_000)).isEqualTo(0f)
        assertThat(PlayerRecovery.retryProgress(2_000, 4_000)).isEqualTo(0.5f)
        assertThat(PlayerRecovery.retryProgress(4_000, 4_000)).isEqualTo(1f)
        assertThat(PlayerRecovery.retryProgress(9_000, 4_000)).isEqualTo(1f)
    }

    @Test
    fun `нулевая пауза сразу считается законченной`() {
        assertThat(PlayerRecovery.retryProgress(0, 0)).isEqualTo(1f)
        assertThat(PlayerRecovery.secondsLeft(0, 0)).isEqualTo(0)
    }

    @Test
    fun `отсчёт секунд округляется вверх`() {
        assertThat(PlayerRecovery.secondsLeft(0, 4_000)).isEqualTo(4)
        assertThat(PlayerRecovery.secondsLeft(1_500, 4_000)).isEqualTo(3)
        assertThat(PlayerRecovery.secondsLeft(3_900, 4_000)).isEqualTo(1)
        assertThat(PlayerRecovery.secondsLeft(4_000, 4_000)).isEqualTo(0)
    }
}
