package com.mops1k.smotrishka.tv.core.data

/**
 * Вид результата поиска.
 */
enum class SearchHitKind {

    /** Канал: открывается плеер. */
    CHANNEL,

    /** Категория: переключает раздел. */
    CATEGORY,

    /** Передача: открывается канал, на котором она идёт. */
    PROGRAMME,
}
