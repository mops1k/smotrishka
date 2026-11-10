package com.mops1k.smotrishka.tv.ui.player.osd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.List
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType

/**
 * Верхняя полоса OSD: назад, канал с передачей, плашка архива, кнопки и часы.
 *
 * Разметка повторяет `docs/design/player.md`: кнопки-круги 43 dp с зазором 7, название
 * канала 17/600, передача 11 полупрозрачная, часы 15/600 с `tabular-nums`.
 *
 * @param channelTitle название канала;
 * @param programmeTitle название текущей передачи, `null` — программы нет;
 * @param isArchive играет ли архивная передача;
 * @param clock время в правом верхнем углу;
 * @param favorite канал в избранном;
 * @param aspectIcon иконка текущего режима кадра;
 * @param aspectLabel подпись режима кадра для доступности;
 * @param onBack выход из плеера;
 * @param onGoLive переход из архива в эфир;
 * @param onOpenProgramme открыть панель «Телепрограмма»;
 * @param onToggleFavorite переключить избранное;
 * @param onOpenRecents открыть панель «Недавние каналы»;
 * @param onCycleAspect переключить режим кадра;
 * @param modifier размеры полосы.
 */
@Composable
fun PlayerOsdTop(
    channelTitle: String,
    programmeTitle: String?,
    isArchive: Boolean,
    clock: String,
    favorite: Boolean = false,
    aspectIcon: ImageVector? = null,
    aspectLabel: String? = null,
    onBack: () -> Unit,
    onGoLive: (() -> Unit)? = null,
    onOpenProgramme: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onOpenRecents: (() -> Unit)? = null,
    onCycleAspect: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // Фокус при открытии OSD: без него пульт остаётся на плеере, и кнопки «не реагируют».
    initialFocus: FocusRequester? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OsdCircleButton(
            icon = Icons.Filled.ArrowBack,
            contentDescription = "Назад",
            onClick = onBack,
            focusRequester = initialFocus,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (isArchive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "АРХИВ",
                        style = TvType.badge.copy(letterSpacing = 1.2.sp),
                        color = TvColors.playerArchive,
                    )
                    Spacer(Modifier.width(12.dp))
                    if (onGoLive != null) {
                        Text(
                            text = "В эфир",
                            style = TvType.rowProgramme.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onGoLive() }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Text(
                text = channelTitle,
                style = TvType.panelTitle,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!programmeTitle.isNullOrBlank()) {
                Text(
                    text = programmeTitle,
                    style = TvType.programme,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (onOpenProgramme != null) {
                OsdCircleButton(
                    icon = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Программа канала",
                    onClick = onOpenProgramme,
                )
            }
            if (onToggleFavorite != null) {
                OsdCircleButton(
                    icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Избранное",
                    onClick = onToggleFavorite,
                    tint = if (favorite) TvColors.favorite else Color.White,
                    active = favorite,
                )
            }
            if (onOpenRecents != null) {
                OsdCircleButton(
                    icon = Icons.Filled.DateRange,
                    contentDescription = "Недавние каналы",
                    onClick = onOpenRecents,
                )
            }
            if (aspectIcon != null && aspectLabel != null && onCycleAspect != null) {
                OsdCircleButton(
                    icon = aspectIcon,
                    contentDescription = aspectLabel,
                    onClick = onCycleAspect,
                )
            }
            Spacer(Modifier.width(11.dp))
            Text(
                text = clock,
                style = TvType.clock,
                color = Color.White,
            )
        }
    }
}

/**
 * Круглая кнопка OSD: 43 dp, фон `rgba(255,255,255,.07)`, рамка `.12`, иконка 22.
 *
 * @param icon иконка;
 * @param contentDescription описание для доступности;
 * @param onClick действие;
 * @param tint цвет иконки;
 * @param active активное состояние (избранное): акцентная подложка.
 */
@Composable
fun OsdCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    tint: Color = Color.White,
    active: Boolean = false,
    size: Dp = TvDimens.osdButton,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(
                when {
                    // Фокус в OSD: акцентная заливка поверх любого видео — единственный
                    // вариант, который читается и на светлом, и на тёмном кадре.
                    focused -> TvColors.accent
                    active -> TvColors.favorite.copy(alpha = 0.12f)
                    else -> Color.White.copy(alpha = 0.07f)
                },
            )
            .border(
                if (focused) 3.dp else 1.dp,
                when {
                    focused -> TvColors.focusOutline
                    active -> TvColors.favorite.copy(alpha = 0.4f)
                    else -> TvColors.lineStrong
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = RoundedCornerShape(TvRadius.item)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) Color(0xFF100A1E) else tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
