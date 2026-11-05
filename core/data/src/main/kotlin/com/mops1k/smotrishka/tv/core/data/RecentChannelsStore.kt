package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранит список недавно просмотренных каналов.
 *
 * Список короткий (десять каналов), поэтому лежит одной строкой через разделитель — отдельная
 * таблица в базе для этого не нужна.
 */
@Singleton
class RecentChannelsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Недавние каналы: свежие первыми. */
    val channels: Flow<List<String>> = dataStore.data.map { preferences ->
        preferences[KEY]
            ?.split(SEPARATOR)
            ?.filter(String::isNotBlank)
            .orEmpty()
    }

    /**
     * Запоминает канал как последний просмотренный.
     *
     * @param channelId ключ канала.
     */
    suspend fun remember(channelId: String) {
        dataStore.edit { preferences ->
            val current = preferences[KEY]
                ?.split(SEPARATOR)
                ?.filter(String::isNotBlank)
                .orEmpty()
            preferences[KEY] = RecentChannels.add(current, channelId).joinToString(SEPARATOR)
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("recent_channels")

        /** Разделитель: идентификаторы каналов могут содержать что угодно, кроме него. */
        const val SEPARATOR = "\u0000"
    }
}
