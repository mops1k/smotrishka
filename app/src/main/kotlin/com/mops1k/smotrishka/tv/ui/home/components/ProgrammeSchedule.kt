package com.mops1k.smotrishka.tv.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.common.TextFormat
import com.mops1k.smotrishka.tv.ui.home.ProgrammeRowUi
import com.mops1k.smotrishka.tv.ui.home.ProgrammeStatus
import java.time.Instant
import java.time.ZoneId

/**
 * Разворот телепрограммы внутри карточки канала.
 *
 * Панель не расширяется в отдельный экран (решение Д3): список передач живёт прямо в карточке,
 * под её шапкой.
 *
 * @param schedule передачи канала;
 * @param zone часовой пояс;
 * @param now текущий момент;
 * @param onClick клик по передаче: экран решает, что делать со статусом строки.
 */
@Composable
fun ProgrammeSchedule(
    schedule: List<ProgrammeRowUi>,
    zone: ZoneId,
    now: Instant,
    onClick: (ProgrammeRowUi) -> Unit,
    // Заявка на фокус снаружи: экран сам ставит фокус на текущую передачу при открытии.
    focusRequester: FocusRequester? = null,
    // Сдвиг гида этого канала в часах: у части каналов провайдер отдаёт сетку со смещением.
    shiftHours: Int = 0,
    // Изменение сдвига на ±1 час; `null` — управление не показываем.
    onChangeShift: ((Int) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "ТЕЛЕПРОГРАММА",
                style = TvType.eyebrow,
                color = TvColors.eyebrow,
            )
            Text(
                text = TextFormat.programmes(schedule.size),
                style = TvType.caption,
                color = TvColors.nowLabel,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp)
                .height(1.dp)
                .background(TvColors.line),
        )
        // Сдвиг гида только для этого канала: подбирается, когда сетка не совпадает с эфиром.
        if (onChangeShift != null) {
            ShiftRow(shiftHours = shiftHours, onChangeShift = onChangeShift)
        }
        Spacer(Modifier.height(13.dp))
        if (schedule.isEmpty()) {
            Text(
                text = "Программы нет",
                style = TvType.rowProgramme,
                color = TvColors.nowLabel,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return@Column
        }
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        val currentIndex = schedule.indexOfFirst { it.status == ProgrammeStatus.NOW }
        val ownRequester = remember { FocusRequester() }
        val currentRequester = focusRequester ?: ownRequester

        /** Держит строку в середине списка: фокус остаётся, прокрутка идёт под ним. */
        suspend fun centerOn(index: Int) {
            if (index < 0) return
            val visible = listState.layoutInfo.visibleItemsInfo.size.takeIf { it > 0 } ?: DEFAULT_VISIBLE_ROWS
            listState.scrollToItem((index - visible / 2).coerceAtLeast(0))
        }

        // Открываем на текущей передаче в середине списка и сразу ставим на неё фокус.
        LaunchedEffect(schedule.size, currentIndex) {
            centerOn(currentIndex.coerceAtLeast(0))
            withFrameNanos { }
            runCatching { currentRequester.requestFocus() }
        }

        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(
                items = schedule,
                key = { _, row -> "${row.programme.start}_${row.programme.title}" },
            ) { index, row ->
                ScheduleRow(
                    row = row,
                    zone = zone,
                    now = now,
                    onClick = { onClick(row) },
                    focusRequester = if (index == currentIndex) currentRequester else null,
                    onFocused = { scope.launch { centerOn(index) } },
                )
            }
        }
    }
}

/**
 * Строка передачи: время в две строки, название и бейдж справа.
 *
 * @param row передача со статусом;
 * @param zone часовой пояс;
 * @param now текущий момент;
 * @param onClick клик по строке.
 */
