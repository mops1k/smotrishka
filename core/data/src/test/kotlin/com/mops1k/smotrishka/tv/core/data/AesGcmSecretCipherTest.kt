package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * TDD-спецификация шифрования секретов источников (AES-GCM).
 *
 * Требования: обратимость, случайный IV у каждого шифрования, отсутствие открытого
 * текста в шифротексте и устойчивость к испорченным данным (пользователь получит null,
 * а не падение).
 */
class AesGcmSecretCipherTest {

    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val cipher = AesGcmSecretCipher(key)

    @Test
    fun `шифрует и расшифровывает обратно`() {
        val secret = "пароль-от-провайдера-42"

        val encrypted = cipher.encrypt(secret)

        assertThat(cipher.decrypt(encrypted)).isEqualTo(secret)
    }

    @Test
    fun `шифротекст не содержит открытый текст`() {
        val secret = "SuperSecretPassword"

        val encrypted = cipher.encrypt(secret)

        assertThat(encrypted).doesNotContain(secret)
    }

    @Test
    fun `два шифрования одного секрета различаются`() {
        val secret = "SuperSecretPassword"

        assertThat(cipher.encrypt(secret)).isNotEqualTo(cipher.encrypt(secret))
    }

    @Test
    fun `испорченные данные не расшифровываются`() {
        val encrypted = cipher.encrypt("SuperSecretPassword")
        val broken = encrypted.dropLast(4)

        assertThat(cipher.decrypt(broken)).isNull()
    }

    @Test
    fun `мусор вместо шифротекста даёт null`() {
        assertThat(cipher.decrypt("не-base64-и-не-шифротекст")).isNull()
    }
}
