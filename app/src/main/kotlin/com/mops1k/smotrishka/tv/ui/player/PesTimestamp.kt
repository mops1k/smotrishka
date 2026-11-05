package com.mops1k.smotrishka.tv.ui.player

/**
 * Чтение и запись метки времени из заголовка PES.
 *
 * Сегмент MPEG-TS состоит из PES-пакетов; время лежит в их заголовке в 33-битном поле. Чтобы
 * сдвинуть таймлайн (см. [TsTimeline]), нужно прочитать его и записать обратно — здесь только
 * работа с полем, без разбора самих TS-пакетов.
 */
object PesTimestamp {

    /** Смещение метки PTS в PES-заголовке. */
    private const val PTS_OFFSET = 9

    /** Длина заголовка PES до метки. */
    private const val HEADER_MIN_SIZE = 14

    /**
     * Читает PTS из PES-заголовка.
     *
     * @param bytes начало PES-пакета;
     * @return метка времени либо `null`, если её нет или данные не по формату.
     */
    fun read(bytes: ByteArray): Long? {
        if (bytes.size < HEADER_MIN_SIZE) return null
        // 00 00 01 — префикс начала пакета.
        if (bytes[0].toInt() != 0 || bytes[1].toInt() != 0 || bytes[2].toInt() != 1) return null
        // Флаги PES: байт 6 — наличие PTS, байт 7 (старшие два бита) — «только PTS» или PTS+DTS.
        if (bytes[6].toInt() and 0x80 == 0) return null
        val ptsFlags = (bytes[7].toInt() and 0xC0) ushr 6
        if (ptsFlags != 0b10 && ptsFlags != 0b11) return null
        val marker = (bytes[PTS_OFFSET].toInt() and 0xF0) ushr 4
        if (marker != 0b0010 && marker != 0b0011) return null

        val b1 = bytes[PTS_OFFSET].toInt() and 0xFF
        val b2 = bytes[10].toInt() and 0xFF
        val b3 = bytes[11].toInt() and 0xFF
        val b4 = bytes[12].toInt() and 0xFF
        val b5 = bytes[13].toInt() and 0xFF
        return (((b1 and 0x0E) ushr 1).toLong() shl 30) or
            (b2.toLong() shl 22) or
            (((b3 and 0xFE) ushr 1).toLong() shl 15) or
            (b4.toLong() shl 7) or
            ((b5 and 0xFE) ushr 1).toLong()
    }

    /**
     * Записывает PTS обратно в тот же заголовок, сохраняя тип префикса.
     *
     * @param bytes изменяемый PES-заголовок;
     * @param pts новая метка времени.
     */
    fun write(bytes: ByteArray, pts: Long) {
        val value = pts % TsTimeline.WRAP
        val prefix = (bytes[PTS_OFFSET].toInt() and 0xF0) or 0x20
        bytes[PTS_OFFSET] = (
            prefix or (((value ushr 29).toInt()) and 0x0E) or 0x01
            ).toByte()
        bytes[10] = ((value ushr 22) and 0xFF).toByte()
        bytes[11] = ((((value ushr 14) and 0xFE).toInt()) or 0x01).toByte()
        bytes[12] = ((value ushr 7) and 0xFF).toByte()
        bytes[13] = ((((value shl 1) and 0xFE).toInt()) or 0x01).toByte()
    }
}
