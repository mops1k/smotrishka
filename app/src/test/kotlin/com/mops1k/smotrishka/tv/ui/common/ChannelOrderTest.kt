package com.mops1k.smotrishka.tv.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация единого порядка каналов.
 *
 * Один поставщик сортировки для списка каналов и для оверлея в плеере (замечание
 * Александра 10.11.2026): «Зал № 2» должен идти перед «Зал № 11» в обоих местах.
 */
class ChannelOrderTest {

    @Test
    fun `каналы с номерами идут по возрастанию`() {
        val names = listOf("Зал № 11", "Зал № 2", "Зал № 31", "Зал № 3")

        assertThat(ChannelOrder.sorted(names) { it })
            .containsExactly("Зал № 2", "Зал № 3", "Зал № 11", "Зал № 31")
            .inOrder()
    }

    @Test
    fun `название берётся из элемента, а не из самого элемента`() {
        data class Row(val title: String)

        val rows = listOf(Row("Зал № 10"), Row("Зал № 1"))

        assertThat(ChannelOrder.sorted(rows) { it.title }.map { it.title })
            .containsExactly("Зал № 1", "Зал № 10")
            .inOrder()
    }
}
