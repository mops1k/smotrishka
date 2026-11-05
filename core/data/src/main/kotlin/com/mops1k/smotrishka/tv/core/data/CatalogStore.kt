package com.mops1k.smotrishka.tv.core.data

import androidx.room.withTransaction
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.Playlist

/**
 * Каталог каналов поверх Room.
 *
 * Каталог всегда заменяется целиком в одной транзакции: старый и новый список
 * не должны видеть друг друга, иначе на экране окажутся каналы двух плейлистов.
 *
 * @param database база каталога;
 * @param mapper перевод модели в строки и обратно.
 */
class CatalogStore(
    private val database: SmotrishkaDatabase,
    private val mapper: CatalogMapper = CatalogMapper(),
) {

    private val dao: CatalogDao get() = database.catalogDao()

    /**
     * Заменяет каталог целиком.
     *
     * @param playlist разобранный плейлист.
     */
    suspend fun replaceCatalog(playlist: Playlist) {
        database.withTransaction {
            dao.deleteChannels()
            dao.deleteCategories()
            dao.insertCategories(playlist.categories.mapIndexed { position, category ->
                mapper.toEntity(category, position)
            })
            dao.insertChannels(playlist.channels.map(mapper::toEntity))
        }
    }

    /** Читает сохранённый каталог. */
    suspend fun playlist(): Playlist =
        Playlist(
            categories = dao.categories().map(mapper::toModel),
            channels = dao.channels().map(mapper::toModel),
        )

    /**
     * Каналы одной категории.
     *
     * @param categoryId ключ категории.
     */
    suspend fun channelsByCategory(categoryId: String): List<Channel> =
        dao.channelsByCategory(categoryId).map(mapper::toModel)

    /** Сохраняет источник каталога. */
    suspend fun saveSource(source: SourceEntity) {
        dao.upsertSource(source)
    }

    /** Настроенные источники. */
    suspend fun sources(): List<SourceEntity> = dao.sources()
}
