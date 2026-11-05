package com.mops1k.smotrishka.tv.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Связь канала каталога с каналом программы передач.
 *
 * Нужна, чтобы не сопоставлять каналы с гидом на каждом открытии экрана: связь считается один
 * раз при обновлении каталога и сохраняется в базу. У каналов каталога `epgId` часто пустой,
 * поэтому сопоставление идёт по нормализованному названию (см. `EpgMatcher`).
 *
 * @param channelId идентификатор канала каталога;
 * @param epgChannelId идентификатор канала гида, по которому лежат передачи.
 */
@Entity(tableName = "channel_epg")
data class ChannelEpgEntity(
    @PrimaryKey val channelId: String,
    val epgChannelId: String,
)
