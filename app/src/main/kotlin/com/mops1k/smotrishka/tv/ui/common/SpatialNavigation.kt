package com.mops1k.smotrishka.tv.ui.common

import kotlin.math.abs

/**
 * Прямоугольник элемента на экране — для расчёта направления движения фокуса.
 *
 * @param left левая граница;
 * @param top верхняя граница;
 * @param right правая граница;
 * @param bottom нижняя граница.
 */
data class FocusRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    /** Центр по горизонтали. */
    val centerX: Float get() = (left + right) / 2f

    /** Центр по вертикали. */
    val centerY: Float get() = (top + bottom) / 2f
}

/** Направление движения фокуса. */
enum class FocusDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

/**
 * Единая пространственная навигация вместо локальных решений в экранах.
 *
 * Из текущего элемента берётся ближайший в нужную сторону; вес = расстояние по оси движения
 * + 2.4 × поперечное расстояние (`docs/design/flows.md`). Поперечный вес не даёт фокусу
 * «прыгать» на соседний столбец, если в том же столбце есть элемент по прямой.
 */
object SpatialNavigation {

    /** Вес поперечного расстояния: элемент не по прямой проигрывает выровненному. */
    const val CROSS_WEIGHT = 2.4f

    /**
     * Вес кандидата: чем меньше, тем ближе он в нужную сторону.
     *
     * @param current прямоугольник текущего элемента;
     * @param candidate прямоугольник кандидата;
     * @param direction направление движения.
     */
    fun weight(current: FocusRect, candidate: FocusRect, direction: FocusDirection): Float {
        val axis = when (direction) {
            FocusDirection.RIGHT -> candidate.left - current.right
            FocusDirection.LEFT -> current.left - candidate.right
            FocusDirection.DOWN -> candidate.top - current.bottom
            FocusDirection.UP -> current.top - candidate.bottom
        }.coerceAtLeast(0f)
        val cross = when (direction) {
            FocusDirection.RIGHT, FocusDirection.LEFT -> abs(candidate.centerY - current.centerY)
            FocusDirection.DOWN, FocusDirection.UP -> abs(candidate.centerX - current.centerX)
        }
        return axis + CROSS_WEIGHT * cross
    }

    /**
     * Выбирает следующий элемент в направлении.
     *
     * @param current прямоугольник текущего элемента;
     * @param direction направление движения;
     * @param candidates кандидаты: значение и его прямоугольник;
     * @param T тип значения (индекс, идентификатор, composable-ключ).
     * @return значение ближайшего кандидата либо `null`, если в эту сторону двигаться некуда.
     */
    fun <T> nearest(
        current: FocusRect,
        direction: FocusDirection,
        candidates: List<Pair<T, FocusRect>>,
    ): T? = candidates
        .filter { (_, rect) -> isInDirection(current, rect, direction) }
        .minByOrNull { (_, rect) -> weight(current, rect, direction) }
        ?.first

    /** Лежит ли кандидат в нужной стороне от текущего элемента (по центрам). */
    private fun isInDirection(
        current: FocusRect,
        candidate: FocusRect,
        direction: FocusDirection,
    ): Boolean = when (direction) {
        FocusDirection.RIGHT -> candidate.centerX > current.centerX
        FocusDirection.LEFT -> candidate.centerX < current.centerX
        FocusDirection.DOWN -> candidate.centerY > current.centerY
        FocusDirection.UP -> candidate.centerY < current.centerY
    }
}
