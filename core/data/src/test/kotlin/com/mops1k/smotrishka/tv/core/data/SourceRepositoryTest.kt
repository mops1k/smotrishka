package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

/**
 * TDD-спецификация хранения активного источника: в базе только описание,
 * код и пароль — в зашифрованном хранилище.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SourceRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: SmotrishkaDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var secretStore: SecretStore
    private lateinit var repository: SourceRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()

        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tempFolder.root, "secrets.preferences_pb")
        }
        secretStore = SecretStore(dataStore, AesGcmSecretCipher(key))
        repository = SourceRepository(CatalogStore(database), secretStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun `сохраняет и читает m3u источник`() = runTest {
        repository.save(PlaylistSource.M3uUrl(url = "http://example.com/list.m3u", name = "Домашний"), "Домашний")

        val active = repository.active() as PlaylistSource.M3uUrl

        assertThat(active.url).isEqualTo("http://example.com/list.m3u")
    }

    @Test
    fun `код провайдера хранится в шифрованном виде и читается`() = runTest {
        repository.save(PlaylistSource.IedemCode(code = "987654"), "По коду")

        val active = repository.active() as PlaylistSource.IedemCode

        assertThat(active.code).isEqualTo("987654")
    }

    @Test
    fun `пароль xtream хранится отдельно от описания`() = runTest {
        repository.save(
            PlaylistSource.Xtream(server = "http://example.com:8080", username = "user", password = "pass"),
            "Xtream",
        )

        val active = repository.active() as PlaylistSource.Xtream

        assertThat(active.username).isEqualTo("user")
        assertThat(active.password).isEqualTo("pass")
    }

    @Test
    fun `без сохранённого источника активного нет`() = runTest {
        assertThat(repository.active()).isNull()
    }

    @Test
    fun `файловый источник сохраняется с uri и именем и переживает перезапуск`() = runTest {
        repository.save(
            PlaylistSource.File(
                uri = "content://com.android.providers.downloads.documents/document/42",
                name = "smotrishka.m3u",
            ),
            "smotrishka.m3u",
        )

        // Новый экземпляр репозитория на той же базе — как после перезапуска приложения.
        val restarted = SourceRepository(CatalogStore(database), secretStore)
        val active = restarted.active() as PlaylistSource.File

        assertThat(active.uri).isEqualTo("content://com.android.providers.downloads.documents/document/42")
        assertThat(active.name).isEqualTo("smotrishka.m3u")
    }
}
