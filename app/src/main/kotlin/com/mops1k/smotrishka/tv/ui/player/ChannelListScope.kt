package com.mops1k.smotrishka.tv.ui.player

import com.mops1k.smotrishka.tv.core.model.Channel
import java.time.Instant

/**
 * Канал в списке плеера: для оверлея и переключения достаточно идентификатора и названия,
 * а строке панели ещё нужны признаки текущей передачи и архива.
 *
 * @param id идентификатор канала в каталоге;
 * @param name название для показа;
 * @param hasArchive у канала есть архив по заявке плейлиста;
 * @param programmeTitle текущая передача: показывается под названием тонким шрифтом;
 * @param programmeDescription описание текущей передачи из гида (`null` — провайдер не дал);
 * @param programmeStart начало текущей передачи (со сдвигом гида);
 * @param programmeEnd конец текущей передачи (со сдвигом гида).
 */
data class ChannelRef(
    val id: String,
    val name: String,
    // Логотип канала: у каталога провайдера его часто нет, тогда берём иконку из гида.
    val logoUrl: String? = null,
    val hasArchive: Boolean = false,
    val programmeTitle: String? = null,
    val programmeDescription: String? = null,
    val programmeStart: Instant? = null,
    val programmeEnd: Instant? = null,
)

/**
 * Отбор каналов, доступных из плеера.
 *
 * Плеер не знает категорию из маршрута, но знает последнюю категорию пользователя из настроек.
 * Правило то же, что и при переключении каналов: в «Избранном» — только избранные, в «Все» —
 * весь список, в обычной категории — её каналы. Сортировка по названию без учёта регистра:
 * порядок оверлея обязан совпадать с порядком переключения, иначе «следующий» в списке
 * не совпадёт с тем, что включает жест или Up/Down.
 */
object ChannelListScope {

    /**
     * Собирает каналы в порядке переключения.
     *
     * @param channels все каналы каталога;
     * @param category последняя категория пользователя: `null` и [allCategory] — все каналы,
     *   [favoritesCategory] — только избранные;
     * @param favoriteIds идентификаторы избранных каналов;
     * @param allCategory значение настройки для «Все»;
     * @param favoritesCategory значение настройки для «Избранного».
     */
    fun resolve(
        channels: List<Channel>,
        category: String?,
        favoriteIds: Set<String>,
        allCategory: String,
        favoritesCategory: String,
    ): List<ChannelRef> {
        val scoped = when {
            category == favoritesCategory -> channels.filter { it.id.value in favoriteIds }
            category == null || category == allCategory -> channels
            else -> channels.filter { it.categoryId == category }
        }

        return scoped
            .sortedBy { it.name.lowercase() }
            .map {
                ChannelRef(
                    id = it.id.value,
                    name = it.name,
                    // Архив показываем так, как заявил плейлист: фоновая проверка выдачи давала
                    // ложные «нет архива» у каналов, где он есть (решение Александра 09.11.2026).
                    hasArchive = it.archive != null,
                )
            }
    }
}

/**
 * Недавний канал в панели плеера.
 *
 * @param id идентификатор канала в каталоге;
 * @param name название канала;
 * @param hasArchive у канала есть архив по заявке плейлиста;
 * @param programmeTitle передача, которая идёт сейчас (если гид её знает);
 * @param programmeDescription описание текущей передачи из гида (`null` — провайдер не дал);
 * @param programmeStart начало текущей передачи (со сдвигом гида);
 * @param programmeEnd конец текущей передачи (со сдвигом гида).
 */
data class RecentChannelRef(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val hasArchive: Boolean = false,
    val programmeTitle: String? = null,
    val programmeDescription: String? = null,
    val programmeStart: Instant? = null,
    val programmeEnd: Instant? = null,
)
