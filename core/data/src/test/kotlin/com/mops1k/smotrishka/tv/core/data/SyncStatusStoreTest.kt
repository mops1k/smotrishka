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

/**
 * TDD-спецификация последнего результата обновления для экрана настроек.
 */
class SyncStatusStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: SyncStatusStore

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "status.preferences_pb")
        }
        store = SyncStatusStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `успешное обновление описывается числами`() = runTest {
        store.save(UpdateResult.Success(channels = 12, categories = 3, programmes = 40), atMillis = 1_760_000_000_000L)

        val status = store.last()

        assertThat(status?.message).contains("12")
        assertThat(status?.message).contains("40")
        assertThat(status?.atMillis).isEqualTo(1_760_000_000_000L)
    }

    @Test
    fun `частичный результат содержит причину`() = runTest {
        store.save(
            UpdateResult.Partial(reason = "Программа передач не загружена: 500", channels = 5, categories = 2),
            atMillis = 1L,
        )

        assertThat(store.last()?.message).contains("500")
    }

    @Test
    fun `до первого обновления результата нет`() = runTest {
        assertThat(store.last()).isNull()
    }
}
