package com.mops1k.smotrishka.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Epg
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Programme
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * TDD-спецификация выбора гида провайдером программы.
 *
 * Подмена на одноимённый канал другого гида допустима, только если сетки за прошедший
 * период совпадают: иначе пользователь увидел бы программу чужого канала.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgProviderTest {

    private lateinit var database: SmotrishkaDatabase
    private lateinit var store: EpgStore
    private lateinit var provider: EpgProvider

    private val now: Instant = Instant.parse("2026-11-09T20:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        store = EpgStore(database)
        provider = EpgProvider(
            channelEpgStore = ChannelEpgStore(
                dao = database.channelEpgDao(),
                epgStore = store,
                epgMatcher = EpgMatcher(),
            ),
            epgStore = store,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun channel(name: String, epgId: String?) = Channel(
        id = ChannelId("catalog"),
        name = name,
        streamUrl = "http://example.test/catalog.m3u8",
        epgId = epgId,
    )

    /** Пишет канал гида и сетку передач: `start` — часы от текущего момента. */
    private suspend fun guide(id: String, name: String, vararg hours: Long) =
        guideTitled(id = id, name = name, title = "Передача", *hours)

    /** Пишет канал гида и сетку передач с заданным названием передачи. */
    private suspend fun guideTitled(id: String, name: String, title: String, vararg hours: Long) {
        store.merge(
            Epg(
                channels = listOf(EpgChannel(id = id, displayName = name)),
                programmes = hours.map { hour ->
                    Programme(
                        channelId = id,
                        title = title,
                        start = now.plusSeconds(hour * 3600),
                        end = now.plusSeconds(hour * 3600 + 3000),
                    )
                },
            ),
        )
    }

    @Test
    fun `переставший обновляться гид уступает свежему с той же сеткой`() = runTest {
        // Привязанный гид: передачи только в прошлом, сетка та же, что у свежего.
        guide(id = "stale", name = "4Ever Drama HD UA", -24, -22)
        // Свежий гид: та же сетка в прошлом и передача сейчас.
        guide(id = "fresh", name = "4ever Drama HD", -24, -22, 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "stale")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("fresh")
    }

    @Test
    fun `подмена не делается, если сетки расходятся`() = runTest {
        guide(id = "stale", name = "4Ever Drama HD UA", -24, -22)
        guide(id = "other", name = "4ever Drama HD", -30, -28, 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "stale")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("stale")
    }

    @Test
    fun `свежий привязанный гид остаётся без подмены`() = runTest {
        guide(id = "stale", name = "4Ever Drama HD UA", -24, 0)
        guide(id = "fresh", name = "4ever Drama HD", -24, 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "stale")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("stale")
    }

    @Test
    fun `заглушка уступает гиду с настоящей передачей сейчас`() = runTest {
        // Привязанный гид пуст, у двух одноимённых: в одном заглушка, в другом — фильм.
        guideTitled(id = "filler", name = "4ever Drama HD", title = "Програма відсутня", 0)
        guideTitled(id = "real", name = "4Ever Drama UA", title = "Фільм", 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "missing")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("real")
    }

    @Test
    fun `если настоящей передачи нет нигде, гид остаётся`() = runTest {
        guideTitled(id = "filler", name = "4ever Drama HD", title = "Програма відсутня", 0)

        val resolved = provider.links(
            channels = listOf(channel("4Ever Drama HD UA", epgId = "missing")),
            stored = emptyMap(),
            now = now,
        )

        assertThat(resolved[ChannelId("catalog")]).isEqualTo("filler")
    }
}
