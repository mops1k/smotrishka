package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * TDD-спецификация восстановления потока: паузы между попытками растут и ограничены,
 * зависание определяется по времени буферизации.
 */
class PlayerRecoveryTest {

    @Test
    fun `паузы между попытками растут`() {
        assertThat(PlayerRecovery.delayBefore(1)).isEqualTo(2.seconds)
        assertThat(PlayerRecovery.delayBefore(2)).isEqualTo(4.seconds)
        assertThat(PlayerRecovery.delayBefore(3)).isEqualTo(8.seconds)
    }

    @Test
    fun `пауза после третьей попытки не растёт дальше`() {
        assertThat(PlayerRecovery.delayBefore(4)).isEqualTo(15.seconds)
        assertThat(PlayerRecovery.delayBefore(10)).isEqualTo(15.seconds)
    }

    @Test
    fun `попытки ограничены настройкой`() {
        assertThat(PlayerRecovery.canRetry(attempt = 2, maxAttempts = 5)).isTrue()
        assertThat(PlayerRecovery.canRetry(attempt = 5, maxAttempts = 5)).isFalse()
        assertThat(PlayerRecovery.canRetry(attempt = 3, maxAttempts = 3)).isFalse()
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
}
