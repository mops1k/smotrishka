package com.mops1k.smotrishka.tv.ui.player

/**
 * Порядок сегментов эфира.
 *
 * Провайдер подписывает каждый сегмент по-новому (`md5`), поэтому «тот же» сегмент по ссылке
 * не отличить. Отличаем по метке времени в имени файла (`<epochMillis>.ts`) — она же задаёт
 * порядок и не даёт проиграть один кусок дважды.
 */
object LiveSegments {

    /**
     * Достаёт метку времени сегмента.
     *
     * @param url ссылка на сегмент;
     * @return метка в миллисекундах либо `null`, если имя не по шаблону провайдера.
     */
    fun timestampOf(url: String): Long? {
        val name = url.substringBefore('?').substringAfterLast('/')
        val digits = name.substringBefore('.')
        if (digits.length != 13 || !digits.all(Char::isDigit)) return null
        return digits.toLongOrNull()
    }

    /**
     * Убирает повторы и сохраняет порядок.
     *
     * @param urls сегменты в порядке плейлиста;
     * @return сегменты без повторных меток времени.
     */
    fun unique(urls: List<String>): List<String> {
        val seen = mutableSetOf<Long>()
        return urls.filter { url ->
            val timestamp = timestampOf(url)
            timestamp == null || seen.add(timestamp)
        }
    }

    /**
     * Номера пропущенных меток между сегментами.
     *
     * @param previousMillis метка предыдущего скачанного сегмента;
     * @param nextMillis метка следующего;
     * @param segmentMillis длительность сегмента.
     * @return количество пропущенных сегментов.
     */
    fun gapsBetween(previousMillis: Long, nextMillis: Long, segmentMillis: Long = 10_000L): Int {
        if (segmentMillis <= 0) return 0
        val diff = nextMillis - previousMillis
        if (diff <= segmentMillis) return 0
        return ((diff / segmentMillis) - 1).toInt()
    }
}
