package com.mops1k.smotrishka.tv.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * Зашифрованное хранилище секретов источников (пароли Xtream, коды провайдера, URL с токеном).
 *
 * В DataStore попадают только шифротексты; ключ живёт в Android Keystore и наружу не выходит.
 *
 * @param dataStore хранилище пар ключ-значение;
 * @param cipher шифрование значений.
 */
class SecretStore(
    private val dataStore: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {

    /**
     * Сохраняет секрет под ключом источника.
     *
     * @param key ключ записи (например, идентификатор источника);
     * @param value секрет в открытом виде.
     */
    suspend fun put(key: String, value: String) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = cipher.encrypt(value)
        }
    }

    /**
     * Читает секрет.
     *
     * @param key ключ записи;
     * @return секрет либо `null`, если записи нет или она повреждена.
     */
    suspend fun get(key: String): String? {
        val stored = dataStore.data.first()[stringPreferencesKey(key)] ?: return null
        return cipher.decrypt(stored)
    }

    /**
     * Удаляет секрет.
     *
     * @param key ключ записи.
     */
    suspend fun remove(key: String) {
        dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))
        }
    }
}
