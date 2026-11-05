package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация нарезки архива на чанки.
 */
class ArchiveChunksTest {

    @Test
    fun `часовая передача делится на минутные чанки`() {
        val offsets = ArchiveChunks.offsets(startEpochSeconds = 1_000, endEpochSeconds = 1_000 + 3_600)

        assertThat(offsets).hasSize(60)
        assertThat(offsets.first()).isEqualTo(1_000)
        assertThat(offsets.last()).isEqualTo(1_000 + 59 * 60)
    }

    @Test
    fun `пустая передача не даёт чанков`() {
        assertThat(ArchiveChunks.offsets(1_000, 1_000)).isEmpty()
    }

    @Test
    fun `конец раньше начала не даёт чанков`() {
        assertThat(ArchiveChunks.offsets(2_000, 1_000)).isEmpty()
    }
}
