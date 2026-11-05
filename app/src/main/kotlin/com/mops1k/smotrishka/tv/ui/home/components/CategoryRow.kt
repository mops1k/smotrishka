package com.mops1k.smotrishka.tv.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.home.CategoryUi

/**
 * Плашка категорий: горизонтальный ряд с прокруткой, зазор 8.
 *
 * Показывается только в разделе «Телеканалы»: в «Избранном» и «Недавних» её нет
 * (`docs/design/screens.md`).
 *
 * @param categories категории плейлиста, первой — «Все каналы»;
 * @param selectedId выбранная категория, `null` — «Все каналы»;
 * @param onSelect выбор категории.
 */
@Composable
fun CategoryRow(
    categories: List<CategoryUi>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
    ) {
        items(items = categories, key = { it.id ?: "all" }) { category ->
            CategoryChip(
                name = category.name,
                active = category.id == selectedId,
                onClick = { onSelect(category.id) },
            )
        }
    }
}

/**
 * Кнопка категории: активная — светлая плашка `#f0ecfa` с тёмным текстом.
 *
 * @param name название категории;
 * @param active выбрана ли категория;
 * @param onClick выбор.
 */
@Composable
private fun CategoryChip(
    name: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.small)
    val background = when {
        active -> TvColors.categoryActiveBg
        focused -> Color(0x0DFFFFFF)
        else -> Color.Transparent
    }
    val textColor = when {
        active -> TvColors.categoryActiveText
        focused -> TvColors.text
        else -> TvColors.categoryIdle
    }

    Box(
        modifier = Modifier
            .clip(shape)
            .then(
                if (active) {
                    Modifier.shadow(
                        elevation = 7.dp,
                        shape = shape,
                        ambientColor = TvColors.accent.copy(alpha = 0.11f),
                        spotColor = TvColors.accent.copy(alpha = 0.11f),
                    )
                } else {
                    Modifier
                },
            )
            .background(background)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = TvType.category,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Тонкая линия под плашкой категорий: `padding 2px 2px 14px` макета. */
@Composable
fun CategoryRowBottomGap() {
    Box(modifier = Modifier.padding(bottom = 14.dp))
}

/** Рамка вокруг чипа категории в фокусе — для отладки раскладки. */
@Composable
fun CategoryDebugBorder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.border(1.dp, TvColors.line, RoundedCornerShape(TvRadius.small)))
}
