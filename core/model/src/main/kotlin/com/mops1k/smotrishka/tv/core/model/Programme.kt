package com.mops1k.smotrishka.tv.core.model

import java.time.Instant

/**
 * Передача в программе канала.
 *
 * @param channelId идентификатор канала в EPG;
 * @param title название передачи;
 * @param subTitle подзаголовок (номер серии, уточнение) — в реальных EPG он есть у большинства передач;
 * @param description описание, если провайдер его отдал;
 * @param start начало передачи;
 * @param end конец передачи;
 * @param catchupId идентификатор передачи для архивного запроса (`catchup-id`);
 * @param iconUrl кадр передачи.
 */
data class Programme(
    val channelId: String,
    val title: String,
    val subTitle: String? = null,
    val description: String? = null,
    val start: Instant,
    val end: Instant,
    val catchupId: String? = null,
    val iconUrl: String? = null,
)
