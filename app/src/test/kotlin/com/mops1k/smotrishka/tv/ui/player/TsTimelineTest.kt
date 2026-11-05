package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация выравнивания меток времени.
 */
class TsTimelineTest {

    @Test
    fun `сдвиг выравнивает фактическое начало на ожидаемое`() {
        val offset = TsTimeline.offset(expectedStart = 10_000, actualStart = 4_000)

        assertThat(TsTimeline.apply(4_000, offset)).isEqualTo(10_000)
        assertThat(TsTimeline.apply(5_000, offset)).isEqualTo(11_000)
    }

    @Test
    fun `переполнение 33-битного поля учитывается`() {
        val nearWrap = TsTimeline.WRAP - 100

        assertThat(TsTimeline.apply(nearWrap, offset = 200)).isEqualTo(100)
    }

    @Test
    fun `отрицательный сдвиг не уводит метку в минус`() {
        assertThat(TsTimeline.apply(100, offset = -50)).isEqualTo(50)
        assertThat(TsTimeline.apply(10, offset = -50)).isEqualTo(TsTimeline.WRAP - 40)
    }

    @Test
    fun `ожидаемое начало следующего сегмента`() {
        assertThat(TsTimeline.expectedNextStart(5_000, 2_000)).isEqualTo(7_000)
    }

    @Test
    fun `маленькое расхождение не требует сдвига`() {
        assertThat(TsTimeline.needsShift(expectedStart = 10_000, actualStart = 10_020)).isFalse()
        assertThat(TsTimeline.needsShift(expectedStart = 10_000, actualStart = 46_000)).isTrue()
    }
}
