package com.mops1k.smotrishka.tv.core.vod.domain.testing

import com.mops1k.smotrishka.tv.core.vod.domain.VodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.VodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodEvent
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodCatalogPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPortalPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodSearchPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodTitlePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Подделки портов домена для тестов: собственные типы не мокаем — пишем простые in-memory
 * реализации, которые ведут себя как настоящие адаптеры.
 */

/** Шина событий, запоминающая опубликованное. */
internal class FakeVodEventBus : VodEventBus {

    private val stream = MutableSharedFlow<VodEvent>(extraBufferCapacity = 64)

    /** Опубликованные события в порядке появления. */
    val published: MutableList<VodEvent> = mutableListOf()

    override val events: Flow<VodEvent> = stream

    override suspend fun publish(event: VodEvent) {
        published += event
        stream.emit(event)
    }
}

/** Часы с управляемым временем. */
internal class FakeVodClock(private var now: Long = 1_000L) : VodClock {

    override fun nowMillis(): Long = now

    /** Двигает время вперёд. */
    fun advance(millis: Long) {
        now += millis
    }
}

/** Хранилище подключения в памяти. */
internal class InMemoryVodConfigPort : VodConfigPort {

    private val state = MutableStateFlow<VodPortalStatus>(VodPortalStatus.NotConfigured)

    override val status: Flow<VodPortalStatus> = state

    override suspend fun saveConnected(config: VodPortalConfig) {
        state.value = VodPortalStatus.Connected(config)
    }

    override suspend fun saveFailed(config: VodPortalConfig, failure: VodFailure) {
        state.value = VodPortalStatus.Failed(config, failure)
    }

    override suspend fun clear() {
        state.value = VodPortalStatus.NotConfigured
    }
}

/** Избранное в памяти. */
internal class InMemoryVodFavoritesPort : VodFavoritesPort {

    private val state = MutableStateFlow(VodFavorites())

    override val favorites: Flow<VodFavorites> = state

    override suspend fun replace(favorites: VodFavorites) {
        state.value = favorites
    }

    override suspend fun clear() {
        state.value = VodFavorites()
    }
}

/** История просмотра в памяти. */
internal class InMemoryVodProgressPort : VodPlaybackProgressPort {

    private val state = MutableStateFlow<List<VodPlaybackProgress>>(emptyList())

    override val progress: Flow<List<VodPlaybackProgress>> = state

    override suspend fun save(progress: VodPlaybackProgress) {
        state.value = state.value.filterNot { it.titleId == progress.titleId } + progress
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

/** Портал, отдающий заранее заданные категории или ошибку. */
internal class FakeVodPortalPort(
    private val categories: List<VodCategory> = listOf(VodCategory(VodCategoryId(1), "фильмы", 2)),
    private val failure: VodFailure? = null,
) : VodPortalPort {

    /** Сколько раз к порталу обратились. */
    var calls: Int = 0
        private set

    override suspend fun loadCategories(config: VodPortalConfig): VodResult<List<VodCategory>> {
        calls++
        return failure?.let { VodResult.Failure(it) } ?: VodResult.Success(categories)
    }
}

/** Каталог, отдающий одну и ту же страницу. */
internal class FakeVodCatalogPort(private val page: VodCatalogPage = VodCatalogPage(emptyList(), null)) :
    VodCatalogPort {

    /** Последнее запрошенное смещение. */
    var lastOffset: Int? = null
        private set

    override suspend fun loadPage(
        config: VodPortalConfig,
        categoryId: VodCategoryId,
        offset: Int,
        limit: Int,
    ): VodResult<VodCatalogPage> {
        lastOffset = offset
        return VodResult.Success(page)
    }
}

/** Карточка, отдающая заданные детали. */
internal class FakeVodTitlePort(private val details: VodTitleDetails? = null) : VodTitlePort {

    override suspend fun loadDetails(
        config: VodPortalConfig,
        titleId: VodTitleId,
    ): VodResult<VodTitleDetails> =
        details?.let { VodResult.Success(it) } ?: VodResult.Failure(VodFailure.BadFormat)

    override suspend fun loadQualities(
        config: VodPortalConfig,
        titleId: VodTitleId,
    ): VodResult<Map<VodQuality, String>> =
        details?.let { VodResult.Success(it.qualities(titleId)) }
            ?: VodResult.Failure(VodFailure.BadFormat)
}

/** Поиск, запоминающий запросы. */
internal class FakeVodSearchPort(private val found: List<VodTitle> = emptyList()) : VodSearchPort {

    /** Запросы, ушедшие в портал. */
    val queries: MutableList<String> = mutableListOf()

    override suspend fun search(
        config: VodPortalConfig,
        query: String,
        offset: Int,
    ): VodResult<List<VodTitle>> {
        queries += query
        return VodResult.Success(found)
    }
}

/** Карточка каталога для тестов. */
internal fun sampleTitle(id: Int, name: String = "Фильм $id"): VodTitle = VodTitle(
    id = VodTitleId(id),
    title = name,
    year = 2020,
    posterUrl = "http://portal/p_$id.jpg",
    previewPosterUrl = "http://portal/pl_$id.jpg",
)
