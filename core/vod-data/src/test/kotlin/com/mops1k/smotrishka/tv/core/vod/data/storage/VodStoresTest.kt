package com.mops1k.smotrishka.tv.core.vod.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.VodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TDD-спецификация хранилищ медиатеки: избранное не оставляет «хвостов» после замены,
 * а позиция просмотра живёт одной записью на карточку.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VodStoresTest {

    private lateinit var database: VodDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, VodDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun card(id: Int, name: String = "Фильм $id") = VodTitle(id = VodTitleId(id), title = name)

    @Test
    fun `избранное сохраняется и отдаётся новыми сверху`() = runTest {
        val store = VodFavoritesStore(database.favoritesDao())

        store.replace(
            VodFavorites()
                .add(card(1), atMillis = 100)
                .add(card(2), atMillis = 200),
        )

        val stored = store.favorites.first().items
        assertThat(stored.map { it.title.id.value }).containsExactly(2, 1).inOrder()
        assertThat(stored.first().title.title).isEqualTo("Фильм 2")
    }

    @Test
    fun `замена избранного удаляет карточки, которых больше нет`() = runTest {
        val store = VodFavoritesStore(database.favoritesDao())
        store.replace(VodFavorites().add(card(1), atMillis = 1).add(card(2), atMillis = 2))

        store.replace(VodFavorites().add(card(2), atMillis = 3))

        assertThat(store.favorites.first().items.map { it.title.id.value }).containsExactly(2)
    }

    @Test
    fun `очистка избранного удаляет всё`() = runTest {
        val store = VodFavoritesStore(database.favoritesDao())
        store.replace(VodFavorites().add(card(5), atMillis = 1))

        store.clear()

        assertThat(store.favorites.first().items).isEmpty()
    }

    @Test
    fun `позиция просмотра обновляется в одной записи`() = runTest {
        val store = VodProgressStore(database.progressDao())
        val id = VodTitleId(7)

        store.save(VodPlaybackProgress(titleId = id, positionMillis = 10_000, durationMillis = 100_000))
        store.save(VodPlaybackProgress(titleId = id, positionMillis = 40_000, durationMillis = 100_000))

        val stored = store.progress.first().single()
        assertThat(stored.positionMillis).isEqualTo(40_000)
        assertThat(stored.durationMillis).isEqualTo(100_000)
    }

    @Test
    fun `серия сохраняется вместе с позицией`() = runTest {
        val store = VodProgressStore(database.progressDao())

        store.save(
            VodPlaybackProgress(
                titleId = VodTitleId(10),
                episodeId = VodTitleId(12),
                episodeNumber = com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber(3),
                positionMillis = 1_000,
            ),
        )

        val stored = store.progress.first().single()
        assertThat(stored.episodeId?.value).isEqualTo(12)
        assertThat(stored.episodeNumber?.value).isEqualTo(3)
    }
}
