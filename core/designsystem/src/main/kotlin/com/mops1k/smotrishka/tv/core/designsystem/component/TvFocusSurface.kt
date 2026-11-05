package com.mops1k.smotrishka.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors

/**
 * Единое оформление фокуса для любого элемента, куда может попасть пульт.
 *
 * Даёт три признака сразу: затемнённая плашка, обводка 3 dp цветом [TvColors.focusOutline] и
 * внешнее кольцо. Это единственный признак, который надёжно читается на тёмном интерфейсе и
 * поверх видео — поэтому применяется везде: строки, меню, категории, кнопки, превью, поиск.
 *
 * @param shape форма элемента: плашка и обводка повторяют скругление;
 * @param fill заливка в фокусе; по умолчанию почти чёрная, для кнопок поверх видео — акцент.
 */
@Composable
fun Modifier.tvFocusSurface(
    shape: RoundedCornerShape,
    fill: Color = Color(0xFF05060A),
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.background(fill, shape) else Modifier)
        .then(if (focused) Modifier.border(3.dp, TvColors.focusOutline, shape) else Modifier)
        .tvFocusRing(focused = focused, shape = shape)
}
