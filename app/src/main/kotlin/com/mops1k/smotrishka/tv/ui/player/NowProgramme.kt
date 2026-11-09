package com.mops1k.smotrishka.tv.ui.player

import java.time.Instant

/**
 * Текущая передача канала для оверлеев плеера: что идёт, про что она и до каких пор.
 *
 * Время хранится уже с поправкой на сдвиг гида канала — так же, как его показывает OSD
 * и список каналов на главном экране: прогресс строится по тем же границам, что и подпись
 * «до HH:MM», иначе полоса и время разъезжаются.
 *
 * @param title название передачи;
 * @param description описание из гида (`null` — провайдер не дал);
 * @param start начало передачи в показываемом времени;
 * @param end конец передачи в показываемом времени.
 */
data class NowProgramme(
    val title: String,
    val description: String? = null,
    val start: Instant? = null,
    val end: Instant? = null,
)
