package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация разбора плейлиста эфира.
 */
class LivePlaylistTest {

    private val body = """
        #EXTM3U
        #EXT-X-VERSION:3
        #EXT-X-MEDIA-SEQUENCE:59447
        #EXT-X-TARGETDURATION:11
        #EXTINF:10.010000,
        http://50.7.240.98/iptv/C/1/1793730530000.ts?md5=aaa
        #EXTINF:10.010000,
        http://50.7.240.98/iptv/C/1/1793730540000.ts?md5=bbb
        #EXTINF:10.010000,
        http://50.7.240.98/iptv/C/1/1793730530000.ts?md5=ccc
    """.trimIndent()

    @Test
    fun `сегменты идут в порядке плейлиста и без повторов`() {
        val segments = LivePlaylist.segments(body)

        assertThat(segments).hasSize(2)
        assertThat(segments.first()).contains("1793730530000")
        assertThat(segments.last()).contains("1793730540000")
    }

    @Test
    fun `метка последнего сегмента известна`() {
        assertThat(LivePlaylist.lastTimestamp(body)).isEqualTo(1793730540000L)
    }

    @Test
    fun `пустой плейлист не даёт метки`() {
        assertThat(LivePlaylist.lastTimestamp("#EXTM3U")).isNull()
    }
}
