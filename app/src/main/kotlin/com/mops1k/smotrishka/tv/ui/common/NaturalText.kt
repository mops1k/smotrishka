package com.mops1k.smotrishka.tv.ui.common

/**
 * Естественный порядок названий: числа сравниваются как числа.
 *
 * Обычное сравнение строк ставит «Зал № 11» перед «Зал № 2», потому что сравнивает символы
 * по одному. Здесь название делится на куски — цифровые и текстовые, — и цифровые куски
 * сравниваются по значению (замечание Александра 10.11.2026).
 */
object NaturalText {

    /**
     * Компаратор названий каналов и категорий.
     *
     * Регистр не важен; при полном совпадении чисел сравнивается исходный текст, чтобы
     * порядок был устойчивым.
     */
    val COMPARATOR: Comparator<String> = Comparator { first, second ->
        compare(first = first, second = second)
    }

    /**
     * Сравнивает две строки в естественном порядке.
     *
     * @param first первая строка;
     * @param second вторая строка.
     * @return отрицательное число, ноль или положительное — как у [Comparator].
     */
    fun compare(first: String, second: String): Int {
        val left = chunks(first)
        val right = chunks(second)
        val common = minOf(left.size, right.size)
        for (index in 0 until common) {
            val a = left[index]
            val b = right[index]
            val result = when {
                a.isNumber && b.isNumber -> a.number.compareTo(b.number)
                else -> a.text.compareTo(b.text)
            }
            if (result != 0) return result
        }
        if (left.size != right.size) return left.size.compareTo(right.size)
        return first.compareTo(second)
    }

    /** Куски названия: цифры отдельно от текста. */
    private fun chunks(value: String): List<Chunk> = buildList {
        val text = StringBuilder()
        val digits = StringBuilder()
        fun flushText() {
            if (text.isNotEmpty()) {
                add(Chunk(text = text.toString().lowercase(), number = 0L, isNumber = false))
                text.clear()
            }
        }
        fun flushDigits() {
            if (digits.isNotEmpty()) {
                add(Chunk(text = digits.toString(), number = digits.toString().toLong(), isNumber = true))
                digits.clear()
            }
        }
        value.forEach { symbol ->
            if (symbol.isDigit()) {
                flushText()
                digits.append(symbol)
            } else {
                flushDigits()
                text.append(symbol)
            }
        }
        flushDigits()
        flushText()
    }

    /**
     * Кусок названия.
     *
     * @param text текстовое представление куска (в нижнем регистре для текста);
     * @param number значение, если кусок цифровой;
     * @param isNumber цифровой ли кусок.
     */
    private data class Chunk(
        val text: String,
        val number: Long,
        val isNumber: Boolean,
    )
}
