package com.mops1k.smotrishka.tv.core.data

import com.google.gson.Gson
import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import com.mops1k.smotrishka.tv.core.model.Category
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.StreamHeaders

/**
 * Переводит доменную модель в строки Room и обратно.
 *
 * Режим архива хранится именем перечисления; незнакомое значение (например, из будущей
 * версии с другим набором режимов) читается как `UNKNOWN`, а не роняет чтение каталога.
 *
 * @param gson сериализация заголовков потока.
 */
class CatalogMapper(private val gson: Gson = Gson()) {

    fun toEntity(category: Category, position: Int): CategoryEntity =
        CategoryEntity(id = category.id, name = category.name, position = position)

    fun toModel(entity: CategoryEntity): Category =
        Category(id = entity.id, name = entity.name)

    fun toEntity(channel: Channel): ChannelEntity =
        ChannelEntity(
            id = channel.id.value,
            name = channel.name,
            streamUrl = channel.streamUrl,
            logoUrl = channel.logoUrl,
            categoryId = channel.categoryId,
            epgId = channel.epgId,
            archiveMode = channel.archive?.mode?.name,
            archiveSource = channel.archive?.source,
            archiveDays = channel.archive?.days ?: 0,
            headersJson = channel.headers?.let(gson::toJson),
        )

    fun toModel(entity: ChannelEntity): Channel =
        Channel(
            id = ChannelId(entity.id),
            name = entity.name,
            streamUrl = entity.streamUrl,
            logoUrl = entity.logoUrl,
            categoryId = entity.categoryId,
            epgId = entity.epgId,
            archive = entity.archiveMode?.let { mode ->
                ArchiveInfo(
                    mode = parseMode(mode),
                    source = entity.archiveSource,
                    days = entity.archiveDays,
                )
            },
            headers = entity.headersJson?.let(::parseHeaders),
        )

    private fun parseMode(raw: String): CatchupMode =
        runCatching { CatchupMode.valueOf(raw) }.getOrDefault(CatchupMode.UNKNOWN)

    private fun parseHeaders(json: String): StreamHeaders? =
        runCatching { gson.fromJson(json, StreamHeaders::class.java) }.getOrNull()
}
