package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import org.junit.Test

/**
 * TDD-спецификация сопоставления каналов каталога с каналами EPG.
 *
 * Ошибка сопоставления показывает пользователю программу чужого канала, поэтому
 * неоднозначные имена не сопоставляются вовсе.
 */
class EpgMatcherTest {

    private val matcher = EpgMatcher()

    private fun channel(id: String, name: String, epgId: String? = null) = Channel(
        id = ChannelId(id),
        name = name,
        streamUrl = "http://example.com/$id.m3u8",
        epgId = epgId,
    )

    @Test
    fun `сопоставление по epgId`() {
        val result = matcher.match(
            channels = listOf(channel(id = "1", name = "Первый", epgId = "one")),
            epgChannels = listOf(EpgChannel(id = "one", displayName = "Первый канал")),
        )

        assertThat(result.matches[ChannelId("1")]).isEqualTo("one")
        assertThat(result.unmatched).isEmpty()
    }

    @Test
    fun `сопоставление по имени без учёта регистра и пробелов`() {
        val result = matcher.match(
            channels = listOf(channel(id = "1", name = "  первый   канал ")),
            epgChannels = listOf(EpgChannel(id = "one", displayName = "Первый канал")),
        )

        assertThat(result.matches[ChannelId("1")]).isEqualTo("one")
    }

    @Test
    fun `буква ё приравнивается к е`() {
        val result = matcher.match(
            channels = listOf(channel(id = "1", name = "Актер")),
            epgChannels = listOf(EpgChannel(id = "one", displayName = "Актёр")),
        )

        assertThat(result.matches[ChannelId("1")]).isEqualTo("one")
    }

    @Test
    fun `канал без совпадения попадает в список без программы`() {
        val result = matcher.match(
            channels = listOf(channel(id = "1", name = "Первый")),
            epgChannels = listOf(EpgChannel(id = "one", displayName = "Второй")),
        )

        assertThat(result.matches).isEmpty()
        assertThat(result.unmatched).containsExactly(ChannelId("1"))
    }

    @Test
    fun `неоднозначное имя не сопоставляется`() {
        val result = matcher.match(
            channels = listOf(channel(id = "1", name = "Кино")),
            epgChannels = listOf(
                EpgChannel(id = "a", displayName = "Кино"),
                EpgChannel(id = "b", displayName = "КИНО"),
            ),
        )

        assertThat(result.matches).isEmpty()
        assertThat(result.unmatched).containsExactly(ChannelId("1"))
    }

    @Test
    fun `epgId приоритетнее имени`() {
        val result = matcher.match(
            channels = listOf(channel(id = "1", name = "Кино", epgId = "a")),
            epgChannels = listOf(
                EpgChannel(id = "b", displayName = "Кино"),
                EpgChannel(id = "a", displayName = "Киноканал"),
            ),
        )

        assertThat(result.matches[ChannelId("1")]).isEqualTo("a")
    }
}
