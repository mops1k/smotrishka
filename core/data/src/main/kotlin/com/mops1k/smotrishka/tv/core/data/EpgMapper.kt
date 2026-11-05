package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Programme
import java.time.Instant

/**
 * Переводит программу передач в строки Room и обратно.
 *
 * Время хранится в epoch-миллисекундах: так запросы по окну остаются простыми
 * и не зависят от часового пояса устройства.
 */
class EpgMapper {

    fun toEntity(channel: EpgChannel): EpgChannelEntity =
        EpgChannelEntity(id = channel.id, displayName = channel.displayName, iconUrl = channel.iconUrl)

    fun toModel(entity: EpgChannelEntity): EpgChannel =
        EpgChannel(id = entity.id, displayName = entity.displayName, iconUrl = entity.iconUrl)

    fun toEntity(programme: Programme): ProgrammeEntity =
        ProgrammeEntity(
            channelId = programme.channelId,
            startAt = programme.start.toEpochMilli(),
            endAt = programme.end.toEpochMilli(),
            title = programme.title,
            subTitle = programme.subTitle,
            description = programme.description,
            catchupId = programme.catchupId,
            iconUrl = programme.iconUrl,
        )

    fun toModel(entity: ProgrammeEntity): Programme =
        Programme(
            channelId = entity.channelId,
            title = entity.title,
            subTitle = entity.subTitle,
            description = entity.description,
            start = Instant.ofEpochMilli(entity.startAt),
            end = Instant.ofEpochMilli(entity.endAt),
            catchupId = entity.catchupId,
            iconUrl = entity.iconUrl,
        )
}
