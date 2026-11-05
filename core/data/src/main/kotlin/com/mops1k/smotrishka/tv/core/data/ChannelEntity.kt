package com.mops1k.smotrishka.tv.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Канал каталога в базе.
 *
 * Заголовки потока хранятся как JSON: набор заголовков произвольный,
 * отдельные колонки под каждый были бы бесполезны.
 *
 * @param id стабильный ключ канала;
 * @param name название канала;
 * @param streamUrl адрес потока;
 * @param logoUrl адрес логотипа;
 * @param categoryId категория канала;
 * @param epgId идентификатор программы;
 * @param archiveMode имя режима архива (`CatchupMode.name`), `null` — архива нет;
 * @param archiveSource шаблон архивного адреса;
 * @param archiveDays глубина архива в днях;
 * @param headersJson заголовки потока сериализованные gson.
 */
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val categoryId: String?,
    val epgId: String?,
    val archiveMode: String?,
    val archiveSource: String?,
    val archiveDays: Int,
    val headersJson: String?,
)
