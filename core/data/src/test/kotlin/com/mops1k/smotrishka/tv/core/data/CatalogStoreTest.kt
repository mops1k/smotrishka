package com.mops1k.smotrishka.tv.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import com.mops1k.smotrishka.tv.core.model.Category
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.core.model.Playlist
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TDD-спецификация каталога в Room: замена каталога целиком, чтение обратно,
 * фильтр по категории и сохранение источников.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogStoreTest {

    private lateinit var database: SmotrishkaDatabase
    private lateinit var store: CatalogStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            SmotrishkaDatabase::class.java,
        ).build()
        store = CatalogStore(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun playlist(name: String) = Playlist(
        categories = listOf(Category(id = "основные", name = "Основные")),
        channels = listOf(
            Channel(
                id = ChannelId("one"),
                name = name,
                streamUrl = "http://example.com/1.m3u8",
                categoryId = "основные",
                archive = ArchiveInfo(mode = CatchupMode.DEFAULT, days = 7),
            ),
            Channel(
                id = ChannelId("two"),
                name = "Второй",
                streamUrl = "http://example.com/2.m3u8",
                categoryId = "кино",
            ),
        ),
    )

    @Test
    fun `сохраняет и читает каталог`() = runTest {
        store.replaceCatalog(playlist("Первый"))

        val saved = store.playlist()

        assertThat(saved.categories.single().name).isEqualTo("Основные")
        assertThat(saved.channels.map { it.name }).containsExactly("Первый", "Второй").inOrder()
        assertThat(saved.channels.first().archive?.days).isEqualTo(7)
    }

    @Test
    fun `повторное сохранение заменяет прежний каталог`() = runTest {
        store.replaceCatalog(playlist("Старый"))
        store.replaceCatalog(playlist("Новый"))

        val saved = store.playlist()

        assertThat(saved.channels.map { it.name }).containsExactly("Новый", "Второй")
    }

    @Test
    fun `каналы фильтруются по категории`() = runTest {
        store.replaceCatalog(playlist("Первый"))

        val movies = store.channelsByCategory("кино")

        assertThat(movies.map { it.name }).containsExactly("Второй")
    }

    @Test
    fun `источник сохраняется и читается`() = runTest {
        store.saveSource(
            SourceEntity(
                id = "iedem",
                kind = "IEDEM_CODE",
                displayName = "Смотришка по коду",
                url = null,
                server = null,
                username = null,
                createdAt = 1_760_000_000_000L,
            ),
        )

        assertThat(store.sources().single().displayName).isEqualTo("Смотришка по коду")
    }
}
