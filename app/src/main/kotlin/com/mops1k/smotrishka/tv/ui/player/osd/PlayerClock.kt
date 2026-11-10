package com.mops1k.smotrishka.tv.ui.player.osd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Формат часов в шапке плеера. */
private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Текущее время для шапки плеера: обновляется по минуте, чтобы часы не отставали.
 *
 * Общее для эфирного плеера и медиатеки: шапка одна, значит и время считается в одном месте.
 */
@Composable
fun rememberPlayerClockText(): String {
    var text by remember { mutableStateOf(LocalTime.now().format(CLOCK_FORMAT)) }
    LaunchedEffect(Unit) {
        while (true) {
            text = LocalTime.now().format(CLOCK_FORMAT)
            val millisToNextMinute = 60_000L - System.currentTimeMillis() % 60_000L
            delay(millisToNextMinute.coerceIn(1_000L, 60_000L))
        }
    }
    return text
}
