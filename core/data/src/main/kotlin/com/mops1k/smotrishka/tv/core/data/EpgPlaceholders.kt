package com.mops1k.smotrishka.tv.core.data

/**
 * Строки-заглушки гида: вместо передачи источник отдаёт блок «программы нет».
 *
 * У epg.one такие строки занимают по 12 часов, у iptvx.one — блоки между фильмами.
 * Заглушка не считается настоящей передачей ни при сверке сеток, ни при выборе гида.
 */
object EpgPlaceholders {

    /** Названия, которыми источники помечают часы без программы. */
    private val TITLES = listOf(
        "програма відсутня",
        "программа отсутствует",
        "нет программы",
        "нет данных",
    )

    /**
     * Заглушка ли это.
     *
     * @param title название передачи из гида.
     */
    fun isPlaceholder(title: String): Boolean {
        val normalized = title.trim().lowercase()
        return TITLES.any { normalized.startsWith(it) }
    }
}
