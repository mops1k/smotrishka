package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация чтения и записи PTS в PES-заголовке.
 */
class PesTimestampTest {

    private fun header(pts: Long): ByteArray {
        val bytes = ByteArray(14)
        bytes[0] = 0
        bytes[1] = 0
        bytes[2] = 1
        bytes[3] = 0xE0.toByte()
        bytes[6] = 0x80.toByte()
        bytes[7] = 0x80.toByte()
        PesTimestamp.write(bytes, pts)
        return bytes
    }

    @Test
    fun `метка читается обратно после записи`() {
        val bytes = header(pts = 123_456_789L)

        assertThat(PesTimestamp.read(bytes)).isEqualTo(123_456_789L)
    }

    @Test
    fun `нулевая метка тоже читается`() {
        assertThat(PesTimestamp.read(header(0))).isEqualTo(0L)
    }

    @Test
    fun `чужой формат не даёт метку`() {
        assertThat(PesTimestamp.read(ByteArray(20))).isNull()
    }

    @Test
    fun `метка без флага PTS не читается`() {
        val bytes = header(pts = 1_000)
        bytes[6] = 0

        assertThat(PesTimestamp.read(bytes)).isNull()
    }
}
