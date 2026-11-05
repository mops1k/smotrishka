package com.mops1k.smotrishka.tv.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors

/**
 * Тонкий скроллбар списка: полоса 3 dp справа, цвет `#353a47` на прозрачном.
 *
 * Собственный индикатор нужен потому, что у `LazyColumn` на ТВ нет визуальной полосы
 * прокрутки, а в списке тысячи каналов — без неё непонятно, где находишься.
 *
 * @param state состояние списка;
 * @param modifier положение индикатора (обычно выровнен по правому краю панели);
 * @param color цвет ползунка;
 * @param width ширина полосы.
 */
@Composable
fun TvScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    color: Color = TvColors.scrollbar,
    width: Dp = 3.dp,
) {
    val layoutInfo by remember(state) { derivedStateOf { state.layoutInfo } }
    val total = layoutInfo.totalItemsCount
    if (total == 0) return

    val visible = layoutInfo.visibleItemsInfo
    val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
    if (viewport <= 0f || visible.isEmpty()) return

    // Высота содержимого оценивается по средней высоте видимых строк: у строк канала она
    // одинаковая, поэтому оценка точная и не требует обхода всего списка.
    val averageHeight = visible.sumOf { it.size }.toFloat() / visible.size
    val contentHeight = averageHeight * total
    val thumbFraction = (viewport / contentHeight).coerceIn(0.06f, 1f)
    val firstIndex = visible.first().index
    val scrollFraction = (firstIndex.toFloat() / total).coerceIn(0f, 1f)

    Box(modifier = modifier.width(width).fillMaxHeight()) {
        Canvas(modifier = Modifier.fillMaxHeight().width(width)) {
            val thumbHeight = (size.height * thumbFraction).coerceAtLeast(24.dp.toPx())
            val maxOffset = (size.height - thumbHeight).coerceAtLeast(0f)
            val top = maxOffset * scrollFraction
            drawRoundRect(
                color = color,
                topLeft = Offset(0f, top),
                size = Size(size.width, thumbHeight),
                cornerRadius = CornerRadius(size.width / 2f, size.width / 2f),
            )
        }
    }
}
