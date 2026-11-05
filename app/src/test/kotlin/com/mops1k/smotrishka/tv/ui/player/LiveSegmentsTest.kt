package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация порядка сегментов эфира.
 */
class LiveSegmentsTest {

    private val first = "http://50.7.240.98/iptv/C/1/1793730530000.ts?md5=aaa"
    private val sameTime = "http://50.7.240.98/iptv/C/1/1793730530000.ts?md5=bbb"
    private val next = "http://50.7.240.98/iptv/C/1/1793730540000.ts?md5=ccc"

    @Test
    fun `метка времени берётся из имени файла`() {
        assertThat(LiveSegments.timestampOf(first)).isEqualTo(1793730530000L)
    }

    @Test
    fun `чужая ссылка не даёт метку`() {
        assertThat(LiveSegments.timestampOf("http://host/x/index.m3u8")).isNull()
    }

    @Test
    fun `повтор того же времени остаётся один, даже если md5 другой`() {
        val unique = LiveSegments.unique(listOf(first, sameTime, next))

        assertThat(unique).containsExactly(first, next).inOrder()
    }

    @Test
    fun `порядок плейлиста сохраняется`() {
        assertThat(LiveSegments.unique(listOf(next, first))).containsExactly(next, first).inOrder()
    }

    @Test
    fun `между сегментами видно пропуск`() {
        assertThat(LiveSegments.gapsBetween(1_000L, 31_000L)).isEqualTo(2)
        assertThat(LiveSegments.gapsBetween(1_000L, 11_000L)).isEqualTo(0)
    }
}
