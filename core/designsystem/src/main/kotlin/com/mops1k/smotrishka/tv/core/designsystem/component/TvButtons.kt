package com.mops1k.smotrishka.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType

/**
 * Главная кнопка действия: градиент, круг с треугольником и текст.
 *
 * Одна реализация на превью канала и на панель медиатеки: стиль кнопки задаётся здесь, экраны
 * передают только подпись и действие.
 *
 * @param onClick действие кнопки;
 * @param label подпись;
 * @param compact уменьшенный вариант для узких мест;
 * @param leftTarget куда ведёт нажатие «влево» на пульте;
 * @param modifier оформление места.
 */
@Composable
fun TvWatchButton(
    onClick: () -> Unit,
    label: String = "Смотреть",
    compact: Boolean = false,
    leftTarget: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 38.dp else TvDimens.watchButtonMinHeight)
            .clip(shape)
            .background(TvButtonGradient)
            .then(
                if (leftTarget != null) Modifier.focusProperties { left = leftTarget } else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape, color = TvColors.accent)
            .focusable()
            .padding(horizontal = if (compact) 6.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 20.dp else 28.dp)
                .clip(CircleShape)
                .background(Color(0x26FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compact) 12.dp else 16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = if (compact) TvType.watchButton.copy(fontSize = 10.5.sp) else TvType.watchButton,
            color = Color.White,
            maxLines = 1,
            // Подпись подбирает кегль под ширину кнопки: в узкой панели медиатеки
            // «Смотреть» иначе обрезалось.
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 20.sp),
        )
    }
}

/**
 * Второстепенная кнопка: тёмная заливка, обводка и иконки по краям.
 *
 * @param onClick действие кнопки;
 * @param label подпись;
 * @param icon иконка слева;
 * @param compact уменьшенный вариант;
 * @param leftTarget куда ведёт нажатие «влево» на пульте;
 * @param trailingIcon иконка справа, `null` — без неё;
 * @param modifier оформление места.
 */
@Composable
fun TvSecondaryButton(
    onClick: () -> Unit,
    label: String,
    icon: ImageVector? = null,
    compact: Boolean = false,
    leftTarget: FocusRequester? = null,
    trailingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.control)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 38.dp else TvDimens.programmeButtonMinHeight)
            .clip(shape)
            .border(3.dp, if (focused) TvColors.focusOutline else TvColors.line, shape)
            // Фокус — тёмная заливка с яркой обводкой: акцентная плашка сливалась с градиентом.
            .background(if (focused) Color(0xFF05060A) else Color.Transparent)
            .then(
                if (leftTarget != null) Modifier.focusProperties { left = leftTarget } else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (focused) TvColors.focusOutline else TvColors.accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = if (compact) TvType.programmeButton.copy(fontSize = 11.sp) else TvType.programmeButton,
            maxLines = 1,
            softWrap = false,
            color = if (focused) TvColors.focusOutline else TvColors.programmeButtonText,
            modifier = Modifier.weight(1f),
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = if (focused) TvColors.focusOutline else TvColors.nowLabel,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
