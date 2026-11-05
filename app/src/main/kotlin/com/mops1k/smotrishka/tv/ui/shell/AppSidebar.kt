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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType

/**
 * Сайдбар: бренд, четыре пункта меню, без футера (профиль и «Онлайн» удалены решением
 * Александра и не возвращаются).
 *
 * @param current текущий раздел;
 * @param onSelect выбор раздела;
 * @param onBrandClick возврат в «Телеканалы»;
 * @param narrow узкий режим: подписи скрыты, пункты центрируются;
 * @param modifier размеры сайдбара.
 */
@Composable
fun AppSidebar(
    current: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    onBrandClick: () -> Unit,
    modifier: Modifier = Modifier,
    narrow: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(if (narrow) TvDimens.sidebarWidthNarrow else TvDimens.sidebarWidth)
            .background(TvColors.bg.copy(alpha = 0.88f))
            .drawBehind {
                // Бордер справа: одна линия по краю сайдбара.
                drawLine(
                    color = TvColors.line,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(
                start = if (narrow) 10.dp else 20.dp,
                end = if (narrow) 10.dp else 20.dp,
                top = if (narrow) 22.dp else 31.dp,
                bottom = 25.dp,
            ),
    ) {
        BrandButton(onClick = onBrandClick, narrow = narrow)
        // В узком режиме отступ сверху меньше: на телефоне в альбомной ориентации по высоте
        // не помещаются бренд, четыре пункта и отступ 57 макета одновременно.
        Spacer(Modifier.size(if (narrow) 20.dp else 57.dp))
        Column(verticalArrangement = Arrangement.spacedBy(TvDimens.menuGap)) {
            ShellDestination.entries.forEach { destination ->
                MenuItem(
                    destination = destination,
                    active = destination == current,
                    narrow = narrow,
                    onClick = { onSelect(destination) },
                )
            }
        }
    }
}

/**
 * Кнопка бренда: знак 30 dp с рамкой акцента, повёрнутый на -8°, и название.
 *
 * @param onClick возврат в «Телеканалы»;
 * @param narrow узкий режим: остаётся только знак.
 */
@Composable
private fun BrandButton(onClick: () -> Unit, narrow: Boolean) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(TvRadius.control))
            .background(if (focused) TvColors.rowHover else Color.Transparent)
            .clickable { onClick() }
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused = focused, shape = RoundedCornerShape(TvRadius.control))
            .focusable()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(TvDimens.brandMark)
                .rotate(-8f)
                .border(2.dp, TvColors.accent, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(TvColors.accent),
            )
        }
        if (!narrow) {
            Spacer(Modifier.width(11.dp))
            Text(text = "Смотришка", style = TvType.brand, color = TvColors.text)
        }
    }
}

/**
 * Пункт меню: min-height 52, радиус 13, иконка 22 и текст 14/600.
 *
 * @param destination раздел;
 * @param active выбранный пункт: градиент и полоса 3 dp слева;
 * @param narrow узкий режим: только иконка;
 * @param onClick выбор раздела.
 */
@Composable
private fun MenuItem(
    destination: ShellDestination,
    active: Boolean,
    narrow: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    val textColor = when {
        active -> TvColors.text
        focused -> TvColors.text
        else -> TvColors.menuItem
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (narrow) 44.dp else TvDimens.menuItemMinHeight)
            .clip(shape)
            .background(
                when {
                    active -> Brush.linearGradient(
                        listOf(TvColors.menuActiveStart, TvColors.menuActiveEnd),
                    )

                    focused -> Brush.linearGradient(
                        listOf(TvColors.rowHover, TvColors.rowHover),
                    )

                    else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .drawBehind {
                if (active) {
                    // Вставка слева 3 dp: inset 3px 0 var(--accent) макета.
                    drawRect(
                        color = TvColors.accent,
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height),
                    )
                }
            }
            .clickable { onClick() }
            .onFocusChanged { focused = it.isFocused }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = if (narrow) 0.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (narrow) Arrangement.Center else Arrangement.Start,
    ) {
        if (narrow) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.title,
                    tint = if (active) TvColors.menuIconActive else textColor,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Icon(
                imageVector = destination.icon,
                contentDescription = null,
                tint = if (active) TvColors.menuIconActive else textColor,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = destination.title,
                style = TvType.menuItem,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
