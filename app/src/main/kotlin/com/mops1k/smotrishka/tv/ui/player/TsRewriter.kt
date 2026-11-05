package com.mops1k.smotrishka.tv.ui.player

/**
 * Сдвигает метки времени в сегменте MPEG-TS.
 *
 * Сегменты провайдера приходят с рваным временем: между ними скачок, из-за которого плеер
 * перезапускает декодер (`Unexpected audio track timestamp discontinuity`) и встаёт на паузу.
 * Переписываем PTS/DTS в каждом PES-заголовке на один и тот же сдвиг — таймлайн становится
 * непрерывным, а содержимое не меняется.
 */
object TsRewriter {

    /** Размер пакета MPEG-TS. */
    const val PACKET_SIZE = 188

    /**
     * Применяет сдвиг ко всем PES-заголовкам сегмента.
     *
     * @param segment байты сегмента;
     * @param offset сдвиг из [TsTimeline.offset];
     * @return новый массив с исправленным временем.
     */
    fun shift(segment: ByteArray, offset: Long): ByteArray {
        if (offset == 0L) return segment
        val result = segment.copyOf()
        var position = 0
        while (position + PACKET_SIZE <= result.size) {
            shiftPacket(result, position, offset)
            position += PACKET_SIZE
        }
        return result
    }

    /**
     * Первая метка PTS в сегменте.
     *
     * @param segment байты сегмента;
     * @return метка в 90 кГц либо `null`, если PES не найден.
     */
    fun firstPts(segment: ByteArray): Long? {
        var position = 0
        while (position + PACKET_SIZE <= segment.size) {
            if (segment[position].toInt() and 0xFF == 0x47) {
                val flags = segment[position + 1].toInt() and 0xFF
                val payloadStart = flags and 0x40 != 0
                val payload = flags and 0x10 != 0
                if (payloadStart && payload) {
                    var payloadOffset = position + 4
                    if (flags and 0x20 != 0) {
                        payloadOffset += 1 + (segment[position + 4].toInt() and 0xFF)
                    }
                    if (payloadOffset + 14 <= position + PACKET_SIZE) {
                        val header = segment.copyOfRange(payloadOffset, payloadOffset + 14)
                        PesTimestamp.read(header)?.let { return it }
                    }
                }
            }
            position += PACKET_SIZE
        }
        return null
    }

    private fun shiftPacket(bytes: ByteArray, position: Int, offset: Long) {
        // Байт 0 — sync 0x47; без него дальше идти нельзя.
        if (bytes[position].toInt() and 0xFF != 0x47) return

        val flags = bytes[position + 1].toInt() and 0xFF
        val payloadStart = flags and 0x40 != 0
        val adaptation = flags and 0x20 != 0
        val payload = flags and 0x10 != 0
        if (!payloadStart || !payload) return

        var payloadOffset = position + 4
        if (adaptation) {
            payloadOffset += 1 + (bytes[position + 4].toInt() and 0xFF)
        }
        if (payloadOffset + 14 > position + PACKET_SIZE) return

        val pts = PesTimestamp.read(bytes.copyOfRange(payloadOffset, payloadOffset + 14)) ?: return
        val shifted = TsTimeline.apply(pts, offset)
        val header = bytes.copyOfRange(payloadOffset, payloadOffset + 14)
        PesTimestamp.write(header, shifted)
        header.copyInto(bytes, payloadOffset)
    }
}
