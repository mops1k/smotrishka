package com.mops1k.smotrishka.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Общая подложка оверлеев: фон и прозрачность задаются здесь.
 *
 * Панели «Каналы», «Недавние» и «Телепрограмма» отличаются только заголовком и содержимым,
 * а выглядят одинаково: прозрачность меняется в одном месте (решение Александра 10.11.2026).
 * Меню управления плеером подложку не использует — у него своя, более прозрачная.
 *
 * @param title заголовок панели;
 * @param modifier модификатор панели;
 * @param content содержимое под заголовком.
 */
@Composable
fun OverlaySurface(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = OverlayStyle.PANEL_ALPHA))
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}
