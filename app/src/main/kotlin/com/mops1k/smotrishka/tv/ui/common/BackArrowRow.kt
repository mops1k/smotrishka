package com.mops1k.smotrishka.tv.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.player.osd.OsdCircleButton

/**
 * Строка возврата: круглая стрелка «назад» и подпись.
 *
 * Одна реализация на «Избранное» каналов и медиатеки: возврат к выбору категорий
 * выглядит везде одинаково.
 *
 * @param label подпись справа от стрелки;
 * @param onBack возврат на предыдущий уровень.
 */
@Composable
fun BackArrowRow(
    label: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OsdCircleButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Назад",
            onClick = onBack,
        )
        Text(
            text = label,
            style = TvType.panelTitle,
            color = TvColors.text,
        )
    }
}
