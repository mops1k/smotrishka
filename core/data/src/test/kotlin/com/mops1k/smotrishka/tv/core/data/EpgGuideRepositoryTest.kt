package com.mops1k.smotrishka.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * TDD-спецификация хранилища пользовательских гидов.
 *
 * Гиды лежат в базе приложения и участвуют в фоновом обновлении. Стартовый набор
 * вставляется один раз: повторная инициализация не должна плодить дубли.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgGuideRepositoryTest {

    private lateinit var database: SmotrishkaDatabase
    private lateinit var repository: EpgGuideRepository

    private val clock = Clock.fixed(Instant.parse("2026-11-05T09:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        repository = EpgGuideRepository(database, clock)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `стартовый набор вставляется в пустую базу`() = runTest {
        val added = repository.ensureDefaults()

        assertThat(added).isEqualTo(3)
        assertThat(repository.guides().map { it.name }).containsExactly(
            "epg.one",
            "EPG it999 (зеркало epg.one)",
            "iptvx.one",
        )
    }

    @Test
    fun `повторная инициализация не дублирует гиды`() = runTest {
        repository.ensureDefaults()
        val added = repository.ensureDefaults()

        assertThat(added).isEqualTo(0)
        assertThat(repository.guides()).hasSize(3)
    }

    @Test
    fun `добавленный гид сохраняется с именем и адресом`() = runTest {
        repository.ensureDefaults()
        repository.add(url = "http://example.test/epg.xml.gz", name = "Мой гид")

        val saved = repository.guides().single { it.url == "http://example.test/epg.xml.gz" }
        assertThat(saved.name).isEqualTo("Мой гид")
        assertThat(saved.addedAt).isAtLeast(clock.instant())
    }

    @Test
    fun `одинаковый адрес не добавляется дважды`() = runTest {
        repository.ensureDefaults()
        repository.add(url = "http://example.test/epg.xml.gz", name = "Мой гид")
        repository.add(url = "http://example.test/epg.xml.gz", name = "Мой гид")

        assertThat(repository.guides().count { it.url == "http://example.test/epg.xml.gz" }).isEqualTo(1)
    }

    @Test
    fun `гид удаляется по id`() = runTest {
        repository.ensureDefaults()
        val guide = repository.guides().single { it.name == "iptvx.one" }

        repository.remove(guide.id)

        assertThat(repository.guides().map { it.name }).doesNotContain("iptvx.one")
    }

    @Test
    fun `гиды упорядочены по времени добавления`() = runTest {
        repository.ensureDefaults()
        repository.add(url = "http://example.test/epg.xml.gz", name = "Мой гид")

        assertThat(repository.guides().last().name).isEqualTo("Мой гид")
    }
}
