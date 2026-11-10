package com.mops1k.smotrishka.tv.core.vod.domain

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodEvent
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodFavoriteAdded
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodFavoriteRemoved
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodPlaybackProgressed
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodPortalConnected
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodPortalRejected
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.testing.FakeVodClock
import com.mops1k.smotrishka.tv.core.vod.domain.testing.FakeVodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.testing.FakeVodPortalPort
import com.mops1k.smotrishka.tv.core.vod.domain.testing.FakeVodSearchPort
import com.mops1k.smotrishka.tv.core.vod.domain.testing.InMemoryVodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.testing.InMemoryVodFavoritesPort
import com.mops1k.smotrishka.tv.core.vod.domain.testing.InMemoryVodProgressPort
import com.mops1k.smotrishka.tv.core.vod.domain.testing.sampleTitle
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ConnectVodPortal
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ObserveVodConnection
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.SaveVodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.SearchVodTitles
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ToggleVodFavorite
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Поведение use case-ов на подделках портов: подключение, поиск, избранное и прогресс.
 */
class VodUseCasesTest {

    private val url = "http://portal.example/api/v1/"
    private val key = "e8179fbc74ceedf-731012aca5e3898"
    private val categories = listOf(VodCategory(VodCategoryId(1), "фильмы", 10))

    private fun configOf(rawUrl: String = url, rawKey: String = key) =
        requireNotNull(VodPortalConfig.from(rawUrl, rawKey))

    private fun connect(
        portal: FakeVodPortalPort = FakeVodPortalPort(categories),
        config: InMemoryVodConfigPort = InMemoryVodConfigPort(),
        favorites: InMemoryVodFavoritesPort = InMemoryVodFavoritesPort(),
        progress: InMemoryVodProgressPort = InMemoryVodProgressPort(),
        bus: FakeVodEventBus = FakeVodEventBus(),
        clock: FakeVodClock = FakeVodClock(),
    ) = ConnectVodPortal(portal, config, favorites, progress, bus, clock)

    @Test
    fun `успешная проверка сохраняет подключение и публикует событие`() = runTest {
        val config = InMemoryVodConfigPort()
        val bus = FakeVodEventBus()

        val result = connect(config = config, bus = bus)(configOf())

        assertThat(result.valueOrNull()).isEqualTo(categories)
        assertThat(config.status.first()).isInstanceOf(VodPortalStatus.Connected::class.java)
        assertThat(bus.published.filterIsInstance<VodPortalConnected>()).hasSize(1)
        assertThat(bus.published.filterIsInstance<VodPortalConnected>().first().categories).isEqualTo(categories)
    }

    @Test
    fun `отказ портала сохраняет причину и публикует отклонение`() = runTest {
        val config = InMemoryVodConfigPort()
        val bus = FakeVodEventBus()
        val portal = FakeVodPortalPort(failure = VodFailure.SubscriptionRequired)

        val result = connect(portal = portal, config = config, bus = bus)(configOf())

        assertThat((result as VodResult.Failure).failure).isEqualTo(VodFailure.SubscriptionRequired)
        assertThat(config.status.first()).isInstanceOf(VodPortalStatus.Failed::class.java)
        assertThat(bus.published.filterIsInstance<VodPortalRejected>()).hasSize(1)
    }

    @Test
    fun `смена портала сбрасывает избранное и историю просмотра`() = runTest {
        val config = InMemoryVodConfigPort()
        val favorites = InMemoryVodFavoritesPort()
        val progress = InMemoryVodProgressPort()
        connect(config = config, favorites = favorites, progress = progress)(configOf())

        favorites.replace(VodFavorites().add(sampleTitle(1), atMillis = 5))
        progress.save(VodPlaybackProgress(titleId = VodTitleId(1), positionMillis = 60_000))

        val otherUrl = "http://other.example/api/v1/"
        connect(config = config, favorites = favorites, progress = progress)(configOf(otherUrl))

        assertThat(favorites.favorites.first().items).isEmpty()
        assertThat(progress.progress.first()).isEmpty()
    }

