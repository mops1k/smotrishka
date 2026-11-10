package com.mops1k.smotrishka.tv.core.vod.domain

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.testing.sampleTitle
import org.junit.Test

/**
 * Инварианты агрегатов медиатеки: каталог не смешивает категории, избранное не даёт дублей,
 * прогресс правильно считает досмотр, карточка сама знает следующую серию.
 */
class VodAggregatesTest {

    private val movies = VodCategory(VodCategoryId(1), "фильмы", 10)
    private val serials = VodCategory(VodCategoryId(2), "сериалы", 5)

    @Test
    fun `выбор категории очищает загруженные карточки и курсор`() {
        val library = VodLibrary()
            .withCategories(listOf(movies, serials))
            .append(VodCatalogPage(listOf(sampleTitle(1)), nextOffset = 31, total = 10))

        val switched = library.select(serials.id)

        assertThat(switched.titles).isEmpty()
        assertThat(switched.nextOffset).isNull()
        assertThat(switched.hasMore).isFalse()
        assertThat(switched.selectedCategory).isEqualTo(serials)
    }

    @Test
    fun `дозагрузка страницы склеивает карточки без дублей`() {
        val first = VodLibrary()
            .withCategories(listOf(movies))
            .append(VodCatalogPage(listOf(sampleTitle(1), sampleTitle(2)), nextOffset = 2, total = 3))

        val merged = first.append(VodCatalogPage(listOf(sampleTitle(2), sampleTitle(3)), nextOffset = null))

        assertThat(merged.titles.map { it.id.value }).containsExactly(1, 2, 3).inOrder()
        assertThat(merged.hasMore).isFalse()
        assertThat(merged.total).isEqualTo(3)
    }

    @Test
    fun `карточки исчезнувшей категории не остаются на экране`() {
        val library = VodLibrary()
            .withCategories(listOf(movies, serials))
            .select(serials.id)
            .append(VodCatalogPage(listOf(sampleTitle(7)), nextOffset = null))

        val refreshed = library.withCategories(listOf(movies))

        assertThat(refreshed.selectedCategory).isEqualTo(movies)
        assertThat(refreshed.titles).isEmpty()
    }

    @Test
    fun `избранное хранит новые сверху и не даёт дублей`() {
        val favorites = VodFavorites()
            .add(sampleTitle(1), atMillis = 100)
            .add(sampleTitle(2), atMillis = 200)
            .add(sampleTitle(1), atMillis = 300)

        assertThat(favorites.items.map { it.title.id.value }).containsExactly(1, 2).inOrder()
        assertThat(favorites.items.first().addedAtMillis).isEqualTo(300)
        assertThat(favorites.contains(VodTitleId(2))).isTrue()
    }

    @Test
    fun `переключение избранного сообщает добавление и удаление`() {
        val favorites = VodFavorites()
        val title = sampleTitle(5)

        val added = favorites.toggle(title, atMillis = 10)
        val removed = added.favorites.toggle(title, atMillis = 20)

        assertThat(added.added).isTrue()
        assertThat(added.favorites.contains(title.id)).isTrue()
        assertThat(removed.added).isFalse()
        assertThat(removed.favorites.contains(title.id)).isFalse()
    }

    @Test
    fun `прогресс считает долю, достмотр и возможность продолжить`() {
        val fresh = VodPlaybackProgress(titleId = VodTitleId(1))
        val middle = fresh.withPosition(positionMillis = 60_000, durationMillis = 600_000, atMillis = 10)
        val finished = fresh.withPosition(positionMillis = 590_000, durationMillis = 600_000, atMillis = 20)

        assertThat(fresh.canResume).isFalse()
        assertThat(middle.canResume).isTrue()
        assertThat(middle.fraction).isWithin(0.001f).of(0.1f)
        assertThat(finished.isFinished).isTrue()
        assertThat(finished.canResume).isFalse()
    }

    @Test
    fun `карточка сезона знает первую и следующую серию`() {
        val details = VodTitleDetails(
            title = sampleTitle(100, "Сезон 1"),
            episodes = listOf(
                VodEpisode(VodEpisodeNumber(1), VodTitleId(101), "Серия 1", "http://p/1"),
                VodEpisode(VodEpisodeNumber(2), VodTitleId(102), "Серия 2", "http://p/2"),
            ),
        )

        assertThat(details.isSerial).isTrue()
        assertThat(details.firstEpisode?.id).isEqualTo(VodTitleId(101))
        assertThat(details.nextEpisode(VodTitleId(101))?.id).isEqualTo(VodTitleId(102))
        assertThat(details.nextEpisode(VodTitleId(102))).isNull()
        assertThat(details.previousEpisode(VodTitleId(102))?.id).isEqualTo(VodTitleId(101))
        assertThat(details.previousEpisode(VodTitleId(101))).isNull()
        assertThat(details.defaultStreamUrl).isEqualTo("http://p/1")
    }

    @Test
    fun `одиночный фильм запускается своим потоком`() {
        val details = VodTitleDetails(
            title = sampleTitle(7),
            stream = com.mops1k.smotrishka.tv.core.vod.domain.model.VodStream("http://p/master.m3u8"),
        )

        assertThat(details.isSerial).isFalse()
        assertThat(details.defaultStreamUrl).isEqualTo("http://p/master.m3u8")
    }
}
