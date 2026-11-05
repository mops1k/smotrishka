package com.mops1k.smotrishka.tv.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * Доступ к полнотекстовому индексу поиска.
 *
 * Запрос передаётся в FTS4 как есть, поэтому вызывающий код обязан экранировать служебные
 * символы и добавлять `*` для поиска по префиксу — это делает [SearchRepository].
 */
@Dao
interface SearchDao {

    /**
     * Ищет совпадения по индексу.
     *
     * @param match выражение FTS4 MATCH;
     */
    @Query("SELECT * FROM search_index WHERE search_index MATCH :match LIMIT 60")
    suspend fun search(match: String): List<SearchIndexEntity>

    /** Сколько строк в индексе: по нулю понимаем, что индекс надо собрать. */
    @Query("SELECT COUNT(*) FROM search_index")
    suspend fun count(): Long

    /** Добавляет строки в индекс. */
    @Insert
    suspend fun insertAll(rows: List<SearchIndexEntity>)

    /** Очищает индекс целиком. */
    @Query("DELETE FROM search_index")
    suspend fun clear()
}
