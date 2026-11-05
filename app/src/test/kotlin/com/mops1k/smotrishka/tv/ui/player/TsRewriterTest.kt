package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация сдвига меток в сегменте.
 */
class TsRewriterTest {

    /** Собирает один TS-пакет с PES-заголовком и меткой PTS. */
    private fun packetWithPts(pts: Long): ByteArray {
        val packet = ByteArray(TsRewriter.PACKET_SIZE)
        packet[0] = 0x47
        packet[1] = 0x51 // payload_unit_start_indicator + payload (0x10) + 0x01
        packet[2] = 0x00
        packet[3] = 0x10
        // PES начинается сразу за заголовком TS: 00 00 01, затем id, длины и флаги.
        packet[4] = 0
        packet[5] = 0
        packet[6] = 1
        packet[7] = 0xE0.toByte()
        packet[8] = 0
        packet[9] = 0
        packet[10] = 0x80.toByte()
        packet[11] = 0x80.toByte()
        packet[12] = 0x05
        val header = packet.copyOfRange(4, 18)
        PesTimestamp.write(header, pts)
        header.copyInto(packet, 4)
        return packet
    }

    @Test
    fun `метка сдвигается на заданную величину`() {
        val packet = packetWithPts(4_000)

        val shifted = TsRewriter.shift(packet, offset = 6_000)

        val header = shifted.copyOfRange(4, 18)
        assertThat(PesTimestamp.read(header)).isEqualTo(10_000)
    }

    @Test
    fun `первая метка сегмента читается`() {
        assertThat(TsRewriter.firstPts(packetWithPts(4_000))).isEqualTo(4_000)
    }

    @Test
    fun `без сдвига массив возвращается как есть`() {
        val packet = packetWithPts(4_000)

        assertThat(TsRewriter.shift(packet, offset = 0)).isSameInstanceAs(packet)
    }

    @Test
    fun `пакет без синхробайта не ломает разбор`() {
        val broken = ByteArray(TsRewriter.PACKET_SIZE)

        val shifted = TsRewriter.shift(broken, offset = 1_000)

        assertThat(shifted).hasLength(TsRewriter.PACKET_SIZE)
    }
}
