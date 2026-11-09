package com.mops1k.smotrishka.tv.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import java.time.Instant
import java.time.ZoneId

/**
 * Шапка контента: заголовок раздела слева, поиск и часы справа.
 *
 * Строки над заголовком (eyebrow) и аватара нет: удалены решением Александра.
 *
 * @param title заголовок раздела;
 * @param now текущий момент для часов;
 * @param zone часовой пояс пользователя;
 * @param onSearch открыть поиск, `null` — кнопки поиска нет;
 * @param narrow узкий режим: заголовок мельче, отступ снизу меньше;
 * @param modifier размеры шапки.
 */
@Composable
fun ContentHeader(
    title: String,
    now: Instant,
    zone: ZoneId,
    onSearch: (() -> Unit)?,
    narrow: Boolean = false,
    modifier: Modifier = Modifier,
    // «Влево» с кнопки поиска открывает боковое меню (кнопка — левый край контента).
    onSearchLeftEdge: (() -> Unit)? = null,
    // Адрес кнопки поиска: на неё ведёт «вверх» из карточки канала.
    searchFocusRequester: FocusRequester? = null,
    // «Вниз» с поиска ведёт на превью карточки: геометрия иногда выбирала шапку.
    searchDownFocusRequester: FocusRequester? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = if (narrow) 9.dp else TvDimens.headerBottom),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = if (narrow) TvType.h1.copy(fontSize = TvType.h1.fontSize * 0.62f) else TvType.h1,
            color = TvColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onSearch != null) {
                SearchButton(
                    onClick = onSearch,
                    narrow = narrow,
                    onLeftEdge = onSearchLeftEdge,
                    focusRequester = searchFocusRequester,
                    downTarget = searchDownFocusRequester,
                )
                Spacer(Modifier.width(15.dp))
            }
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(horizontal = 14.dp),
            ) {
                Text(
                    text = TextFormat.clock(now, zone),
                    style = if (narrow) TvType.clock.copy(fontSize = TvType.clock.fontSize * 0.72f) else TvType.clock,
                    color = TvColors.text,
                )
                Text(
                    text = TextFormat.date(now, zone),
                    style = if (narrow) TvType.date.copy(fontSize = TvType.date.fontSize * 0.8f) else TvType.date,
                    color = TvColors.muted,
                )
            }
        }
    }
}

/**
 * Кнопка поиска: 45×45, радиус 13, фон `var(--panel)`, иконка `#b2b8c3`.
 *
 * @param onClick открыть оверлей поиска.
 */
@Composable
private fun SearchButton(
    onClick: () -> Unit,
    narrow: Boolean = false,
    // Кнопка поиска — левый верхний край контента: «влево» отсюда открывает меню.
    onLeftEdge: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    // «Вниз» с поиска — на превью карточки канала.
    downTarget: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    Box(
        modifier = Modifier
            .size(if (narrow) 36.dp else TvDimens.iconButton)
            .clip(shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (downTarget != null) Modifier.focusProperties { down = downTarget } else Modifier)
            .background(if (focused) TvColors.accent else TvColors.panel)
            .border(if (focused) 3.dp else 1.dp, if (focused) TvColors.focusOutline else TvColors.line, shape)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (
                    onLeftEdge != null &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionLeft
                ) {
                    onLeftEdge()
                    true
                } else {
                    false
                }
            }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Поиск по каналам",
            tint = if (focused) Color(0xFF100A1E) else TvColors.searchIcon,
            modifier = Modifier.size(if (narrow) 17.dp else 22.dp),
        )
    }
}
