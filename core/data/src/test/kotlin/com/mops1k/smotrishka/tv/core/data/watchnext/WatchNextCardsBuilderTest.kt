package com.mops1k.smotrishka.tv.core.data.watchnext

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import kotlin.random.Random
import org.junit.Test

/**
 * Выбор каналов для ленты «Сейчас идёт»: приоритет источников, дедупликация и лимит.
 */
class WatchNextCardsBuilderTest {

    /** Случайность с фиксированным seed: тесты не зависят от запуска. */
    private val builder = WatchNextCardsBuilder(Random(42))

    @Test
    fun `избранное идёт первым`() {
        val cards = builder.build(
            favourites = setOf("fav"),
            recent = listOf("recent"),
            lastCategoryId = "кино",
            channels = channelsOf(
                channel("other", "Прочий", categoryId = null),
                channel("fav", "Избранный", categoryId = "кино"),
                channel("recent", "Недавний", categoryId = "кино"),
            ),
        )

        assertThat(cards.map { it.channelId }).isEqualTo(listOf("fav", "recent", "other"))
    }

    @Test
    fun `недавние идут в порядке просмотра`() {
        val cards = builder.build(
            favourites = emptySet(),
            recent = listOf("second", "first"),
            lastCategoryId = null,
            channels = channelsOf(
                channel("first", "Первый"),
                channel("second", "Второй"),
            ),
        )

        assertThat(cards.map { it.channelId }).isEqualTo(listOf("second", "first"))
    }

    @Test
    fun `последняя категория добирает после недавних`() {
        val cards = builder.build(
            favourites = emptySet(),
            recent = listOf("recent"),
            lastCategoryId = "кино",
            channels = channelsOf(
                channel("movie", "Кино", categoryId = "кино"),
                channel("recent", "Недавний", categoryId = "спорт"),
                channel("sport", "Спорт", categoryId = "спорт"),
            ),
        )

        assertThat(cards.map { it.channelId }).isEqualTo(listOf("recent", "movie", "sport"))
    }

    @Test
    fun `канал из избранного не повторяется в недавних`() {
        val cards = builder.build(
            favourites = setOf("same"),
            recent = listOf("same"),
            lastCategoryId = null,
            channels = channelsOf(channel("same", "Один")),
        )

        assertThat(cards.map { it.channelId }).containsExactly("same")
    }

    @Test
    fun `дедупликация сквозная по всем источникам`() {
        // Один и тот же канал есть в избранном, в недавних и в последней категории.
        val cards = builder.build(
            favourites = setOf("same"),
            recent = listOf("same"),
            lastCategoryId = "кино",
            channels = channelsOf(
                channel("same", "Один", categoryId = "кино"),
                channel("other", "Другой"),
            ),
        )

        assertThat(cards.map { it.channelId }).isEqualTo(listOf("same", "other"))
        assertThat(cards.map { it.channelId }).containsNoDuplicates()
    }

    @Test
    fun `каналы без текущей передачи в ленту не попадают`() {
        val cards = builder.build(
            favourites = emptySet(),
            recent = emptyList(),
            lastCategoryId = null,
            channels = listOf(
                WatchNextSource(channel("with", "С программой"), nowTitle = "Новости"),
                WatchNextSource(channel("without", "Без программы"), nowTitle = null),
                WatchNextSource(channel("blank", "Пустая программа"), nowTitle = "   "),
            ),
        )

        assertThat(cards.map { it.channelId }).containsExactly("with")
    }

    @Test
    fun `лимит из настройки ограничивает ленту`() {
        val channels = (1..30).map { channel("$it", "Канал $it") }

        val cards = builder.build(
            favourites = emptySet(),
            recent = emptyList(),
            lastCategoryId = null,
            channels = channelsOf(*channels.toTypedArray()),
            limit = 5,
        )

        assertThat(cards).hasSize(5)
        assertThat(cards.map { it.channelId }.toSet()).hasSize(5)
    }

    @Test
    fun `по умолчанию лента не длиннее десяти карточек`() {
        val cards = builder.build(
            favourites = emptySet(),
            recent = emptyList(),
            lastCategoryId = null,
            channels = channelsOf(*(1..25).map { channel("$it", "Канал $it") }.toTypedArray()),
        )

        assertThat(cards).hasSize(WatchNextCardsBuilder.DEFAULT_LIMIT)
    }

    @Test
    fun `остаток набирается из всех каналов случайно и без повторов`() {
        val all = (1..20).map { channel("$it", "Канал $it") }
        val first = builder.build(
            favourites = emptySet(),
            recent = emptyList(),
            lastCategoryId = null,
            channels = channelsOf(*all.toTypedArray()),
        )
        val second = WatchNextCardsBuilder(Random(43)).build(
            favourites = emptySet(),
            recent = emptyList(),
            lastCategoryId = null,
            channels = channelsOf(*all.toTypedArray()),
        )

        assertThat(first.map { it.channelId }.toSet()).hasSize(10)
        assertThat(first.map { it.channelId }).isNotEqualTo(second.map { it.channelId })
    }

    @Test
    fun `без подходящих каналов лента пустая`() {
        val cards = builder.build(
            favourites = setOf("1"),
            recent = listOf("2"),
            lastCategoryId = "кино",
            channels = listOf(WatchNextSource(channel("1", "Без программы"), nowTitle = null)),
        )

        assertThat(cards).isEmpty()
    }

    @Test
    fun `ссылка карточки открывает именно этот канал`() {
        val id = "http://provider.example/channel.m3u8?a=1&b=2"
        val cards = builder.build(
            favourites = emptySet(),
            recent = emptyList(),
            lastCategoryId = null,
            channels = listOf(WatchNextSource(channel(id, "Первый"), nowTitle = "Новости")),
        )

        assertThat(WatchNextLink.channelId(cards.single().deepLink)).isEqualTo(id)
    }

    private fun channel(
        id: String,
        name: String,
        categoryId: String? = null,
        logo: String? = "https://logo/$id.png",
    ) = Channel(
        id = ChannelId(id),
        name = name,
        streamUrl = "https://stream/$id",
        logoUrl = logo,
        categoryId = categoryId,
    )

    private fun channelsOf(vararg channels: Channel): List<WatchNextSource> =
        channels.map { WatchNextSource(it, nowTitle = "Передача «${it.name}»") }
}
