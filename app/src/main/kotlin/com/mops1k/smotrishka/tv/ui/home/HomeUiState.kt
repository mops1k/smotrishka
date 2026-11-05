package com.mops1k.smotrishka.tv.ui.home

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import java.time.Instant

/**
 * Состояние главного экрана: рейл категорий, список каналов и превью выбранного.
 */
sealed interface HomeUiState {

    /** Каталог читается. */
    data object Loading : HomeUiState

    /** Каталог пуст — каналы не загружены. */
    data object Empty : HomeUiState

    /**
     * Каталог не удалось прочитать.
     *
     * @param reason причина для экрана.
     */
    data class Error(val reason: String) : HomeUiState

    /**
     * Каталог готов.
     *
     * @param categories рейл категорий, первым идёт «Все»;
     * @param selectedCategoryId выбранная категория, `null` — «Все»;
     * @param channels каналы выбранной категории;
     * @param selectedChannelId канал для превью;
     * @param showChannels показывать ли список каналов вместо категорий;
     * @param now момент, на который посчитана программа (для полос прогресса).
     */
    data class Content(
        val categories: List<CategoryUi>,
        val selectedCategoryId: String?,
        val channels: List<ChannelUi>,
        val selectedChannelId: ChannelId?,
        val previewChannel: ChannelUi? = null,
        val showChannels: Boolean = false,
        val now: Instant = Instant.EPOCH,
    /** Каналы с проверенным архивом (значок ставится по факту выдачи). */
    val archiveVerified: Set<String> = emptySet(),

    /** Каналы с нестабильным потоком у провайдера. */
    val unstable: Set<String> = emptySet(),

    /** Идентификаторы избранных каналов. */
    val favorites: Set<String> = emptySet(),
    ) : HomeUiState
}

/**
 * Категория в рейле.
 *
 * @param id ключ категории, `null` — «Все»;
 * @param name имя для показа.
 */
data class CategoryUi(
    val id: String?,
    val name: String,
)

/**
 * Канал в списке вместе с ближайшей программой.
 *
 * @param channel канал;
 * @param nowTitle что идёт сейчас (`null` — программы нет);
 * @param nextTitle что будет дальше (`null` — неизвестно);
 * @param nowStart начало текущей передачи (`null` — программы нет);
 * @param nowEnd конец текущей передачи (`null` — программы нет).
 */
data class ChannelUi(
    val channel: Channel,
    val nowTitle: String?,
    val nextTitle: String?,
    val nowStart: Instant? = null,
    val nowEnd: Instant? = null,
)
