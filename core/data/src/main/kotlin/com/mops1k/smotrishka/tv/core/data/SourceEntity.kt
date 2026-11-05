package com.mops1k.smotrishka.tv.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Настроенный источник каталога.
 *
 * Секреты (пароли, коды) здесь не хранятся: в базе только их зашифрованные
 * идентификаторы, а значения — в зашифрованном хранилище.
 *
 * @param id ключ источника;
 * @param kind тип источника (`SourceKind.name`);
 * @param displayName имя для экрана настроек;
 * @param url адрес M3U-плейлиста, если источник задан ссылкой;
 * @param server сервер Xtream;
 * @param username логин Xtream;
 * @param createdAt время создания записи.
 */
@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val displayName: String,
    val url: String?,
    val server: String?,
    val username: String?,
    val createdAt: Long,
)
