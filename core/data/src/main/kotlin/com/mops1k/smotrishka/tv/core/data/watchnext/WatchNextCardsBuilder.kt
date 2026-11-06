package com.mops1k.smotrishka.tv.core.data.watchnext

import kotlin.random.Random

/**
 * Собирает карточки ленты «Сейчас идёт» из каналов приложения.
 *
 * Приоритет источников (решение Александра 06.11.2026):
 * 1. избранные каналы;
 * 2. недавно просмотренные — свежие первыми;
 * 3. каналы последней выбранной категории;
 * 4. остальные каналы каталога — в случайном порядке.
 *
 * Канал показывается один раз, даже если попал в несколько источников: карточка избранного
 * не дублируется карточкой из «недавних». Каналы без текущей передачи в ленту не попадают —
 * карточка должна отвечать на вопрос «что идёт».
 *
 * @param random источник случайности: в приложении [Random.Default], в тестах — с фиксированным seed.
 */
class WatchNextCardsBuilder(private val random: Random = Random.Default) {

    /**
     * Строит карточки ленты.
     *
     * @param favourites идентификаторы избранных каналов;
     * @param recent недавно просмотренные каналы, свежие первыми;
     * @param lastCategoryId последняя выбранная категория, `null` — «Все каналы»;
     * @param channels каналы каталога вместе с текущей передачей;
     * @param limit максимум карточек: значение настройки;
     * @return карточки в порядке приоритета, без дублей, не больше [limit].
     */
    fun build(
        favourites: Collection<String>,
        recent: List<String>,
        lastCategoryId: String?,
        channels: List<WatchNextSource>,
        limit: Int = DEFAULT_LIMIT,
    ): List<WatchNextCard> {
        val max = limit.coerceIn(1, MAX_LIMIT)
        val withProgramme = channels.filter { it.nowTitle?.isNotBlank() == true }
        val byId = withProgramme.associateBy { it.channel.id.value }
        val ordered = LinkedHashMap<String, WatchNextSource>(max * 2)

        fun take(source: WatchNextSource?) {
            if (source == null || ordered.size >= max) return
            ordered.putIfAbsent(source.channel.id.value, source)
        }

        favourites.forEach { id -> take(byId[id]) }
        recent.forEach { id -> take(byId[id]) }
        if (lastCategoryId != null) {
            withProgramme
                .filter { it.channel.categoryId == lastCategoryId }
                .forEach { take(it) }
        }
        if (ordered.size < max) {
            withProgramme
                .filter { it.channel.id.value !in ordered }
                .shuffled(random)
                .forEach { take(it) }
        }

        return ordered.values.map(::card)
    }

    /** Карточка ленты по каналу и его текущей передаче. */
    private fun card(source: WatchNextSource): WatchNextCard = WatchNextCard(
        channelId = source.channel.id.value,
        title = source.channel.name.trim(),
        subtitle = source.nowTitle.orEmpty().trim(),
        logoUrl = source.channel.logoUrl?.takeIf { it.isNotBlank() },
        deepLink = WatchNextLink.of(source.channel.id.value),
    )

    companion object {

        /** Сколько карточек показываем по умолчанию. */
        const val DEFAULT_LIMIT: Int = 10

        /** Потолок настройки: больше лаунчер всё равно не показывает в один ряд. */
        const val MAX_LIMIT: Int = 20
    }
}
