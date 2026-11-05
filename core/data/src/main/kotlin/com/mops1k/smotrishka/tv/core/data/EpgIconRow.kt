package com.mops1k.smotrishka.tv.core.data

/**
 * Иконка канала гида.
 *
 * У каталога провайдера часто нет адресов логотипов, а в XMLTV они есть. Читаем их только
 * для нужных каналов, чтобы не выгружать всю таблицу гида.
 *
 * @param id идентификатор канала гида;
 * @param iconUrl адрес иконки.
 */
data class EpgIconRow(
    val id: String,
    val iconUrl: String?,
)
