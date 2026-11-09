package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.Programme

/**
 * Убирает дубли передач, пришедшие из разных гидов.
 *
 * Один канал каталога может получать сетку из нескольких гидов, и одна и та же передача
 * приходит несколько раз с расхождением в минуту по времени. Поэтому дубли определяются
 * не по точному времени начала, а по перекрытию: если передача начинается раньше, чем
 * закончилась предыдущая оставленная, это та же передача из другого гида.
 * В оверлее телепрограммы это выглядело как три одинаковые строки (замечание 09.11.2026).
 *
 * Из перекрывающихся остаётся самая полная (с описанием), при равенстве — первая.
 *
 * @return передачи без дублей, по возрастанию времени начала.
 */
fun List<Programme>.distinctOverlapping(): List<Programme> {
    val sorted = sortedBy { it.start }
    val result = mutableListOf<Programme>()
    var currentEnd = sorted.firstOrNull()?.start
    for (programme in sorted) {
        if (result.isEmpty() || !programme.start.isBefore(currentEnd)) {
            result.add(programme)
            currentEnd = programme.end
            continue
        }
        // Перекрытие: та же передача из другого гида. Оставляем более полное описание.
        val last = result.last()
        if ((programme.description?.length ?: 0) > (last.description?.length ?: 0)) {
            result[result.lastIndex] = programme
            currentEnd = programme.end
        }
    }
    return result
}