    @Test
    fun `повторное подключение того же портала не стирает историю`() = runTest {
        val config = InMemoryVodConfigPort()
        val favorites = InMemoryVodFavoritesPort()
        val progress = InMemoryVodProgressPort()
        connect(config = config, favorites = favorites, progress = progress)(configOf())
        favorites.replace(VodFavorites().add(sampleTitle(1), atMillis = 5))

        connect(config = config, favorites = favorites, progress = progress)(configOf())

        assertThat(favorites.favorites.first().items).hasSize(1)
    }

    @Test
    fun `пункт меню появляется только у проверенного подключения`() = runTest {
        val config = InMemoryVodConfigPort()
        val observe = ObserveVodConnection(config)

        assertThat(observe().first()).isEqualTo(VodPortalStatus.NotConfigured)

        connect(config = config)(configOf())

        assertThat(observe().first()).isInstanceOf(VodPortalStatus.Connected::class.java)
    }

    @Test
    fun `пустой поисковый запрос не уходит на портал`() = runTest {
        val config = InMemoryVodConfigPort()
        val search = FakeVodSearchPort()
        connect(config = config)(configOf())

        val result = SearchVodTitles(search, config)("   ")

        assertThat(result.valueOrNull()).isEmpty()
        assertThat(search.queries).isEmpty()
    }

    @Test
    fun `избранное переключается и публикует оба события`() = runTest {
        val favorites = InMemoryVodFavoritesPort()
        val bus = FakeVodEventBus()
        val toggle = ToggleVodFavorite(favorites, bus, FakeVodClock())
        val title = sampleTitle(3)

        toggle(title)
        toggle(title)

        assertThat(bus.published.filterIsInstance<VodFavoriteAdded>()).hasSize(1)
        assertThat(bus.published.filterIsInstance<VodFavoriteRemoved>()).hasSize(1)
        assertThat(favorites.favorites.first().items).isEmpty()
    }

    @Test
    fun `позиция просмотра сохраняется и публикует событие`() = runTest {
        val progress = InMemoryVodProgressPort()
        val bus = FakeVodEventBus()
        val save = SaveVodPlaybackProgress(progress, bus, FakeVodClock())

        save(titleId = VodTitleId(4), positionMillis = 90_000, durationMillis = 600_000)

        val stored = progress.progress.first().single()
        assertThat(stored.positionMillis).isEqualTo(90_000)
        assertThat(stored.durationMillis).isEqualTo(600_000)
        assertThat(bus.published.filterIsInstance<VodPlaybackProgressed>()).hasSize(1)
    }

    @Test
    fun `сохранение позиции не теряет выбранную серию`() = runTest {
        val progress = InMemoryVodProgressPort()
        val save = SaveVodPlaybackProgress(progress, FakeVodEventBus(), FakeVodClock())
        val season = VodTitleId(10)
        val episode = VodTitleId(11)

        save(titleId = season, positionMillis = 10_000, durationMillis = 100_000, episodeId = episode)
        save(titleId = season, positionMillis = 50_000, durationMillis = 100_000)

        assertThat(progress.progress.first().single().episodeId).isEqualTo(episode)
    }

    @Test
    fun `конфигурация из пользовательского ввода нормализует адрес`() {
        val config = VodPortalConfig.from("portal.example/api/v1", key)

        assertThat(config).isNull()
        assertThat(VodPortalConfig.from(url, "  $key  ")?.key?.value).isEqualTo(key)
        assertThat(VodPortalConfig.from(url, "ключ с пробелом")).isNull()
    }

    @Test
    fun `события несут время доменных часов`() = runTest {
        val clock = FakeVodClock(now = 777)
        val bus = FakeVodEventBus()
        val save = SaveVodPlaybackProgress(InMemoryVodProgressPort(), bus, clock)

        save(titleId = VodTitleId(1), positionMillis = 1_000, durationMillis = 2_000)

        val event: VodEvent = bus.published.single()
        assertThat(event.occurredAtMillis).isEqualTo(777)
    }
}
