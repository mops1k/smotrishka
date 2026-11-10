package com.mops1k.smotrishka.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType

/**
 * Сдвоенная кнопка: основная часть с действием по умолчанию и приросшая к ней стрелка.
 *
 * Стрелка — не отдельная кнопка, а продолжение основной: одна заливка, одна высота, между
 * частями только тонкий разделитель. Нажатие на стрелку раскрывает выбор (например, качество),
 * нажатие на основную часть выполняет действие по умолчанию.
 *
 * @param label подпись основной части;
 * @param onClick действие по умолчанию;
 * @param onTrailingClick раскрыть выбор;
 * @param modifier оформление места;
 * @param icon иконка основной части;
 * @param trailingIcon иконка стрелки.
 */
@Composable
fun TvSplitButton(
    label: String,
    onClick: () -> Unit,
    onTrailingClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.PlayArrow,
    trailingIcon: ImageVector = Icons.Filled.ArrowDropDown,
) {
    var focusedMain by remember { mutableStateOf(false) }
    var focusedTrailing by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    // Рамка рисуется до клипа — иначе клип срезает её наружную половину.
    val ring = tvFocusRingFor(focusedMain || focusedTrailing, shape)

    Row(
        modifier = modifier
            .then(ring)
            .clip(shape)
            .background(TvButtonGradient)
            // Высота фиксированная, ширина — по содержимому: кнопка не растягивается
            // на всю доступную ширину.
            .height(TvDimens.watchButtonMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .onFocusChanged { focusedMain = it.isFocused }
                .clickable { onClick() }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0x26FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = TvType.watchButton,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
            )
        }
        // Разделитель внутри общей плашки: части выглядят одним элементом.
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0x33FFFFFF)),
        )
        Box(
            modifier = Modifier
                .width(44.dp)
                .fillMaxHeight()
                .onFocusChanged { focusedTrailing = it.isFocused }
                .clickable { onTrailingClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = "Выбрать качество",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Рамка фокуса для группы: тот же вид, что у одиночной кнопки. */
@Composable
private fun tvFocusRingFor(focused: Boolean, shape: RoundedCornerShape): Modifier =
    Modifier.tvFocusRing(focused = focused, shape = shape, color = TvColors.accent)
