package com.mops1k.smotrishka.tv.core.playlist

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Разбор времени XMLTV.
 *
 * Формат — `yyyyMMddHHmmss` с необязательными секундами и смещением:
 * `20261102120000 +0300`, `20261102120000`, `202611021200`. Время без смещения
 * считается UTC: провайдеры, которые так отдают, подразумевают именно UTC.
 */
internal object XmltvTime {

    private val PATTERN = Regex("""(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-]\d{4})?""")

    /**
     * Разбирает время.
     *
     * @param raw значение атрибута `start`/`stop`.
     * @return момент времени либо `null`, если формат незнаком.
     */
    fun parse(raw: String?): Instant? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null

        val match = PATTERN.matchEntire(value) ?: return null
        val groups = match.groupValues

        return try {
            LocalDateTime.of(
                groups[1].toInt(),
                groups[2].toInt(),
                groups[3].toInt(),
                groups[4].toInt(),
                groups[5].toInt(),
                groups[6].ifEmpty { "0" }.toInt(),
            ).toInstant(offsetOf(groups[7]))
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun offsetOf(raw: String): ZoneOffset {
        if (raw.isEmpty()) return ZoneOffset.UTC
        return ZoneOffset.of("${raw.substring(0, 3)}:${raw.substring(3, 5)}")
    }
}
