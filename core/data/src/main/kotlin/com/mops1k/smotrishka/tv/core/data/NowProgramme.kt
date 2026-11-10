package com.mops1k.smotrishka.tv.core.data

import java.time.Instant

/**
 * Передача, которая идёт сейчас, для строки списка каналов.
 *
 * Один тип на все экраны: главный список, оверлеи «Каналы», «Недавние каналы» и OSD показывают
 * одно и то же, а выбирает передачу [EpgProvider.nowProgrammes] — окно и сдвиг гида считаются
 * в одном месте (решение 11.11.2026: оверлей каналов расходился с главным экраном).
 *
 * @param title название текущей передачи;
 * @param description описание текущей передачи, если провайдер его дал;
 * @param start начало текущей передачи со сдвигом гида канала;
 * @param end конец текущей передачи со сдвигом гида канала;
 * @param nextTitle название следующей передачи, если гид её знает.
 */
data class NowProgramme(
    val title: String,
    val description: String?,
    val start: Instant,
    val end: Instant,
    val nextTitle: String? = null,
)
