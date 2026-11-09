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

/**
 * TDD-спецификация учёта незавершённых импортов гида.
 *
 * Оборванный импорт обязан остаться в базе: иначе устройство навсегда остаётся
 * с частично заполненной программой (случай проектора 10.11.2026 — программа
 * была у 315 каналов из 3147).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgImportTrackerTest {

    private lateinit var database: SmotrishkaDatabase
    private lateinit var tracker: EpgImportTracker

    private val url = "http://epg.one/epg2.xml.gz"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        tracker = EpgImportTracker(database.epgImportDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `начатый импорт виден как незавершённый`() = runTest {
        tracker.start(url, now = 1_000)

        assertThat(tracker.isUnfinished(url)).isTrue()
    }

    @Test
    fun `прогресс сохраняется по ходу разбора`() = runTest {
        tracker.start(url, now = 1_000)
        tracker.progress(source = url, parsedChannels = 300, addedProgrammes = 12_000, now = 2_000)

        val state = tracker.unfinished().single()
        assertThat(state.parsedChannels).isEqualTo(300)
        assertThat(state.addedProgrammes).isEqualTo(12_000)
        assertThat(state.running).isTrue()
    }

    @Test
    fun `оборванный импорт остаётся незавершённым`() = runTest {
        tracker.start(url, now = 1_000)
        tracker.progress(source = url, parsedChannels = 300, addedProgrammes = 12_000, now = 2_000)

        tracker.interrupted(url, now = 3_000)

        assertThat(tracker.isUnfinished(url)).isTrue()
        assertThat(tracker.unfinished().single().running).isFalse()
    }

    @Test
    fun `завершённый импорт снимается с учёта`() = runTest {
        tracker.start(url, now = 1_000)
        tracker.progress(source = url, parsedChannels = 3147, addedProgrammes = 250_000, now = 2_000)

        tracker.finish(url)

        assertThat(tracker.isUnfinished(url)).isFalse()
        assertThat(tracker.unfinished()).isEmpty()
    }
}
