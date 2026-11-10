package com.mops1k.smotrishka.tv.ui.player.osd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors

/** Шаг перемотки кнопками и пультом. */
const val PLAYER_SEEK_STEP_MS = 10_000L

/** Толщина дорожки в покое. */
private val SEEK_BAR_HEIGHT = 6.dp

/** Высота дорожки в фокусе: по ней видно, что перемотка доступна с пульта. */
private val SEEK_BAR_FOCUSED_HEIGHT = 12.dp

/** Высота зоны касания полосы: под палец, а не под толщину полосы. */
private val SEEK_BAR_TOUCH_HEIGHT = 48.dp

/** Размер кружка текущего момента. */
private val THUMB_SIZE = 16.dp

/** Кружок в фокусе. */
private val THUMB_FOCUSED_SIZE = 22.dp

/**
 * Полоса перемотки: касание в точку перематывает, перетаскивание — с предпросмотром.
 *
 * Общая для эфирного плеера и медиатеки: внешний вид и управление пультом не должны расходиться.
 *
 * @param fraction текущая доля проигранного, 0…1;
 * @param onSeek выбор новой доли;
 * @param onSeekBy шаг перемотки пультом (влево/вправо), миллисекунды;
 * @param onPreview предпросмотр при перетаскивании;
 * @param modifier размеры полосы.
 */
@Composable
fun PlayerSeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    onSeekBy: (Long) -> Unit = {},
    onPreview: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableStateOf(1f) }
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SEEK_BAR_TOUCH_HEIGHT)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onSeekBy(-PLAYER_SEEK_STEP_MS)
                        true
                    }

                    Key.DirectionRight -> {
                        onSeekBy(PLAYER_SEEK_STEP_MS)
                        true
                    }

                    else -> false
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset: Offset ->
                    onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                var last = fraction
                detectDragGestures(
                    onDragStart = { offset ->
                        last = (offset.x / widthPx).coerceIn(0f, 1f)
                        onPreview(last)
                    },
                    onDragEnd = { onSeek(last) },
                    onDragCancel = { onSeek(last) },
                ) { change, _ ->
                    change.consume()
                    last = (change.position.x / widthPx).coerceIn(0f, 1f)
                    onPreview(last)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val height = if (focused) SEEK_BAR_FOCUSED_HEIGHT else SEEK_BAR_HEIGHT

        // Дорожка: тонкая, но по всей ширине. В фокусе акцентная и выше — видно издалека.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (focused) TvColors.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.35f),
                )
                .then(
                    if (focused) {
                        Modifier.border(2.dp, TvColors.focusOutline, RoundedCornerShape(4.dp))
                    } else {
                        Modifier
                    },
                ),
        )
        // Заполненная часть.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(height)
                .clip(RoundedCornerShape(4.dp))
                .background(if (focused) TvColors.accent else Color.White),
        )
        // Кружок текущего момента: за него тянут полосу, как в плеерах на телефоне.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val size = if (focused) THUMB_FOCUSED_SIZE else THUMB_SIZE
                    IntOffset((fraction * widthPx - size.toPx() / 2f).toInt(), 0)
                }
                .size(if (focused) THUMB_FOCUSED_SIZE else THUMB_SIZE)
                .clip(CircleShape)
                .background(if (focused) TvColors.accent else Color.White)
                .then(
                    if (focused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier,
                ),
        )
    }
}

/**
 * Полоса «прямого эфира»: без бегунка и без перемотки — показать, что запись ещё не началась.
 *
 * @param modifier размеры полосы.
 */
@Composable
fun PlayerLiveBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SEEK_BAR_TOUCH_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEEK_BAR_HEIGHT)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.35f)),
        )
    }
}
