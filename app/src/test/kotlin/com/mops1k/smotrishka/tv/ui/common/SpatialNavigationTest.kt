package com.mops1k.smotrishka.tv.ui.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация единой пространственной навигации.
 *
 * Правило макета: из текущего элемента берётся ближайший в нужную сторону, вес =
 * расстояние по оси движения + 2.4 × поперечное расстояние (`docs/design/flows.md`).
 */
class SpatialNavigationTest {

    private fun rect(left: Float, top: Float, width: Float, height: Float): FocusRect =
        FocusRect(left = left, top = top, right = left + width, bottom = top + height)

    @Test
    fun `вправо выбирается ближайший элемент справа`() {
        val current = rect(left = 0f, top = 0f, width = 100f, height = 50f)
        val near = "near" to rect(left = 120f, top = 0f, width = 100f, height = 50f)
        val far = "far" to rect(left = 400f, top = 0f, width = 100f, height = 50f)

        val next = SpatialNavigation.nearest(current, FocusDirection.RIGHT, listOf(far, near))

        assertThat(next).isEqualTo("near")
    }

    @Test
    fun `элемент сбоку проигрывает элементу по прямой из-за поперечного веса`() {
        val current = rect(left = 0f, top = 100f, width = 100f, height = 50f)
        // По прямой: чуть дальше по оси, но на той же высоте.
        val aligned = "aligned" to rect(left = 160f, top = 100f, width = 100f, height = 50f)
        // Ближе по оси, но сильно выше: 2.4 × поперечное расстояние делает его хуже.
        val diagonal = "diagonal" to rect(left = 110f, top = 0f, width = 100f, height = 50f)

        val next = SpatialNavigation.nearest(current, FocusDirection.RIGHT, listOf(diagonal, aligned))

        assertThat(next).isEqualTo("aligned")
    }

    @Test
    fun `вниз выбирается элемент под текущим`() {
        val current = rect(left = 0f, top = 0f, width = 100f, height = 50f)
        val below = "below" to rect(left = 0f, top = 70f, width = 100f, height = 50f)
        val above = "above" to rect(left = 0f, top = -100f, width = 100f, height = 50f)

        val next = SpatialNavigation.nearest(current, FocusDirection.DOWN, listOf(above, below))

        assertThat(next).isEqualTo("below")
    }

    @Test
    fun `влево и вверх работают симметрично`() {
        val current = rect(left = 200f, top = 200f, width = 100f, height = 50f)
        val left = "left" to rect(left = 50f, top = 200f, width = 100f, height = 50f)
        val up = "up" to rect(left = 200f, top = 50f, width = 100f, height = 50f)

        assertThat(SpatialNavigation.nearest(current, FocusDirection.LEFT, listOf(left, up)))
            .isEqualTo("left")
        assertThat(SpatialNavigation.nearest(current, FocusDirection.UP, listOf(left, up)))
            .isEqualTo("up")
    }

    @Test
    fun `без кандидатов в нужной стороне возвращается null`() {
        val current = rect(left = 0f, top = 0f, width = 100f, height = 50f)
        val left = "left" to rect(left = -200f, top = 0f, width = 100f, height = 50f)

        assertThat(SpatialNavigation.nearest(current, FocusDirection.RIGHT, listOf(left))).isNull()
        assertThat(
            SpatialNavigation.nearest(
                current = current,
                direction = FocusDirection.RIGHT,
                candidates = emptyList<Pair<String, FocusRect>>(),
            ),
        ).isNull()
    }

    @Test
    fun `вес считается по формуле оси и поперечного расстояния`() {
        val current = rect(left = 0f, top = 0f, width = 100f, height = 50f)
        val candidate = rect(left = 130f, top = 50f, width = 100f, height = 50f)

        val weight = SpatialNavigation.weight(current, candidate, FocusDirection.RIGHT)

        // Ось: 130 - 100 = 30; поперечное: |центр 25 − центр 75| = 50; вес = 30 + 2.4 × 50 = 150.
        assertThat(weight).isWithin(0.01f).of(150f)
    }
}
