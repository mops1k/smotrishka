package com.mops1k.smotrishka.tv.core.playlist

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Определяет кодировку текста плейлиста и декодирует байты.
 *
 * Провайдеры отдают M3U в UTF-8 (иногда с BOM), в UTF-16 с BOM и в cp1251.
 * Правило: явный BOM решает всё; иначе текст считается UTF-8, а если он не является
 * корректным UTF-8 — значит это однобайтовая кодировка, и берётся windows-1251
 * (её используют русскоязычные провайдеры).
 */
class M3uTextDecoder {

    /**
     * Декодирует байты плейлиста.
     *
     * @param bytes содержимое файла или ответа сети.
     */
    fun decode(bytes: ByteArray): String = when {
        bytes.isEmpty() -> ""
        bytes.startsWith(BOM_UTF8) -> String(bytes, BOM_UTF8.size, bytes.size - BOM_UTF8.size, UTF_8)
        bytes.startsWith(BOM_UTF16_LE) -> String(bytes, 2, bytes.size - 2, UTF_16_LE)
        bytes.startsWith(BOM_UTF16_BE) -> String(bytes, 2, bytes.size - 2, UTF_16_BE)
        isValidUtf8(bytes) -> String(bytes, UTF_8)
        else -> String(bytes, WINDOWS_1251)
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean =
        try {
            UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            true
        } catch (e: CharacterCodingException) {
            false
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        val UTF_8: Charset = Charsets.UTF_8
        val UTF_16_LE: Charset = Charsets.UTF_16LE
        val UTF_16_BE: Charset = Charsets.UTF_16BE
        val WINDOWS_1251: Charset = Charset.forName("windows-1251")

        val BOM_UTF8 = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val BOM_UTF16_LE = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val BOM_UTF16_BE = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
    }
}
