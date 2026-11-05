package com.mops1k.smotrishka.tv.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Канал из файла программы передач.
 *
 * @param id идентификатор канала в EPG;
 * @param displayName отображаемое имя;
 * @param iconUrl иконка канала.
 */
@Entity(tableName = "epg_channels")
data class EpgChannelEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val iconUrl: String?,
)
