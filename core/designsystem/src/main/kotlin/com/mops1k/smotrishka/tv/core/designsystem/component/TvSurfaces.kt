package com.mops1k.smotrishka.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius

/**
 * Фон приложения: `var(--bg)` и радиальное свечение `rgba(95,63,165,.12)` в точке 82 % / 7 %.
 *
 * @param modifier размеры фона;
 * @param content содержимое поверх фона.
 */
@Composable
fun TvAppBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(TvColors.bg)
            .drawBehind {
                val radius = size.maxDimension * 0.32f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x1F5F3FA5), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.07f),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(size.width * 0.82f, size.height * 0.07f),
                )
            },
    ) {
        content()
    }
}

/**
 * Панель макета: фон `rgba(17,21,30,.82)`, рамка 1 dp `var(--line)`, радиус 20.
 *
 * @param modifier размеры панели;
 * @param shape форма;
 * @param content содержимое.
 */
@Composable
fun TvPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(TvRadius.panel),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(TvColors.panel.copy(alpha = 0.82f))
            .border(1.dp, TvColors.line, shape),
    ) {
        content()
    }
}

/**
 * Стеклянная карточка панели плеера: фон `rgba(17,21,30,.84)`, рамка `.12`, тень, радиус 20.
 *
 * `backdrop-filter` в Compose недоступен: размытие заменено полупрозрачным фоном
 * (`docs/design/compose.md`, раздел «Ограничения переноса»).
 *
 * @param modifier размеры карточки;
 * @param width предельная ширина карточки (макет: `min(900px, 100%)`);
 * @param content содержимое.
 */
@Composable
fun TvGlassPanel(
    modifier: Modifier = Modifier,
    width: Dp = 900.dp,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(TvRadius.panel)
    Box(
        modifier = modifier
            .shadow(
                elevation = 30.dp,
                shape = shape,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .clip(shape)
            .background(TvColors.panel.copy(alpha = 0.84f))
            .border(1.dp, TvColors.lineStrong, shape)
            .widthIn(max = width),
    ) {
        content()
    }
}

/** Градиент главной кнопки: `#845ee5 → #9b73ef`. */
val TvButtonGradient: Brush
    get() = Brush.linearGradient(listOf(TvColors.buttonStart, TvColors.buttonEnd))

/** Градиент прогресса в карточке: `#8057e8 → #b596ff`. */
val TvCardProgressGradient: Brush
    get() = Brush.linearGradient(listOf(TvColors.cardProgressStart, TvColors.cardProgressEnd))

/** Градиент активного пункта меню: `rgba(130,89,226,.28) → .09`. */
val TvMenuActiveGradient: Brush
    get() = Brush.linearGradient(listOf(TvColors.menuActiveStart, TvColors.menuActiveEnd))

/** Градиент выбранной строки канала: `rgba(160,127,241,.16) → .04`. */
val TvRowSelectedGradient: Brush
    get() = Brush.linearGradient(listOf(TvColors.rowSelectedStart, TvColors.rowSelectedEnd))
