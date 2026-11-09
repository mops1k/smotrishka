package com.mops1k.smotrishka.tv.core.data

/**
 * Время начала передачи и её название: строка для сверки сеток двух гидов.
 *
 * @param startAt начало в epoch-миллисекундах;
 * @param title название передачи.
 */
data class ProgrammeStartRow(
    val startAt: Long,
    val title: String,
)
