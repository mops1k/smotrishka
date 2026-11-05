package com.mops1k.smotrishka.tv.core.data

import androidx.room.Entity

/**
 * Передача в базе программы передач.
 *
 * Ключ — канал плюс время начала: одна и та же передача не может начинаться дважды,
 * а повторная загрузка EPG должна заменять запись, а не дублировать её.
 *
 * @param channelId идентификатор канала EPG;
 * @param startAt начало в epoch-миллисекундах;
 * @param endAt конец в epoch-миллисекундах;
 * @param title название;
 * @param subTitle подзаголовок;
 * @param description описание;
 * @param catchupId идентификатор для архивного запроса;
 * @param iconUrl кадр передачи.
 */
@Entity(tableName = "programmes", primaryKeys = ["channelId", "startAt"])
data class ProgrammeEntity(
    val channelId: String,
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val subTitle: String?,
    val description: String?,
    val catchupId: String?,
    val iconUrl: String?,
)
