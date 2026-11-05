package com.mops1k.smotrishka.tv.core.data

/**
 * Результат поиска.
 *
 * @param kind вид результата;
 * @param refId идентификатор канала или категории (у передачи — канал передачи);
 * @param title что показываем крупно;
 * @param subtitle пояснение мелким.
 */
data class SearchHit(
    val kind: SearchHitKind,
    val refId: String,
    val title: String,
    val subtitle: String,
)
