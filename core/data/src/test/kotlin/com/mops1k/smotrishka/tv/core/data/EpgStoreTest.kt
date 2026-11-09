package com.mops1k.smotrishka.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Epg
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Programme
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * TDD-спецификация хранилища программы передач.
 *
 * Окно хранения — 24 часа назад и 48 часов вперёд (решение интервью): всё, что вышло
 * за него, удаляется, иначе база растёт бесконечно.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgStoreTest {

    private lateinit var database: SmotrishkaDatabase
    private lateinit var store: EpgStore

    private val now: Instant = Instant.parse("2026-11-02T20:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        store = EpgStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun programme(
        title: String,
        start: Instant,
        end: Instant,
        description: String? = null,
    ) = Programme(
        channelId = "one",
        title = title,
        start = start,
        end = end,
        description = description,
    )

    private fun epg(vararg programmes: Programme) = Epg(
        channels = listOf(EpgChannel(id = "one", displayName = "Первый")),
        programmes = programmes.toList(),
    )

    @Test
    fun `сохраняет и читает программу в окне`() = runTest {
        store.replace(epg(programme("Новости", now.minusSeconds(3600), now)))

        val programmes = store.programmes(
            channelId = "one",
            from = now.minusSeconds(7200),
            to = now.plusSeconds(7200),
        )

        assertThat(programmes.map { it.title }).containsExactly("Новости")
        assertThat(store.epgChannels().single().displayName).isEqualTo("Первый")
    }

    @Test
    fun `повторная загрузка заменяет программу`() = runTest {
        store.replace(epg(programme("Старая", now.minusSeconds(3600), now)))
        store.replace(epg(programme("Новая", now.minusSeconds(3600), now)))

        val programmes = store.programmes("one", now.minusSeconds(7200), now.plusSeconds(7200))

        assertThat(programmes.map { it.title }).containsExactly("Новая")
    }

    @Test
    fun `чтение за пределами окна не находит передачу`() = runTest {
        store.replace(epg(programme("Новости", now.minusSeconds(3600), now)))

        val programmes = store.programmes("one", now.plusSeconds(10_000), now.plusSeconds(20_000))

        assertThat(programmes).isEmpty()
    }

    @Test
    fun `обрезка удаляет старое и далёкое будущее`() = runTest {
        store.replace(
            epg(
                programme("Старая", now.minusSeconds(3 * 24 * 3600), now.minusSeconds(3 * 24 * 3600 - 3600)),
                programme("Свежая", now.minusSeconds(3600), now),
                programme("Далёкая", now.plusSeconds(4 * 24 * 3600), now.plusSeconds(4 * 24 * 3600 + 3600)),
            ),
        )

        store.prune(now, backHours = 24)

        val left = store.programmes("one", now.minusSeconds(30 * 24 * 3600), now.plusSeconds(30 * 24 * 3600))
        assertThat(left.map { it.title }).containsExactly("Свежая")
    }

    @Test
    fun `обрезка сохраняет передачи в глубине архива`() = runTest {
        store.replace(
            epg(
                programme("Глубокая", now.minusSeconds(100 * 3600), now.minusSeconds(100 * 3600 - 3600)),
                programme("Слишком старая", now.minusSeconds(200 * 3600), now.minusSeconds(200 * 3600 - 3600)),
            ),
        )

        store.prune(now, backHours = 168)

        val left = store.programmes("one", now.minusSeconds(30 * 24 * 3600), now.plusSeconds(30 * 24 * 3600))
        assertThat(left.map { it.title }).containsExactly("Глубокая")
    }

    @Test
    fun `обрезка с глубиной сутки удаляет передачу двухдневной давности`() = runTest {
        store.replace(
            epg(programme("Позавчерашняя", now.minusSeconds(48 * 3600), now.minusSeconds(47 * 3600))),
        )

        store.prune(now, backHours = 24)

        val left = store.programmes("one", now.minusSeconds(30 * 24 * 3600), now.plusSeconds(30 * 24 * 3600))
        assertThat(left).isEmpty()
    }

    @Test
    fun `дополнение дозаполняет описание, не меняя название и время`() = runTest {
        // Провайдер отдал передачу без описания, общий гид знает описание той же передачи.
        store.replace(epg(programme("Провайдер", now.minusSeconds(3600), now)))

        store.merge(
            epg(
                programme("Чужое название", now.minusSeconds(3600), now, description = "Описание из гида"),
            ),
        )

        val programmes = store.programmes("one", now.minusSeconds(7200), now.plusSeconds(7200))

        assertThat(programmes.single().title).isEqualTo("Провайдер")
        assertThat(programmes.single().description).isEqualTo("Описание из гида")
    }

    @Test
    fun `дополнение не перетирает провайдерскую передачу`() = runTest {
        store.replace(epg(programme("Провайдер", now.minusSeconds(3600), now)))

        store.merge(
            epg(
                programme("Чужое название", now.minusSeconds(3600), now),
                programme("Дополнение", now, now.plusSeconds(3600)),
            ),
        )

        val programmes = store.programmes("one", now.minusSeconds(7200), now.plusSeconds(7200))

        assertThat(programmes.map { it.title })
            .containsExactly("Провайдер", "Дополнение")
            .inOrder()
    }

    @Test
    fun `дополнение второй раз не создаёт дубликатов`() = runTest {
        store.replace(epg(programme("Провайдер", now.minusSeconds(3600), now)))
        val extra = epg(programme("Дополнение", now, now.plusSeconds(3600)))

        store.merge(extra)
        store.merge(extra)

        val programmes = store.programmes("one", now.minusSeconds(7200), now.plusSeconds(7200))

        assertThat(programmes).hasSize(2)
    }
}
