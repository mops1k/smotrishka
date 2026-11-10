package com.mops1k.smotrishka.tv.ui.player.osd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Размер центральной кнопки «пауза / играть». */
private val PLAY_BUTTON_SIZE = 64.dp

/** Расстояние между кнопками панели. */
private val BUTTON_GAP = 28.dp

/**
 * Ряд кнопок панели плеера: перемотка, пауза, звук и — для сериалов — соседние серии.
 *
 * Кнопки соседних серий показываются только когда серия существует: нажимать «следующую»,
 * которой нет, бессмысленно. Один ряд на оба плеера — эфир и медиатеку.
 *
 * @param playing идёт ли воспроизведение;
 * @param onSeekBack перемотка назад;
 * @param onTogglePlay пауза или продолжение;
 * @param onSeekForward перемотка вперёд;
 * @param muted выключен ли звук, `null` — кнопки звука нет;
 * @param onToggleSound переключение звука;
 * @param onPreviousEpisode предыдущая серия, `null` — кнопки нет;
 * @param onNextEpisode следующая серия, `null` — кнопки нет;
 * @param focusRequester начальный фокус панели;
 * @param modifier размеры ряда.
 */
@Composable
fun PlayerOsdButtons(
    playing: Boolean,
    onSeekBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
    muted: Boolean? = null,
    onToggleSound: (() -> Unit)? = null,
    onPreviousEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    // Начальный фокус заявляем из самой панели: снаружи заявка приходит раньше, чем кнопки
    // попадают в композицию, и фокус оставался на полосе перемотки вместо «Пауза».
    LaunchedEffect(focusRequester) {
        if (focusRequester == null) return@LaunchedEffect
        repeat(6) {
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            delay(60)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onPreviousEpisode != null) {
            OsdCircleButton(
                icon = Icons.Filled.SkipPrevious,
                contentDescription = "Предыдущая серия",
                onClick = onPreviousEpisode,
            )
        }

        // Нижние кнопки — тот же OsdCircleButton, что и в верхней панели:
        // у стандартных IconButton наше оформление фокуса не работало.
        OsdCircleButton(
            icon = Icons.Filled.Replay10,
            contentDescription = "Назад на 10 секунд",
            onClick = onSeekBack,
        )

        OsdCircleButton(
            icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Пауза" else "Играть",
            onClick = onTogglePlay,
            // Фокус при открытии OSD — на этой кнопке.
            focusRequester = focusRequester,
            size = PLAY_BUTTON_SIZE,
        )

        OsdCircleButton(
            icon = Icons.Filled.Forward10,
            contentDescription = "Вперёд на 10 секунд",
            onClick = onSeekForward,
        )

        if (onNextEpisode != null) {
            OsdCircleButton(
                icon = Icons.Filled.SkipNext,
                contentDescription = "Следующая серия",
                onClick = onNextEpisode,
            )
        }

        if (muted != null && onToggleSound != null) {
            OsdCircleButton(
                icon = if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (muted) "Включить звук" else "Выключить звук",
                onClick = onToggleSound,
            )
        }
    }
}
