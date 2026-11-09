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

        /**
         * Канал, подтверждённый нажатием OK, — по нему показывается живое превью.
         *
         * Пока фокус листает список, карточка (название, программа, кнопки) следует за фокусом,
         * но видео переключается только по OK: иначе просмотр дёргается на каждой строке.
         * `null` — превью ещё не подтверждали, показываем выбранный канал.
         */
        val previewVideoChannelId: ChannelId? = null,

        /**
         * Канал, который играет в превью (полный объект).
         *
         * Хранится отдельно от [channels], потому что подтверждённый канал может не входить
         * в текущую категорию или поиск — тогда превью не должно «прыгать» на канал под фокусом.
         */
        val previewVideoChannel: ChannelUi? = null,

        /**
         * Программа передач ещё читается из базы.
         *
         * В этом состоянии «Программы нет» показывать нельзя: передачи могут быть, их просто
         * ещё не прочитали. Подпись означает «передач действительно нет».
         */
        val epgPending: Boolean = false,
        val now: Instant = Instant.EPOCH,
        val archiveVerified: Set<String> = emptySet(),
        val unstable: Set<String> = emptySet(),
        val favorites: Set<String> = emptySet(),
        val recent: List<String> = emptyList(),
        val query: String = "",
        /** Показан ли список каналов; `false` — открыта панель категорий. */
        val showChannels: Boolean = true,
        val programmeOpen: Boolean = false,
        val schedule: List<ProgrammeRowUi> = emptyList(),
        /** Ручной сдвиг гида для канала карточки, часы (0 — без сдвига). */
        val epgShiftHours: Int = 0,
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
 * @param nowDescription описание текущей передачи из гида (`null` — провайдер не дал);
 * @param nowStart начало текущей передачи (`null` — программы нет);
 * @param nowEnd конец текущей передачи (`null` — программы нет).
 */
data class ChannelUi(
    val channel: Channel,
    val nowTitle: String?,
    val nextTitle: String?,
    val nowDescription: String? = null,
    val nowStart: Instant? = null,
    val nowEnd: Instant? = null,
)
