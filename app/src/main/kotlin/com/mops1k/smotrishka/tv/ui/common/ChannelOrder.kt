package com.mops1k.smotrishka.tv.ui.common

/**
 * Единый порядок каналов в интерфейсе.
 *
 * И список каналов на главном экране, и оверлей списка в плеере сортируются здесь: одно место
 * на всё приложение, поэтому «Зал № 2» всегда стоит перед «Зал № 11» (решение Александра
 * 10.11.2026).
 */
object ChannelOrder {

    /**
     * Сортирует элементы по названию канала в естественном порядке.
     *
     * @param items элементы списка;
     * @param name название канала для элемента.
     */
    fun <T> sorted(items: List<T>, name: (T) -> String): List<T> =
        items.sortedWith(compareBy(NaturalText.COMPARATOR) { item -> name(item) })
}
