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
                text = "ТЕЛЕПРОГРАММА НА СЕГОДНЯ",
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items = schedule, key = { "${it.programme.start}_${it.programme.title}" }) { row ->
                ScheduleRow(row = row, zone = zone, now = now, onClick = { onClick(row) })
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
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.small)
    val current = row.status == ProgrammeStatus.NOW
    val background: Brush = when {
        current -> Brush.linearGradient(
            listOf(TvColors.rowSelectedStart, TvColors.rowSelectedEnd),
        )

        focused -> Brush.linearGradient(listOf(TvColors.rowHover, TvColors.rowHover))
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
                if (current) Modifier.border(1.dp, TvColors.rowSelectedLine, shape) else Modifier,
            )
            .clickable { onClick() }
            .onFocusChanged { focused = it.isFocused }
            // У прошедшей передачи без архива нет подсветки фокуса: строка не действие
            // (`docs/design/components.md`, «Вариации архива»).
            .tvFocusRing(
                focused = focused && !(row.status == ProgrammeStatus.PAST && !row.hasArchive),
                shape = shape,
            )
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
