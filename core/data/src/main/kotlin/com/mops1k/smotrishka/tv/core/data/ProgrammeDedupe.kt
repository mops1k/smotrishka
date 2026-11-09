package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.Programme

/**
 * Убирает дубли передач, пришедшие из разных гидов.
 *
 * Один канал каталога может получать сетку из нескольких гидов, и одна и та же передача
 * приходит несколько раз с расхождением в минуту по времени. Поэтому дубль определяется
 * не по точному времени начала, а по **совпадению названия и перекрытию по времени**:
 * передача из другого гида с другим названием — это другая передача, её склеивать нельзя.
 * Правило одно для всех экранов (решение Александра 09.11.2026): список каналов, оверлеи
 * «Каналы»/«Недавние», оверлей и экран «Телепрограмма» получают одинаковый список.
 *
 * Дубль сравнивается со всеми уже оставленными передачами, а не только с последней:
 * в сетке бывает «Новости → Фильм внахлёст → те же Новости из другого гида», и третья
 * строка обязана склеиться с первой. Из дублей остаётся время самой ранней строки
 * (порядок строк не прыгает от источника к источнику), а описание берётся самое полное.
 *
 * @return передачи без дублей, по возрастанию времени начала.
 */
fun List<Programme>.withoutGuideDuplicates(): List<Programme> {
    val result = mutableListOf<Programme>()
    for (programme in sortedBy { it.start }) {
        val duplicateAt = result.indexOfFirst { kept ->
            kept.title == programme.title &&
                kept.start.isBefore(programme.end) &&
                programme.start.isBefore(kept.end)
        }
        if (duplicateAt < 0) {
            result.add(programme)
        } else {
            // Та же передача из другого гида: время оставляем от самой ранней строки,
            // а описание берём самое полное — иначе от источника к источнику прыгало
            // и время начала, и порядок строк.
            val kept = result[duplicateAt]
            if ((programme.description?.length ?: 0) > (kept.description?.length ?: 0)) {
                result[duplicateAt] = kept.copy(description = programme.description)
            }
        }
    }
    return result.sortedBy { it.start }
}
