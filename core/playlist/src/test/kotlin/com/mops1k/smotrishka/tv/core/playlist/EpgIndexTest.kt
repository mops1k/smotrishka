package com.mops1k.smotrishka.tv.core.playlist

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import org.junit.Test

/**
 * TDD-спецификация индекса каналов гида.
 *
 * Импорт гида сохраняет ровно те каналы, которые провайдер может выбрать: основной и
 * брендовые. Без брендовых свежий одноимённый канал другого гида в базу не попадал,
 * и фолбэк не срабатывал (случай «4Ever Drama HD UA»).
 */
class EpgIndexTest {

    private val guides = listOf(
        EpgChannel(id = "epg-one-9149", displayName = "4Ever Drama HD UA"),
        EpgChannel(id = "iptvx-4ever-drama", displayName = "4ever Drama HD"),
        EpgChannel(id = "guide-sport", displayName = "Спорт"),
    )

    private fun channel(name: String, epgId: String? = null) = Channel(
        id = ChannelId("catalog"),
        name = name,
        streamUrl = "http://example.test/catalog.m3u8",
        epgId = epgId,
    )

    @Test
    fun `кандидаты включают основной канал и брендовые варианты из других гидов`() {
        val index = EpgIndex(guides)

        val candidates = index.candidates(channel("4Ever Drama HD UA", epgId = "epg-one-9149"))

        assertThat(candidates).containsExactly("epg-one-9149", "iptvx-4ever-drama")
    }

    @Test
    fun `чужие каналы в кандидаты не попадают`() {
        val index = EpgIndex(guides)

        val candidates = index.candidates(channel("Неизвестный канал"))

        assertThat(candidates).isEmpty()
    }
}
