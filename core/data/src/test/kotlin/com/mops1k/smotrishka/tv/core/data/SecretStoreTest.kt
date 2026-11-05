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
import javax.crypto.KeyGenerator

/**
 * TDD-спецификация хранилища секретов: значения лежат на диске только в шифрованном виде.
 */
class SecretStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var secretFile: File
    private lateinit var store: SecretStore

    @Before
    fun setUp() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        secretFile = File(tempFolder.root, "secrets.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(scope = scope) { secretFile }
        store = SecretStore(dataStore, AesGcmSecretCipher(key))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `сохраняет и читает секрет`() = runTest {
        store.put("xtream", "СуперСекрет")

        assertThat(store.get("xtream")).isEqualTo("СуперСекрет")
    }

    @Test
    fun `отсутствующий секрет даёт null`() = runTest {
        assertThat(store.get("нет-такого")).isNull()
    }

    @Test
    fun `перезапись заменяет значение`() = runTest {
        store.put("iedem", "старый")
        store.put("iedem", "новый")

        assertThat(store.get("iedem")).isEqualTo("новый")
    }

    @Test
    fun `удаление убирает секрет`() = runTest {
        store.put("iedem", "код")
        store.remove("iedem")

        assertThat(store.get("iedem")).isNull()
    }

    @Test
    fun `в файле на диске нет открытого секрета`() = runTest {
        store.put("iedem", "ОткрытыйКод123")

        val raw = String(secretFile.readBytes(), Charsets.ISO_8859_1)

        assertThat(raw).doesNotContain("ОткрытыйКод123")
    }
}
