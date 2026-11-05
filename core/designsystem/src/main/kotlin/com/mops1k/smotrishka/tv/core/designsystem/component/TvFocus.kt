package com.mops1k.smotrishka.tv.core.designsystem.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors

/**
 * Рамка фокуса макета: `outline 3px #d1bcff`, отступ 3, свечение `rgba(169,135,255,.18)`.
 *
 * В Compose обводка рисуется за границами элемента (`drawBehind` без клипа): так она не
 * съедает внутренний отступ и не сдвигает содержимое при получении фокуса.
 *
 * @param focused показывать ли рамку;
 * @param shape форма элемента: рамка повторяет скругление;
 * @param color цвет обводки;
 * @param thickness толщина обводки.
 */
fun Modifier.tvFocusRing(
    focused: Boolean,
    shape: RoundedCornerShape = RoundedCornerShape(13.dp),
    color: Color = TvColors.focusOutline,
    thickness: Dp = 3.dp,
): Modifier {
    if (!focused) return this
    return drawBehind {
        val stroke = thickness.toPx()
        val radius = shape.topStart.toPx(size, this) + stroke
        // Смещение наружу: обводка стоит «вокруг» элемента, как outline-offset в макете.
        val topLeft = Offset(-stroke, -stroke)
        val outlineSize = Size(size.width + stroke * 2, size.height + stroke * 2)
        drawRoundRect(
            color = TvColors.focusGlow,
            topLeft = topLeft,
            size = outlineSize,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = stroke * 2),
        )
        drawRoundRect(
            color = color,
            topLeft = topLeft,
            size = outlineSize,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = stroke),
        )
    }
}

/**
 * Запоминает состояние фокуса элемента и рисует рамку фокуса.
 *
 * Возвращает модификатор, который сам подписывается на `onFocusChanged`: вызывающему коду
 * не нужно держать состояние фокуса вручную.
 *
 * @param shape форма элемента;
 * @param enabled рисовать ли рамку (например, не рисовать на неактивных элементах).
 */
@Composable
fun Modifier.tvFocusableRing(
    shape: RoundedCornerShape = RoundedCornerShape(13.dp),
    enabled: Boolean = true,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .tvFocusRing(focused = focused && enabled, shape = shape)
}
