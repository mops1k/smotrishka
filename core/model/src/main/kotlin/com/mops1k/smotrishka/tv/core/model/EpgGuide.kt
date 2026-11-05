package com.mops1k.smotrishka.tv.core.model

import java.time.Instant

/**
 * Гид программы передач, добавленный пользователем в настройках.
 *
 * Гиды участвуют в фоновом обновлении: программа передач дополняется из них так же,
 * как из гида провайдера, но только недостающие передачи.
 *
 * @param id стабильный идентификатор записи;
 * @param name имя источника для настроек и логов;
 * @param url адрес XMLTV (xml или xml.gz);
 * @param addedAt когда гид добавлен — по нему задаётся порядок разбора.
 */
data class EpgGuide(
    val id: String,
    val name: String,
    val url: String,
    val addedAt: Instant,
)
