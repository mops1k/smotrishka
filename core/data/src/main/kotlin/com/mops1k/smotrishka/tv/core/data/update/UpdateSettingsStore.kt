package com.mops1k.smotrishka.tv.core.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Хранение настроек обновления: пререлизы, «только Wi-Fi», кэш проверки и скрытая версия.
 *
 * @param dataStore общее хранилище настроек приложения.
 */
class UpdateSettingsStore(private val dataStore: DataStore<Preferences>) {

    /** Текущие настройки обновления. */
    val settings: Flow<UpdateSettings> = dataStore.data.map { preferences ->
        UpdateSettings(
            includePrereleases = preferences[PRERELEASES] ?: false,
            wifiOnly = preferences[WIFI_ONLY] ?: true,
            lastCheckedAtMillis = preferences[LAST_CHECKED]?.toLongOrNull(),
            dismissedVersion = preferences[DISMISSED_VERSION]?.ifBlank { null },
            notifiedVersion = preferences[NOTIFIED_VERSION]?.ifBlank { null },
        )
    }

    /**
     * Включает или выключает пререлизы.
     *
     * @param value учитывать пререлизы.
     */
    suspend fun setIncludePrereleases(value: Boolean) {
        dataStore.edit { preferences -> preferences[PRERELEASES] = value }
    }

    /**
     * Включает или выключает ограничение «только Wi-Fi».
     *
     * @param value проверять и скачивать автоматически только по Wi-Fi.
     */
    suspend fun setWifiOnly(value: Boolean) {
        dataStore.edit { preferences -> preferences[WIFI_ONLY] = value }
    }

    /**
     * Запоминает время последней проверки.
     *
     * @param atMillis момент проверки в миллисекундах эпохи.
     */
    suspend fun markChecked(atMillis: Long) {
        dataStore.edit { preferences -> preferences[LAST_CHECKED] = atMillis.toString() }
    }

    /**
     * Запоминает версию, про которую больше не сообщаем.
     *
     * @param version версия без префикса `v`.
     */
    suspend fun dismiss(version: String) {
        dataStore.edit { preferences -> preferences[DISMISSED_VERSION] = version }
    }

    /** Снимает скрытие версии: «Проверить снова» снова показывает обновление. */
    suspend fun clearDismissed() {
        dataStore.edit { preferences ->
            preferences.remove(DISMISSED_VERSION)
            preferences.remove(NOTIFIED_VERSION)
        }
    }

    /**
     * Запоминает версию, про которую уже показали тост при запуске.
     *
     * @param version версия без префикса `v`.
     */
    suspend fun markNotified(version: String) {
        dataStore.edit { preferences -> preferences[NOTIFIED_VERSION] = version }
    }

    private companion object {
        val PRERELEASES = booleanPreferencesKey("update:prereleases")
        val WIFI_ONLY = booleanPreferencesKey("update:wifiOnly")
        val LAST_CHECKED = stringPreferencesKey("update:lastChecked")
        val DISMISSED_VERSION = stringPreferencesKey("update:dismissedVersion")
        val NOTIFIED_VERSION = stringPreferencesKey("update:notifiedVersion")
    }
}
