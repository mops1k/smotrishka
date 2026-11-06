package com.mops1k.smotrishka.tv.core.data.watchnext

/**
 * Ссылка, которая открывает канал сразу в плеере.
 *
 * Схема своя (`smotrishka://watch?channel=…`), потому что карточка ленты в лаунчере
 * запускает приложение по URI: обработчик объявлен в манифесте, а разбор ссылки живёт здесь,
 * где его можно проверить юнит-тестом.
 *
 * Идентификатор канала кодируется: у каналов без `tvg-id` он содержит URL, и «?», «&», «/»
 * в нём сломали бы разбор ссылки.
 */
object WatchNextLink {

    /** Схема ссылок приложения. */
    const val SCHEME: String = "smotrishka"

    /** Хост ссылок, открывающих канал в плеере. */
    const val HOST: String = "watch"

    /** Имя параметра с идентификатором канала. */
    const val CHANNEL_PARAM: String = "channel"

    /**
     * Собирает ссылку на канал.
     *
     * @param channelId идентификатор канала каталога.
     */
    fun of(channelId: String): String =
        "$SCHEME://$HOST?$CHANNEL_PARAM=${encode(channelId)}"

    /**
     * Достаёт идентификатор канала из ссылки.
     *
     * @param raw ссылка из [of] или из Intent'а лаунчера.
     * @return идентификатор канала, `null` — ссылка не наша или испорчена.
     */
    fun channelId(raw: String?): String? {
        val link = raw?.trim().orEmpty()
        val schemeEnd = link.indexOf("://")
        if (schemeEnd <= 0) return null
        if (!link.substring(0, schemeEnd).equals(SCHEME, ignoreCase = true)) return null
        val rest = link.substring(schemeEnd + 3)
        val hostEnd = rest.indexOfFirst { it == '?' || it == '#' || it == '/' }
        val host = if (hostEnd >= 0) rest.substring(0, hostEnd) else rest
        if (!host.equals(HOST, ignoreCase = true)) return null
        if (hostEnd < 0) return null
        val query = rest.substring(hostEnd + 1)
        val pairs = query.split('&')
        for (pair in pairs) {
            val separator = pair.indexOf('=')
            if (separator <= 0) continue
            val name = pair.substring(0, separator)
            if (name != CHANNEL_PARAM) continue
            val value = pair.substring(separator + 1).substringBefore('#')
            if (value.isEmpty()) return null
            return decode(value)?.takeIf { it.isNotBlank() }
        }
        return null
    }

    /** Кодирует значение как компонент URI (RFC 3986: незакрытые только `A-Z a-z 0-9 - . _ ~`). */
    private fun encode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (code < 0x80 && (char.isLetterOrDigit() || char == '-' || char == '.' || char == '_' || char == '~')) {
                append(char)
            } else {
                append('%')
                append(HEX[code shr 4])
                append(HEX[code and 0x0F])
            }
        }
    }

    /** Обратное к [encode]: `null` — в ссылке испорченное кодирование. */
    private fun decode(value: String): String? {
        val bytes = java.io.ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%') {
                if (index + 2 >= value.length) return null
                val hex = value.substring(index + 1, index + 3)
                val code = hex.toIntOrNull(16) ?: return null
                bytes.write(code)
                index += 3
            } else {
                // Незакрытые символы в ссылке — только ASCII: остальное должно быть закодировано.
                if (char.code >= 0x80) return null
                bytes.write(char.code)
                index++
            }
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}
