package com.mops1k.smotrishka.tv.core.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * TDD-спецификация настроек обновления: пререлизы выключены, «только Wi-Fi» включено,
 * скрытая версия снимается повторной проверкой.
 */
class UpdateSettingsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var store: UpdateSettingsStore

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "update.preferences_pb")
        }
        store = UpdateSettingsStore(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `по умолчанию пререлизы выключены, а Wi-Fi ограничение включено`() = runTest {
        val settings = store.settings.first()

        assertThat(settings.includePrereleases).isFalse()
        assertThat(settings.wifiOnly).isTrue()
        assertThat(settings.lastCheckedAtMillis).isNull()
        assertThat(settings.dismissedVersion).isNull()
    }

    @Test
    fun `переключатели сохраняются`() = runTest {
        store.setIncludePrereleases(true)
        store.setWifiOnly(false)

        val settings = store.settings.first()

        assertThat(settings.includePrereleases).isTrue()
        assertThat(settings.wifiOnly).isFalse()
    }

    @Test
    fun `время проверки сохраняется`() = runTest {
        store.markChecked(atMillis = 1_760_000_000_000L)

        assertThat(store.settings.first().lastCheckedAtMillis).isEqualTo(1_760_000_000_000L)
    }

    @Test
    fun `скрытая версия снимается повторной проверкой`() = runTest {
        store.dismiss("0.2.0")
        store.markNotified("0.2.0")
        assertThat(store.settings.first().dismissedVersion).isEqualTo("0.2.0")

        store.clearDismissed()

        val settings = store.settings.first()
        assertThat(settings.dismissedVersion).isNull()
        assertThat(settings.notifiedVersion).isNull()
    }
}
