package com.mops1k.smotrishka.tv.core.data

import androidx.room.Entity
import androidx.room.Fts4

/**
 * Полнотекстовый индекс поиска: одна строка — один результат.
 *
 * Таблица FTS4 собирается из каталога и программы передач и пересобирается, когда каталог или
 * гид обновились: так строка поиска ищет одним запросом по каналам, категориям и передачам.
 *
 * @param kind вид результата: `channel`, `category` или `programme`;
 * @param refId идентификатор канала или категории (у передачи — канал передачи);
 * @param title что показываем крупно: название канала, категории или передачи;
 * @param subtitle пояснение мелким: категория канала или время передачи с названием канала.
 */
@Fts4
@Entity(tableName = "search_index")
data class SearchIndexEntity(
    val kind: String,
    val refId: String,
    val title: String,
    val subtitle: String,
)
