package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
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

    private fun channel(id: String, name: String, category: String?) = Channel(
        id = ChannelId(id),
        name = name,
        streamUrl = "http://example.test/$id/index.m3u8",
        logoUrl = null,
        categoryId = category,
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
}
