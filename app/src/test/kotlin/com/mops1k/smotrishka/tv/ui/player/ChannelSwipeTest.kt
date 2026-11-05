package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация свайпа по видео в плеере.
 *
 * Жест вертикальный: палец вверх — следующий канал, вниз — предыдущий. Короткие случайные
 * сдвиги (касание и лёгкое дрожание) канал не переключают, иначе смотреть станет невозможно.
 */
class ChannelSwipeTest {

    private val threshold = 120f

    @Test
    fun `свайп вверх переключает на следующий канал`() {
        val decision = ChannelSwipe.decide(totalDyPx = -300f, thresholdPx = threshold)

        assertThat(decision).isEqualTo(SwipeChannel.Next)
    }

    @Test
    fun `свайп вниз переключает на предыдущий канал`() {
        val decision = ChannelSwipe.decide(totalDyPx = 300f, thresholdPx = threshold)

        assertThat(decision).isEqualTo(SwipeChannel.Previous)
    }

    @Test
    fun `короткий жест канал не переключает`() {
        val decision = ChannelSwipe.decide(totalDyPx = -40f, thresholdPx = threshold)

        assertThat(decision).isEqualTo(SwipeChannel.None)
    }

    @Test
    fun `жест ровно на пороге срабатывает`() {
        assertThat(ChannelSwipe.decide(totalDyPx = -threshold, thresholdPx = threshold))
            .isEqualTo(SwipeChannel.Next)
        assertThat(ChannelSwipe.decide(totalDyPx = threshold, thresholdPx = threshold))
            .isEqualTo(SwipeChannel.Previous)
    }

    @Test
    fun `движение чуть меньше порога игнорируется`() {
        val decision = ChannelSwipe.decide(totalDyPx = -(threshold - 1f), thresholdPx = threshold)

        assertThat(decision).isEqualTo(SwipeChannel.None)
    }
}
