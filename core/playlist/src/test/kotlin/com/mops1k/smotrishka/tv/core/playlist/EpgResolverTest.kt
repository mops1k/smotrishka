package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import org.junit.Test

/**
 * TDD-спецификация единого поиска гида: один порядок для всех экранов.
 *
 * Сохранённая связь важнее названия, `epgId` канала (`tvg-id`) важнее названия,
 * а канал без гида не должен получать передачу чужой станции.
 */
class EpgResolverTest {

    private fun channel(id: String, name: String, epgId: String? = null) = Channel(
        id = ChannelId(id),
        name = name,
        streamUrl = "http://example.test/$id/index.m3u8",
        epgId = epgId,
    )

    private val epgChannels = listOf(
        EpgChannel(id = "guide-main", displayName = "Первый"),
        EpgChannel(id = "guide-alt", displayName = "Другой"),
        EpgChannel(id = "guide-sport", displayName = "Спорт"),
    )

    @Test
    fun `сохранённая связь важнее названия`() {
        val channel = channel("a", "Первый")

        val resolved = EpgResolver.resolve(
            channels = listOf(channel),
            links = mapOf(ChannelId("a") to "guide-alt"),
            epgChannels = epgChannels,
        )

        assertThat(resolved[ChannelId("a")]).isEqualTo("guide-alt")
    }

    @Test
    fun `без связи канал находится по epgId`() {
        val channel = channel("b", "Спорт", epgId = "guide-main")

        val resolved = EpgResolver.resolve(
            channels = listOf(channel),
            links = emptyMap(),
            epgChannels = epgChannels,
        )

        assertThat(resolved[ChannelId("b")]).isEqualTo("guide-main")
    }

    @Test
    fun `без связи и epgId канал находится по названию`() {
        val channel = channel("c", "Другой")

        val resolved = EpgResolver.resolve(
            channels = listOf(channel),
            links = emptyMap(),
            epgChannels = epgChannels,
        )

        assertThat(resolved[ChannelId("c")]).isEqualTo("guide-alt")
    }

    @Test
    fun `непустая таблица связей не мешает сопоставлению по названию`() {
        // Раньше оверлей списка каналов в этом случае оставлял канал без программы.
        val linked = channel("a", "Первый")
        val unlinked = channel("b", "Спорт")

        val resolved = EpgResolver.resolve(
            channels = listOf(linked, unlinked),
            links = mapOf(ChannelId("a") to "guide-alt"),
            epgChannels = epgChannels,
        )

        assertThat(resolved[ChannelId("a")]).isEqualTo("guide-alt")
        assertThat(resolved[ChannelId("b")]).isEqualTo("guide-sport")
    }

    @Test
    fun `канал без гида остаётся без программы`() {
        val channel = channel("d", "Неизвестный канал")

        val resolved = EpgResolver.resolve(
            channels = listOf(channel),
            links = emptyMap(),
            epgChannels = epgChannels,
        )

        assertThat(resolved).isEmpty()
    }

    @Test
    fun `пустой гид не ломает сохранённые связи`() {
        val channel = channel("a", "Первый")

        val resolved = EpgResolver.resolve(
            channels = listOf(channel),
            links = mapOf(ChannelId("a") to "guide-alt"),
            epgChannels = emptyList(),
        )

        assertThat(resolved[ChannelId("a")]).isEqualTo("guide-alt")
    }
}
