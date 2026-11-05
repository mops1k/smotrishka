package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация распознавания проблемного канала.
 */
class StreamAnomalyTest {

    @Test
    fun `откат на две секунды считается признаком`() {
        assertThat(StreamAnomaly.isRewind(previousMs = 30_000, currentMs = 28_000)).isTrue()
    }

    @Test
    fun `нормальное движение вперёд не признак`() {
        assertThat(StreamAnomaly.isRewind(previousMs = 28_000, currentMs = 30_000)).isFalse()
    }

    @Test
    fun `двух признаков недостаточно для переключения`() {
        val now = 1_000_000L
        assertThat(StreamAnomaly.shouldSwitch(listOf(now - 5_000, now - 20_000), now)).isFalse()
    }

    @Test
    fun `трёх признаков достаточно для переключения`() {
        val now = 1_000_000L
        assertThat(StreamAnomaly.shouldSwitch(listOf(now - 5_000, now - 20_000, now - 40_000), now))
            .isTrue()
    }

    @Test
    fun `один признак не переключает`() {
        val now = 1_000_000L
        assertThat(StreamAnomaly.shouldSwitch(listOf(now - 5_000), now)).isFalse()
    }

    @Test
    fun `старые признаки не считаются`() {
        val now = 1_000_000L
        assertThat(
            StreamAnomaly.shouldSwitch(listOf(now - 90_000, now - 120_000), now),
        ).isFalse()
    }
}
