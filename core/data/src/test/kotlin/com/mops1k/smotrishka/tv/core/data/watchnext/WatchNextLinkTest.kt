package com.mops1k.smotrishka.tv.core.data.watchnext

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Ссылка карточки ленты: сборка и разбор.
 */
class WatchNextLinkTest {

    @Test
    fun `простой идентификатор разбирается обратно`() {
        val link = WatchNextLink.of("ren-tv")

        assertThat(link).isEqualTo("smotrishka://watch?channel=ren-tv")
        assertThat(WatchNextLink.channelId(link)).isEqualTo("ren-tv")
    }

    @Test
    fun `идентификатор с URL кодируется и разбирается без потерь`() {
        val id = "http://provider.example:8080/live/channel.m3u8?token=a&b=2"

        val parsed = WatchNextLink.channelId(WatchNextLink.of(id))

        assertThat(parsed).isEqualTo(id)
    }

    @Test
    fun `кириллица в идентификаторе переживает кодирование`() {
        val id = "Первый канал"

        assertThat(WatchNextLink.channelId(WatchNextLink.of(id))).isEqualTo(id)
    }

    @Test
    fun `чужая схема не считается нашей ссылкой`() {
        assertThat(WatchNextLink.channelId("https://watch?channel=1")).isNull()
        assertThat(WatchNextLink.channelId("smotrishka2://watch?channel=1")).isNull()
    }

    @Test
    fun `чужой хост не считается ссылкой на канал`() {
        assertThat(WatchNextLink.channelId("smotrishka://settings?channel=1")).isNull()
    }

    @Test
    fun `ссылка без параметра канала не разбирается`() {
        assertThat(WatchNextLink.channelId("smotrishka://watch?other=1")).isNull()
        assertThat(WatchNextLink.channelId("smotrishka://watch")).isNull()
        assertThat(WatchNextLink.channelId("smotrishka://watch?channel=")).isNull()
    }

    @Test
    fun `испорченное кодирование не разбирается`() {
        assertThat(WatchNextLink.channelId("smotrishka://watch?channel=%ZZ")).isNull()
        assertThat(WatchNextLink.channelId("smotrishka://watch?channel=%2")).isNull()
    }

    @Test
    fun `null и мусор дают null`() {
        assertThat(WatchNextLink.channelId(null)).isNull()
        assertThat(WatchNextLink.channelId("channel=1")).isNull()
        assertThat(WatchNextLink.channelId("")).isNull()
    }
}
