package com.mops1k.smotrishka.tv.ui.home.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.home.CategoryUi

/**
 * Панель категорий на месте списка каналов.
 *
 * Схема навигации прежняя и привычная пульту: из списка каналов «назад» открывает категории,
 * выбор категории снова показывает каналы. Верхняя плашка категорий убрана: два разных способа
 * выбора путали фокус.
 *
 * @param categories категории плейлиста, первой — «Все каналы»;
 * @param selectedCategoryId выбранная категория, `null` — «Все каналы»;
 * @param onSelect выбор категории.
 */
@Composable
fun CategoryPane(
    categories: List<CategoryUi>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    // Категории — левый край контента: «влево» отсюда активирует боковое меню.
    onActivateMenu: () -> Unit = {},
    // Адрес фокуса для возврата из меню по «вправо».
    rowFocusRequester: FocusRequester? = null,
    // Сообщает наверх, что фокус внутри категорий: по нему экран решает, куда ведёт «влево».
    onFocusInCategories: (Boolean) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { onFocusInCategories(it.hasFocus) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Категории", style = TvType.panelTitle, color = TvColors.text)
            Spacer(Modifier.weight(1f))
            Text(
                text = "OK — открыть, «назад» — к каналам",
                style = TvType.caption,
                color = TvColors.muted,
            )
        }
        Spacer(Modifier.height(8.dp))

        val listState = rememberLazyListState()
        val internalRequester = remember { FocusRequester() }
        val focusRequester = rowFocusRequester ?: internalRequester
        val focusIndex = categories.indexOfFirst { it.id == selectedCategoryId }.takeIf { it >= 0 } ?: 0

        // Выбранная строка держится в середине, как и в списке каналов.
        suspend fun centerOn(index: Int) {
            val visible = listState.layoutInfo.visibleItemsInfo.size.takeIf { it > 0 } ?: 5
            listState.scrollToItem((index - visible / 2).coerceAtLeast(0))
        }

        // Фокус сразу на текущей категории: иначе пульт начинал бы с первой строки.
        LaunchedEffect(focusIndex, categories.size) {
            if (categories.isEmpty()) return@LaunchedEffect
            centerOn(focusIndex)
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(items = categories, key = { _, category -> category.id ?: "all" }) { index, category ->
                CategoryPaneRow(
                    name = category.name,
                    selected = category.id == selectedCategoryId,
                    onClick = { onSelect(category.id) },
                    focusRequester = if (index == focusIndex) focusRequester else null,
                    onActivateMenu = onActivateMenu,
                )
            }
        }
    }
}

/**
 * Строка категории.
 *
 * @param name название;
 * @param selected выбрана ли категория;
 * @param onClick выбор;
 * @param focusRequester запрос фокуса для текущей категории.
 */
@Composable
private fun CategoryPaneRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    onActivateMenu: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.small)
    val background = when {
        // Фокус — почти чёрная заливка: на тёмной панели светлая плашка выбранной
        // категории и фокус сливались, пультом было не понять, где стоишь.
        focused -> Color(0xFF05060A)
        selected -> TvColors.categoryActiveBg
        else -> Color.Transparent
    }
    val textColor = when {
        focused -> TvColors.focusOutline
        selected -> TvColors.categoryActiveText
        else -> TvColors.categoryIdle
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            // Категории — левый край: «влево» отсюда передаёт управление боковому меню.
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                    onActivateMenu()
                    true
                } else {
                    false
                }
            }
            // Без clickable строка получала фокус, но OK ничего не делал.
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .then(if (focused) Modifier.border(3.dp, TvColors.focusOutline, shape) else Modifier)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = name,
            style = TvType.rowChannel,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
