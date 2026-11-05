package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Последний результат обновления данных — чтобы экран настроек показал,
 * что произошло при фоновой синхронизации.
 *
 * @param dataStore хранилище результатов.
 */
class SyncStatusStore(private val dataStore: DataStore<Preferences>) {

    /**
     * Запоминает результат обновления.
     *
     * @param result итог обновления;
     * @param atMillis когда это произошло.
     */
    suspend fun save(result: UpdateResult, atMillis: Long) {
        val text = when (result) {
            is UpdateResult.Success ->
                "Обновлено: каналов ${result.channels}, передач ${result.programmes}"

            is UpdateResult.Partial ->
                "Каталог обновлён (${result.channels} каналов). ${result.reason}"

            is UpdateResult.Failure ->
                "Обновление не удалось: ${result.reason}"
        }

        dataStore.edit { preferences ->
            preferences[MESSAGE] = text
            preferences[TIME] = atMillis.toString()
        }
    }

    /** Последний результат обновления либо `null`, если обновлений ещё не было. */
    suspend fun last(): SyncStatus? {
        val preferences = dataStore.data.first()
        val message = preferences[MESSAGE] ?: return null
        val time = preferences[TIME]?.toLongOrNull() ?: return null

        return SyncStatus(message = message, atMillis = time)
    }

    private companion object {
        val MESSAGE = stringPreferencesKey("sync:message")
        val TIME = stringPreferencesKey("sync:time")
    }
}

/**
 * Последний результат обновления.
 *
 * @param message текст для экрана настроек;
 * @param atMillis время обновления.
 */
data class SyncStatus(
    val message: String,
    val atMillis: Long,
)
