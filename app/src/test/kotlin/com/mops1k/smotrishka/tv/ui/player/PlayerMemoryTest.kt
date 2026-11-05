package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация ограничений памяти плеера.
 */
class PlayerMemoryTest {

    @Test
    fun `на малом озу буфер не больше тридцати двух мегабайт`() {
        assertThat(PlayerMemory.targetBufferBytes(lowRamDevice = true))
            .isEqualTo(32 * 1024 * 1024)
    }

    @Test
    fun `на обычном устройстве буфер не больше шестидесяти четырёх мегабайт`() {
        assertThat(PlayerMemory.targetBufferBytes(lowRamDevice = false))
            .isEqualTo(64 * 1024 * 1024)
    }
}
