package com.mops1k.smotrishka.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens

/**
 * Общая подложка оверлеев: фон, прозрачность и крестик закрытия задаются здесь.
 *
 * Панели «Каналы», «Недавние» и «Телепрограмма» отличаются только заголовком и содержимым,
 * а выглядят и закрываются одинаково: прозрачность и кнопка закрытия меняются в одном месте
 * (решения Александра 10.11.2026 и 11.11.2026). Меню управления плеером подложку не использует —
 * у него своя, более прозрачная.
 *
 * @param title заголовок панели;
 * @param onClose закрытие панели: то же действие, что короткое нажатие «Назад»;
 * @param modifier модификатор панели;
 * @param content содержимое под заголовком.
 */
@Composable
fun OverlaySurface(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = OverlayStyle.PANEL_ALPHA))
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverlayCloseButton(onClick = onClose)
            Spacer(Modifier.width(HEADER_GAP))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/**
 * Кнопка закрытия панели: крестик слева от заголовка.
 *
 * Оформление фокуса то же, что у кнопок оверлеев: акцентная плашка и обводка, тёмная иконка —
 * так видно, куда встал пульт. Крестик, а не стрелка «Назад»: «влево» в плеере уже открывает
 * список каналов (решение Александра 11.11.2026).
 *
 * @param onClick закрытие панели.
 */
@Composable
private fun OverlayCloseButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(TvDimens.iconButton)
            .clip(CircleShape)
            .background(if (focused) TvColors.accent else TvColors.panel)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) TvColors.focusOutline else TvColors.line,
                CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Закрыть",
            tint = if (focused) FOCUS_INK else TvColors.searchIcon,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Зазор между крестиком и заголовком панели: 13 из макета. */
private val HEADER_GAP = 13.dp

/** Тёмные чернила на акцентной плашке: крестик под фокусом должен читаться сразу. */
private val FOCUS_INK = Color(0xFF100A1E)
