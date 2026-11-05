package com.mops1k.smotrishka.tv.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Ключ шифрования секретов в Android Keystore.
 *
 * Ключ создаётся один раз при первом запуске и дальше только читается: материал ключа
 * не покидает Keystore устройства.
 *
 * IV генерируется приложением и хранится рядом с шифротекстом, поэтому у ключа явно снято
 * требование Keystore о «случайном IV от системы» (`setRandomizedEncryptionRequired(false)`).
 * Без этого Android отвечает `Caller-provided IV not permitted` и шифрование падает.
 *
 * @param alias имя ключа в хранилище.
 */
class KeystoreSecretKeyProvider(
    private val alias: String = DEFAULT_ALIAS,
) {

    /** Возвращает существующий ключ или создаёт новый. */
    fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) {
            return existing.secretKey
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(false)
                .build(),
        )

        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"

        /** Версия в имени: ключи, созданные без разрешения своего IV, уже непригодны. */
        const val DEFAULT_ALIAS = "smotrishka-secrets-v2"
        const val KEY_SIZE_BITS = 256
    }
}
