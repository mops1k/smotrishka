package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранит избранные каналы.
 *
 * Избранное — обычный набор идентификаторов: он показывается отдельной категорией в самом верху
 * списка и переживает перезапуск. Набор маленький, поэтому лежит в DataStore.
 */
@Singleton
class FavoritesStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /** Идентификаторы избранных каналов. */
    val channels: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[KEY].orEmpty()
    }

    /**
     * Переключает канал в избранном.
     *
     * @param channelId ключ канала.
     * @return новое состояние: `true` — канал в избранном.
     */
    suspend fun toggle(channelId: String): Boolean {
        var added = false
        dataStore.edit { preferences ->
            val current = preferences[KEY].orEmpty()
            added = channelId !in current
            preferences[KEY] = if (added) current + channelId else current - channelId
        }
        return added
    }

    private companion object {
        val KEY = stringSetPreferencesKey("favorites")
    }
}
