package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import com.mops1k.smotrishka.tv.core.model.CatchupMode
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import org.junit.Test

/**
 * TDD-спецификация отбора каналов для плеера.
 *
 * Один и тот же список нужен и переключению (Up/Down, свайп), и оверлею: если порядок
 * разойдётся, «следующий» в списке не совпадёт с тем, что включает жест. Поэтому отбор
 * и сортировка вынесены из ViewModel в чистую функцию.
 */
class ChannelListScopeTest {

    private val allCategory = "\u0000all"
    private val favoritesCategory = "\u0001favorites"

    private fun channel(
        id: String,
        name: String,
        category: String?,
        archive: ArchiveInfo? = null,
    ) = Channel(
        id = ChannelId(id),
        name = name,
        streamUrl = "http://example.test/$id/index.m3u8",
        logoUrl = null,
        categoryId = category,
        archive = archive,
    )

    private val channels = listOf(
        channel(id = "b", name = "Бета", category = "кино"),
        channel(id = "a", name = "Альфа", category = "кино"),
        channel(id = "c", name = "Гамма", category = "новости"),
    )

    @Test
    fun `категория оставляет свои каналы в порядке имён`() {
        val scope = ChannelListScope.resolve(
            channels = channels,
            category = "кино",
            favoriteIds = emptySet(),
            allCategory = allCategory,
            favoritesCategory = favoritesCategory,
        )

        assertThat(scope.map { it.id }).containsExactly("a", "b").inOrder()
        assertThat(scope.map { it.name }).containsExactly("Альфа", "Бета").inOrder()
    }

    @Test
    fun `категория «Все» отдаёт все каналы`() {
        val scope = ChannelListScope.resolve(
            channels = channels,
            category = allCategory,
            favoriteIds = emptySet(),
            allCategory = allCategory,
            favoritesCategory = favoritesCategory,
        )

        assertThat(scope.map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `неизвестная категория считается «Все»`() {
        val scope = ChannelListScope.resolve(
            channels = channels,
            category = null,
            favoriteIds = emptySet(),
            allCategory = allCategory,
            favoritesCategory = favoritesCategory,
        )

        assertThat(scope.map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `«Избранное» отдаёт только избранные каналы в порядке имён`() {
        val scope = ChannelListScope.resolve(
            channels = channels,
            category = favoritesCategory,
            favoriteIds = setOf("c", "b"),
            allCategory = allCategory,
            favoritesCategory = favoritesCategory,
        )

        assertThat(scope.map { it.id }).containsExactly("b", "c").inOrder()
    }

    @Test
    fun `канал с архивом помечен в списке оверлея`() {
        // Архив берём из заявки плейлиста (решение Александра 09.11.2026): фоновая проверка
        // выдачи давала ложные «нет архива» у каналов, где он есть.
        val scope = ChannelListScope.resolve(
            channels = listOf(
                channel(id = "a", name = "Альфа", category = "кино"),
                channel(
                    id = "b",
                    name = "Бета",
                    category = "кино",
                    archive = ArchiveInfo(mode = CatchupMode.DEFAULT, days = 7),
                ),
            ),
            category = "кино",
            favoriteIds = emptySet(),
            allCategory = allCategory,
            favoritesCategory = favoritesCategory,
        )

        assertThat(scope.first { it.id == "a" }.hasArchive).isFalse()
        assertThat(scope.first { it.id == "b" }.hasArchive).isTrue()
    }
}
