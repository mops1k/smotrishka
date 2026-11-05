package com.mops1k.smotrishka.tv.ui.home

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.ChannelId
import java.time.Instant

/**
 * Состояние главного экрана: раздел, категории, список каналов и карточка выбранного.
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
     * @param section текущий раздел;
     * @param categories плашка категорий (показывается только в «Телеканалах»);
     * @param selectedCategoryId выбранная категория, `null` — «Все каналы»;
     * @param channels каналы после фильтра по разделу, категории и поиску;
     * @param selectedChannelId канал, чья карточка справа;
     * @param previewChannel канал карточки;
     * @param now момент, на который посчитана программа;
     * @param archiveVerified каналы с проверенным архивом;
     * @param unstable каналы с нестабильным потоком;
     * @param favorites идентификаторы избранных каналов;
     * @param recent недавние каналы, свежие первыми;
     * @param query строка поиска;
     * @param programmeOpen развёрнута ли телепрограмма в карточке;
     * @param schedule передачи выбранного канала для разворота.
     */
    data class Content(
        val section: HomeSection = HomeSection.TV,
        val categories: List<CategoryUi>,
        val selectedCategoryId: String?,
        val channels: List<ChannelUi>,
        val selectedChannelId: ChannelId?,
        val previewChannel: ChannelUi? = null,
        val now: Instant = Instant.EPOCH,
        val archiveVerified: Set<String> = emptySet(),
        val unstable: Set<String> = emptySet(),
        val favorites: Set<String> = emptySet(),
        val recent: List<String> = emptyList(),
        val query: String = "",
        val programmeOpen: Boolean = false,
        val schedule: List<ProgrammeRowUi> = emptyList(),
    ) : HomeUiState
}

/**
 * Раздел приложения: определяет заголовок шапки, плашку категорий и состав списка.
 *
 * @param title заголовок раздела в шапке контента.
 */
enum class HomeSection(val title: String) {

    /** Телеканалы: плашка категорий и шапка списка. */
    TV("Телеканалы"),

    /** Избранное: без плашки и шапки списка. */
    FAVORITES("Избранное"),

    /** Недавние: без плашки и шапки списка. */
    RECENT("Недавние"),
}

/**
 * Категория в плашке.
 *
 * @param id ключ категории, `null` — «Все каналы»;
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
