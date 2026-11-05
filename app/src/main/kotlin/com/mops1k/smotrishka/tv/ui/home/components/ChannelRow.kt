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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.unit.sp
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.common.TextFormat
import com.mops1k.smotrishka.tv.ui.home.ChannelUi
import java.time.Instant
import java.time.ZoneId

/**
 * Строка канала: логотип, название с передачей, бейджи, время с прогрессом, шеврон.
 *
 * Пять колонок фиксированы (`43 | 1fr | 92 | 105 | 18`), колонка бейджей есть всегда — даже
 * пустая: иначе строки с архивом сдвигали бы время и прогресс (`docs/design/screens.md`).
 *
 * @param item канал с ближайшей передачей;
 * @param selected выбран ли канал (его карточка справа);
 * @param now текущий момент для прогресса;
 * @param zone часовой пояс;
 * @param showLogo грузить ли логотипы (экономия трафика их выключает);
 * @param hasArchive доступен ли архив у канала;
 * @param hasUnstable отмечен ли канал как нестабильный;
 * @param onClick выбор канала;
 * @param onFocused канал получил фокус — карточка переключается;
 * @param onShowProgramme «вправо» по строке открывает телепрограмму;
 * @param focusRequester запрос фокуса для возврата на строку.
 */
@Composable
fun ChannelRow(
    item: ChannelUi,
    selected: Boolean,
    now: Instant,
    zone: ZoneId,
    showLogo: Boolean,
    hasArchive: Boolean,
    hasUnstable: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    onShowProgramme: () -> Unit,
    narrow: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    val background: Brush = when {
        selected -> Brush.linearGradient(
            listOf(TvColors.rowSelectedStart, TvColors.rowSelectedEnd),
        )

        focused -> Brush.linearGradient(listOf(TvColors.rowHover, TvColors.rowHover))
        else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
    }
    val titleColor = if (selected) TvColors.text else TvColors.rowTitle
    val secondaryColor = if (selected) TvColors.now else TvColors.rowProgramme
    val timeColor = if (selected) TvColors.now else TvColors.rowIdle
    val chevronColor = if (selected) TvColors.chevronSelected else TvColors.rowIdle

    val progress = if (item.nowStart != null && item.nowEnd != null) {
        TextFormat.progressFraction(item.nowStart, item.nowEnd, now)
    } else {
        null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TvDimens.channelRowMinHeight)
            .clip(shape)
            .background(background)
            .then(
                if (selected) {
                    Modifier.border(1.dp, TvColors.rowSelectedLine, shape)
                } else {
                    Modifier
                },
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable { onClick() }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            // Пульт: «вправо» по каналу открывает его телепрограмму (основной способ управления).
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onShowProgramme()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.width(TvDimens.rowLogo), contentAlignment = Alignment.CenterStart) {
            ChannelLogo(
                name = item.channel.name,
                url = item.channel.logoUrl,
                showImage = showLogo,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.channel.name,
                style = TvType.rowChannel,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = item.nowTitle ?: "Программы нет",
                style = TvType.rowProgramme,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Колонка бейджей: в широком режиме пустая, но занимает свои 92 dp — время не съезжает.
        // В узком режиме колонка уже (78 dp), как в макете для max-width 980.
        Row(
            modifier = Modifier.width(if (narrow) 78.dp else TvDimens.rowBadges),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasUnstable) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Нестабильный поток",
                    tint = TvColors.favorite,
                    modifier = Modifier.size(12.dp),
                )
            }
            if (hasArchive) {
                ArchiveBadge(selected = selected, days = item.channel.archive?.days ?: 0)
            } else {
                Text(
                    text = "без архива",
                    style = TvType.caption.copy(fontSize = 8.5.sp),
                    color = TvColors.muted.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            }
        }

        if (true) {
            Column(
                modifier = Modifier.width(if (narrow) 40.dp else TvDimens.rowTime),
                horizontalAlignment = Alignment.End,
            ) {
                if (!narrow) {
                    Text(
                        text = item.nowEnd?.let { "до ${TextFormat.time(it, zone)}" } ?: "",
                        style = TvType.caption,
                        color = timeColor,
                        maxLines = 1,
                    )
                    Spacer(Modifier.size(7.dp))
                }
                Box(
                    modifier = Modifier
                        .width(if (narrow) 40.dp else TvDimens.rowProgressWidth)
                        .size(width = if (narrow) 40.dp else TvDimens.rowProgressWidth, height = TvDimens.rowProgressHeight)
                        .clip(RoundedCornerShape(5.dp))
                        .background(TvColors.progressTrack),
                ) {
                    if (progress != null && progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(TvColors.progressFill),
                        )
                    }
                }
            }
        }

        if (!narrow) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = chevronColor,
                modifier = Modifier.size(TvDimens.rowChevron),
            )
        }
    }
}

/**
 * Бейдж «Архив»: часы 11 dp и текст 8/700 uppercase.
 *
 * Глубина архива уходит в описание для доступности: в макете это тултип «Архив 7 дн.»,
 * на ТВ подсказка показывается тем же способом.
 *
 * @param selected строка выбрана: бейдж светлее;
 * @param days глубина архива в днях, 0 — провайдер не указал.
 */
@Composable
fun ArchiveBadge(selected: Boolean, days: Int) {
    val shape = RoundedCornerShape(TvRadius.badge)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) TvColors.badgeArchiveBg else TvColors.badgeArchiveBg)
            .border(1.dp, TvColors.badgeArchiveLine, shape)
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = if (days > 0) "Архив $days дн." else "Доступен архив",
            tint = if (selected) TvColors.badgeArchiveSelected else TvColors.badgeArchive,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = "АРХИВ",
            style = TvType.badge,
            color = if (selected) TvColors.badgeArchiveSelected else TvColors.badgeArchive,
            maxLines = 1,
        )
    }
}
