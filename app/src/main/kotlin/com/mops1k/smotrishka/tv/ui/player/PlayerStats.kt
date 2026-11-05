package com.mops1k.smotrishka.tv.ui.player

/**
 * Текст индикатора качества для OSD: разрешение, битрейт и запас буфера.
 *
 * Нужен, чтобы отличить проблему сети от проблемы канала: если битрейт низкий и буфер пуст,
 * виноват интернет; если всё в норме, а картинки нет — дело в канале.
 */
object PlayerStats {

    /**
     * Собирает строку индикатора.
     *
     * @param height высота кадра в пикселях, `null` — ещё неизвестна;
     * @param bitrate битрейт в бит/с, `null` — неизвестен;
     * @param bufferMs запас буфера в миллисекундах.
     */
    fun describe(height: Int?, bitrate: Int?, bufferMs: Long): String {
        val parts = mutableListOf<String>()

        if (height != null && height > 0) {
            parts += when {
                height >= 1080 -> "1080p"
                height >= 720 -> "720p"
                else -> "${height}p"
            }
        }

        if (bitrate != null && bitrate > 0) {
            parts += "%.1f Мбит/с".format(bitrate / 1_000_000f)
        }

        val seconds = (bufferMs / 1000).coerceAtLeast(0)
        parts += "буфер ${seconds} с"

        return parts.joinToString(" · ")
    }
}