@Composable
private fun ScheduleRow(
    row: ProgrammeRowUi,
    zone: ZoneId,
    now: Instant,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.small)
    val current = row.status == ProgrammeStatus.NOW
    // Прошедшая передача без архива — не действие, но фокус на неё ставится: иначе
    // стрелка «вверх» перескакивала через такие строки и уходила в левое меню.
    val canOpen = !(row.status == ProgrammeStatus.PAST && !row.hasArchive)
    // Порядок важен: фокус проверяется первым, иначе строка «сейчас идёт» перекрывает
    // оформление фокуса и его не видно (та же ошибка была в списке каналов).
    val background: Brush = when {
        focused -> Brush.linearGradient(
            listOf(Color(0xFF05060A), Color(0xFF05060A)),
        )

        current -> Brush.linearGradient(
            listOf(TvColors.rowSelectedStart, TvColors.rowSelectedEnd),
        )

        else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val titleColor = when (row.status) {
        ProgrammeStatus.NOW -> TvColors.text
        ProgrammeStatus.PAST -> TvColors.programmePast
        ProgrammeStatus.FUTURE -> TvColors.text
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TvDimens.scheduleRowMinHeight)
            .clip(shape)
            .background(background)
            .then(
                when {
                    focused -> Modifier.border(3.dp, TvColors.focusOutline, shape)
                    current -> Modifier.border(1.dp, TvColors.rowSelectedLine, shape)
                    else -> Modifier
                },
            )
            // onFocusChanged обязан идти ДО clickable: clickable сам создаёт узел фокуса.
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            // OK на недоступной передаче ничего не делает, но фокус по строкам ходит.
            .clickable(enabled = canOpen) { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.width(TvDimens.scheduleTimeWidth)) {
            Text(
                text = TextFormat.time(row.programme.start, zone),
                style = TvType.rowProgramme,
                color = if (current) TvColors.now else TvColors.programmePastTime,
                maxLines = 1,
            )
            Text(
                text = TextFormat.time(row.programme.end, zone),
                style = TvType.badge,
                color = TvColors.programmeEndTime,
                maxLines = 1,
            )
        }
        Text(
            text = row.programme.title,
            style = TvType.programme,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when {
            row.hasArchive -> ArchiveBadge(selected = current, days = 0)
            current -> Text(
                text = "СЕЙЧАС",
                style = TvType.badge,
                color = TvColors.now,
                maxLines = 1,
            )
        }
    }
}

/**
 * Строка настройки сдвига гида для этого канала.
 *
 * Управление стрелками: «влево» — минус час, «вправо» — плюс час; значение показывается
 * рядом и сохраняется сразу (замечание Александра 06.11.2026: сдвиг нужен не всем каналам).
 *
 * @param shiftHours текущий сдвиг, часы;
 * @param onChangeShift изменение на ±1 час.
 */
@Composable
private fun ShiftRow(shiftHours: Int, onChangeShift: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    val value = when {
        shiftHours > 0 -> "+$shiftHours ч"
        else -> "$shiftHours ч"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(shape)
            .background(if (focused) Color(0xFF05060A) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        onChangeShift(-1)
                        true
                    }
                    Key.DirectionRight -> {
                        onChangeShift(1)
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            .then(if (focused) Modifier.border(3.dp, TvColors.focusOutline, shape) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Сдвиг гида канала",
            style = TvType.caption,
            color = if (focused) TvColors.focusOutline else TvColors.nowLabel,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ShiftButton(label = "−1 ч", onClick = { onChangeShift(-1) })
            Text(
                text = value,
                style = TvType.caption,
                color = if (focused) TvColors.focusOutline else TvColors.text,
            )
            ShiftButton(label = "+1 ч", onClick = { onChangeShift(1) })
        }
    }
}

/**
 * Кнопка сдвига гида для тача.
 *
 * Пультом то же самое делает строка стрелками, но тачем стрелок нет: без кнопок сдвиг
 * канала было не настроить (замечание Александра 09.11.2026).
 *
 * @param label подпись кнопки;
 * @param onClick изменение сдвига на час.
 */
@Composable
private fun ShiftButton(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .tvFocusRing(focused = focused, shape = RoundedCornerShape(TvRadius.item))
            .clip(RoundedCornerShape(TvRadius.item))
            .background(TvColors.rowSelectedStart)
            .clickable(onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = TvType.caption, color = TvColors.text)
    }
}

/** Сколько строк передач считать видимыми, пока список не разложен. */
private const val DEFAULT_VISIBLE_ROWS = 5
