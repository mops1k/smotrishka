package com.mops1k.smotrishka.tv.core.vod.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mops1k.smotrishka.tv.core.data.SecretStore
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalKey
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalUrl
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Подключение медиатеки поверх DataStore и шифрованного хранилища секретов.
 *
 * Адрес лежит открыто, ключ — только шифротекстом через [SecretStore] (AES-GCM, ключ
 * в Android Keystore). Восстановленное состояние различает три случая: не настроено, настроено
 * и проверено, настроено но не проверено — этого хватает, чтобы пункт меню показывался только
 * после успешной проверки порталом.
 *
 * @param dataStore общие настройки приложения;
 * @param secretStore шифрованное хранилище ключа.
 */
class VodConfigStore(
    private val dataStore: DataStore<Preferences>,
    private val secretStore: SecretStore,
) : VodConfigPort {

    override val status: Flow<VodPortalStatus> = dataStore.data.map { preferences ->
        readStatus(preferences)
    }

    override suspend fun saveConnected(config: VodPortalConfig) {
        secretStore.put(SECRET_KEY, config.key.value)
        dataStore.edit { preferences ->
            preferences[KEY_URL] = config.url.value
            preferences[KEY_VERIFIED] = true
        }
    }

    override suspend fun saveFailed(config: VodPortalConfig, failure: VodFailure) {
        // Ключ сохраняем: пользователю не нужно вводить его заново, чтобы повторить проверку.
        secretStore.put(SECRET_KEY, config.key.value)
        dataStore.edit { preferences ->
            preferences[KEY_URL] = config.url.value
            preferences[KEY_VERIFIED] = false
        }
    }

    override suspend fun clear() {
        secretStore.remove(SECRET_KEY)
        dataStore.edit { preferences ->
            preferences.remove(KEY_URL)
            preferences.remove(KEY_VERIFIED)
        }
    }

    /** Собирает состояние из настроек и секрета. */
    private suspend fun readStatus(preferences: Preferences): VodPortalStatus {
        val rawUrl = preferences[KEY_URL] ?: return VodPortalStatus.NotConfigured
        val url = VodPortalUrl.from(rawUrl) ?: return VodPortalStatus.NotConfigured
        val storedKey = secretStore.get(SECRET_KEY) ?: return VodPortalStatus.NotConfigured
        val config = VodPortalConfig(url = url, key = VodPortalKey(storedKey))
        val verified = preferences[KEY_VERIFIED] ?: false
        return if (verified) {
            VodPortalStatus.Connected(config)
        } else {
            // Причина прошлой неудачи не хранится: свежую даст следующая проверка,
            // а показывать вчерашнюю ошибку на старте было бы неверно.
            VodPortalStatus.Failed(config, VodFailure.PortalRejected(null))
        }
    }

    private companion object {
        val KEY_URL = stringPreferencesKey("vod_portal_url")
        val KEY_VERIFIED = booleanPreferencesKey("vod_portal_verified")
        const val SECRET_KEY = "vod-portal-key"
    }
}
