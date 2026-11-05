package com.mops1k.smotrishka.tv.core.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация списка недавних каналов.
 */
class RecentChannelsTest {

    @Test
    fun `новый канал встаёт в начало`() {
        val result = RecentChannels.add(listOf("a", "b"), "c")

        assertThat(result).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `повторный выбор поднимает канал наверх без дубля`() {
        val result = RecentChannels.add(listOf("a", "b", "c"), "b")

        assertThat(result).containsExactly("b", "a", "c").inOrder()
    }

    @Test
    fun `список ограничен десятью каналами`() {
        val current = (1..10).map { "c$it" }

        val result = RecentChannels.add(current, "new")

        assertThat(result).hasSize(10)
        assertThat(result.first()).isEqualTo("new")
        assertThat(result).doesNotContain("c10")
    }

    @Test
    fun `пустой идентификатор не меняет список`() {
        val result = RecentChannels.add(listOf("a"), " ")

        assertThat(result).containsExactly("a")
    }
}
