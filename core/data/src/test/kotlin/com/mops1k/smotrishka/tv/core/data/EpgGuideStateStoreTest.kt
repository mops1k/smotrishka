package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant

/**
 * TDD-спецификация памяти о загрузках публичных гидов.
 *
 * Нужна, чтобы не перекачивать один и тот же гид (24–47 МБ) при каждом обновлении
 * каждые 6 часов: успешная загрузка запоминается и действует 12 часов.
 */
class EpgGuideStateStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: EpgGuideStateStore

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "guides.preferences_pb")
        }
        store = EpgGuideStateStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `до первой загрузки времени нет`() = runTest {
        assertThat(store.lastFetched("epg.one")).isNull()
    }

    @Test
    fun `время успешной загрузки запоминается`() = runTest {
        val at = Instant.parse("2026-11-05T09:00:00Z")
        store.markFetched("epg.one", at)

        assertThat(store.lastFetched("epg.one")).isEqualTo(at)
    }

    @Test
    fun `гиды запоминаются независимо`() = runTest {
        store.markFetched("epg.one", Instant.parse("2026-11-05T09:00:00Z"))

        assertThat(store.lastFetched("iptvx.one")).isNull()
    }
}
