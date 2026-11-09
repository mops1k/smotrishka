package com.mops1k.smotrishka.tv.core.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Состояние импорта гида: строка живёт, пока гид не разобран полностью.
 *
 * Нужна, чтобы обрыв импорта (убитый процесс, потеря сети, ANR) не оставлял устройство
 * с частично заполненной программой: незавершённый импорт перезапускается при следующем
 * старте, даже если источник считается «свежим» (требование Александра 10.11.2026).
 *
 * @param source адрес гида — он же первичный ключ;
 * @param running импорт идёт прямо сейчас;
 * @param startedAt начало последнего импорта;
 * @param updatedAt когда в последний раз обновлялся прогресс;
 * @param parsedChannels сколько каналов гида уже сохранено;
 * @param addedProgrammes сколько передач уже добавлено.
 */
@Entity(tableName = "epg_imports")
data class EpgImportEntity(
    @PrimaryKey val source: String,
    val running: Boolean,
    @ColumnInfo(name = "startedAt") val startedAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "parsedChannels") val parsedChannels: Int,
    @ColumnInfo(name = "addedProgrammes") val addedProgrammes: Int,
)
