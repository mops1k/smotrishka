package com.mops1k.smotrishka.tv.ui.shell

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.R
import com.mops1k.smotrishka.tv.core.designsystem.component.LocalReduceMotion
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
 * @param narrow узкий режим: подписи скрыты, пункты центрируются;
 * @param showMediateka показывать ли пункт «Медиатека»: он есть только у проверенного портала;
 * @param modifier размеры сайдбара.
 *
 * Ширина меняется по фокусу: пока фокус вне меню, сайдбар свёрнут в иконки и не занимает
 * полезную площадь; как только фокус приходит в меню — разворачивается с подписями.
 */
@Composable
fun AppSidebar(
    current: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    modifier: Modifier = Modifier,
    narrow: Boolean = false,
    // Медиатека появляется в меню только после успешной проверки портала (решение 10.11.2026).
    showMediateka: Boolean = false,
    // Пункты меню принимают фокус только когда меню активировано кнопкой «влево»:
    // иначе фокус-движок уводил в меню при движении вверх из карточки и списка.
    focusEnabled: Boolean = true,
    // Адрес фокуса текущего пункта: по нему в меню попадает «влево» с края контента.
    menuFocusRequester: FocusRequester? = null,
    // Куда ведёт «вправо» из меню: в видимую панель контента (категории или список).
    rightFocus: FocusRequester? = null,
    // Доступно обновление: на пункте «О приложении» появляется точка акцента.
    updateAvailable: Boolean = false,
) {
    // Фокус внутри меню (на нём самом или на пункте) — разворачиваем, иначе сворачиваем.
    var focusedInside by remember { mutableStateOf(false) }
    // Адреса пунктов и текущий пункт под фокусом: «вверх»/«вниз» ходят по пунктам явно,
    // геометрический поиск внутри меню не переключал их.
    val itemRequesters = remember { ShellDestination.entries.associateWith { FocusRequester() } }
    var focusedDestination by remember { mutableStateOf<ShellDestination?>(null) }
    val visibleDestinations = ShellDestination.entries.filter {
        it != ShellDestination.MEDIATEKA || showMediateka
    }
    val collapsed = narrow || !focusedInside
    val targetWidth = if (collapsed) TvDimens.sidebarWidthNarrow else TvDimens.sidebarWidth
    // На слабых приставках ширина меняется мгновенно: анимация отнимала кадры у пульта.
    val reduceMotion = LocalReduceMotion.current
    val animatedWidth by animateDpAsState(targetValue = targetWidth, label = "sidebarWidth")
    val width = if (reduceMotion) targetWidth else animatedWidth
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
            .onFocusChanged { focusedInside = it.hasFocus }
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
            .onPreviewKeyEvent { event ->
                if (!focusEnabled || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                val index = visibleDestinations.indexOf(focusedDestination)
                val target = when (event.key) {
                    Key.DirectionDown -> visibleDestinations.getOrNull(index + 1)
                    Key.DirectionUp -> visibleDestinations.getOrNull(index - 1)
                    else -> null
                } ?: return@onPreviewKeyEvent false
                runCatching { itemRequesters[target]?.requestFocus() }
                true
            }
            .padding(
                start = if (collapsed) 10.dp else 20.dp,
                end = if (collapsed) 10.dp else 20.dp,
                top = if (collapsed) 22.dp else 31.dp,
                bottom = 25.dp,
            ),
    ) {
        BrandButton(narrow = collapsed)
        // В узком режиме отступ сверху меньше: на телефоне в альбомной ориентации по высоте
        // не помещаются бренд, четыре пункта и отступ 57 макета одновременно.
        Spacer(Modifier.size(if (collapsed) 20.dp else 57.dp))
        Column(verticalArrangement = Arrangement.spacedBy(TvDimens.menuGap)) {
            val internalRequester = remember { FocusRequester() }
            val currentRequester = menuFocusRequester ?: internalRequester
            // Ушли в меню кнопкой «влево» — фокус на текущем пункте (там, где были).
            LaunchedEffect(focusEnabled) {
                if (focusEnabled) {
                    withFrameNanos { }
                    runCatching { currentRequester.requestFocus() }
                }
            }
            visibleDestinations
                .forEach { destination ->
                MenuItem(
                    destination = destination,
                    active = destination == current,
                    narrow = collapsed,
                    focusEnabled = focusEnabled,
                    focusRequester = if (destination == current) {
                        currentRequester
                    } else {
                        itemRequesters[destination]
                    },
                    onFocused = { focused -> focusedDestination = destination.takeIf { focused } },
                    // «Вправо» из меню ведёт в видимую панель контента, а не «в никуда»:
                    // раньше первый шаг вправо попадал на скрытый элемент (замечание
                    // Александра 09.11.2026).
                    rightFocus = rightFocus,
                    badge = updateAvailable && destination == ShellDestination.ABOUT,
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
private fun BrandButton(narrow: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(TvRadius.control))
            // Бренд — только визуальная часть: ни фокуса, ни клика
            // (указание Александра 06.11.2026). Переход в каналы — через пункт меню.
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.brand_mark),
            contentDescription = null,
            modifier = Modifier
                .size(TvDimens.brandMark)
                .clip(RoundedCornerShape(9.dp)),
        )
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
    // Фокус пункта разрешён только когда меню активировано кнопкой «влево».
    focusEnabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    // Куда ведёт «вправо» из пункта меню: видимая панель контента.
    rightFocus: FocusRequester? = null,
    // Есть обновление: точка акцента на иконке.
    badge: Boolean = false,
    // Сообщает меню, что пункт получил или потерял фокус.
    onFocused: (Boolean) -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    val textColor = when {
        // Порядок как в списке каналов: фокус первым, иначе активный пункт (он же обычно
        // и есть пункт под фокусом) перекрывал оформление фокуса.
        focused -> TvColors.focusOutline
        active -> TvColors.text
        else -> TvColors.menuItem
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (narrow) 44.dp else TvDimens.menuItemMinHeight)
            .clip(shape)
            .background(
                when {
                    // Фокус — почти чёрная плашка: на тёмном сайдбаре иначе не видно,
                    // где стоит пульт.
                    focused -> Brush.linearGradient(
                        listOf(Color(0xFF05060A), Color(0xFF05060A)),
                    )

                    active -> Brush.linearGradient(
                        listOf(TvColors.menuActiveStart, TvColors.menuActiveEnd),
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
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            // Пока меню не активировано «влево», пункт не участвует в поиске фокуса.
            .focusProperties {
                canFocus = focusEnabled
                // «Вправо» — в видимую панель, иначе фокус уходил на невидимый элемент
                // и требовалось второе нажатие (замечание Александра 09.11.2026).
                if (rightFocus != null) right = rightFocus
            }
            .focusProperties {
                canFocus = focusEnabled
            }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .then(if (focused) Modifier.border(3.dp, TvColors.focusOutline, shape) else Modifier)
            .padding(horizontal = if (narrow) 0.dp else 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (narrow) Arrangement.Center else Arrangement.Start,
    ) {
        Box {
            Icon(
                imageVector = destination.icon,
                contentDescription = if (narrow) destination.title else null,
                tint = if (active) TvColors.menuIconActive else textColor,
                modifier = Modifier.size(22.dp),
            )
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(TvColors.accent),
                )
            }
        }
        if (!narrow) {
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
