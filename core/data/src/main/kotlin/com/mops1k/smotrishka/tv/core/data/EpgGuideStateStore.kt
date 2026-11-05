package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Память о загрузках гидов.
 *
 * Успешно скачанный гид действует 12 часов: гиды весят десятки мегабайт, а обновление
 * идёт каждые 6 часов, и без этой памяти один и тот же гид качался бы дважды за сутки.
 *
 * @param dataStore хранилище настроек.
 */
class EpgGuideStateStore(private val dataStore: DataStore<Preferences>) {

    /**
     * Когда гид последний раз успешно загружался.
     *
     * @param guideName имя гида;
     * @return момент загрузки либо `null`, если гид ещё не загружался.
     */
    suspend fun lastFetched(guideName: String): Instant? {
        val millis = dataStore.data.first()[key(guideName)]?.toLongOrNull() ?: return null
        return Instant.ofEpochMilli(millis)
    }

    /**
     * Запоминает успешную загрузку гида.
     *
     * @param guideName имя гида;
     * @param at момент загрузки.
     */
    suspend fun markFetched(guideName: String, at: Instant) {
        dataStore.edit { preferences ->
            preferences[key(guideName)] = at.toEpochMilli().toString()
        }
    }

    private fun key(guideName: String) =
        stringPreferencesKey("epg:guide:$guideName:fetchedAt")
}
