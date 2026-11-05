package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация повторов скачивания сегмента.
 */
class SegmentRetryTest {

    @Test
    fun `задержки растут и упираются в максимум`() {
        assertThat(SegmentRetry.delayBefore(1)).isEqualTo(500)
        assertThat(SegmentRetry.delayBefore(3)).isEqualTo(2_000)
        assertThat(SegmentRetry.delayBefore(99)).isEqualTo(15_000)
    }

    @Test
    fun `попытки ограничены`() {
        assertThat(SegmentRetry.canRetry(1)).isTrue()
        assertThat(SegmentRetry.canRetry(SegmentRetry.MAX_ATTEMPT)).isFalse()
    }
}
