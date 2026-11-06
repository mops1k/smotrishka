package com.mops1k.smotrishka.tv.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Расчёт прокрутки списка к фокусной строке.
 */
class ListCenteringTest {

    @Test
    fun `строка в середине списка ставится в середину видимой области`() {
        // Семь видимых строк: у индекса 10 середина — это 7-я строка области.
        assertThat(ListCentering.targetIndex(focusedIndex = 10, visibleRows = 7)).isEqualTo(7)
    }

    @Test
    fun `первые строки не уводят список в отрицательную прокрутку`() {
        assertThat(ListCentering.targetIndex(focusedIndex = 0, visibleRows = 7)).isEqualTo(0)
        assertThat(ListCentering.targetIndex(focusedIndex = 2, visibleRows = 7)).isEqualTo(0)
    }

    @Test
    fun `короткий список не прокручивается`() {
        assertThat(ListCentering.targetIndex(focusedIndex = 1, visibleRows = 20)).isEqualTo(0)
        assertThat(ListCentering.targetIndex(focusedIndex = 5, visibleRows = 30)).isEqualTo(0)
    }

    @Test
    fun `неизвестная видимость считается одной строкой`() {
        assertThat(ListCentering.targetIndex(focusedIndex = 4, visibleRows = 0)).isEqualTo(4)
        assertThat(ListCentering.targetIndex(focusedIndex = 4, visibleRows = -3)).isEqualTo(4)
    }

    @Test
    fun `отрицательный индекс означает, что центрировать нечего`() {
        assertThat(ListCentering.targetIndex(focusedIndex = -1, visibleRows = 7)).isNull()
    }
}
