package com.mops1k.smotrishka.tv.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Гид программы передач в базе приложения.
 *
 * @param id стабильный идентификатор записи;
 * @param name имя источника для настроек;
 * @param url адрес XMLTV;
 * @param addedAt момент добавления в миллисекундах — задаёт порядок разбора гидов.
 */
@Entity(tableName = "epg_guides")
data class EpgGuideEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val addedAt: Long,
)
