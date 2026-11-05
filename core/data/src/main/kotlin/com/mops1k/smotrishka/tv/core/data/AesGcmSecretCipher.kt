package com.mops1k.smotrishka.tv.core.data

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM шифрование секретов.
 *
 * Формат строки: `base64(IV || шифротекст || тег)`, IV случайный на каждое шифрование.
 * GCM выбран потому, что он не только шифрует, но и проверяет целостность: подменённый
 * шифротекст не расшифруется, а вернёт `null` (пользователь получит просьбу ввести данные заново).
 *
 * @param key ключ AES-256 из Android Keystore.
 */
class AesGcmSecretCipher(private val key: SecretKey) : SecretCipher {

    override fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        return encoder.encodeToString(iv + encrypted)
    }

    override fun decrypt(payload: String): String? =
        try {
            val bytes = decoder.decode(payload)
            if (bytes.size <= IV_SIZE) {
                null
            } else {
                val iv = bytes.copyOfRange(0, IV_SIZE)
                val encrypted = bytes.copyOfRange(IV_SIZE, bytes.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            }
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128

        val encoder: Base64.Encoder = Base64.getEncoder()
        val decoder: Base64.Decoder = Base64.getDecoder()
    }
}
