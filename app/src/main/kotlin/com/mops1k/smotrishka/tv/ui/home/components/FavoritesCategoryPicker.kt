package com.mops1k.smotrishka.tv.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType

/**
 * Выбор категории избранного, когда подключена медиатека.
 *
 * Избранное у каналов и у медиатеки — разные вещи с разными действиями по OK, поэтому при
 * подключённой медиатеке раздел начинается с выбора категории (решение Александра 10.11.2026).
 * Без медиатеки этот экран не показывается: сразу список каналов, как раньше.
 *
 * @param onChannels открыть избранные телеканалы;
 * @param onMediateka открыть избранное медиатеки;
 * @param channelsFocus адрес фокуса плитки «Телеканалы» — сюда фокус возвращается из списка.
 */
@Composable
fun FavoritesCategoryPicker(
    onChannels: () -> Unit,
    onMediateka: () -> Unit,
    channelsFocus: FocusRequester? = null,
) {
    // Начальный фокус — на «Телеканалах»: это первая и самая частая ветка.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { channelsFocus?.requestFocus() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(top = 6.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Заголовок раздела уже есть в шапке экрана: второй «Избранное» по центру лишний.
        Text(
            text = "Выберите категорию",
            style = TvType.caption,
            color = TvColors.muted,
        )
        Spacer(Modifier.size(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FavoritesChoiceCard(
                focusRequester = channelsFocus,
                title = "Телеканалы",
                subtitle = "Каналы, отмеченные сердцем",
                icon = Icons.Filled.Star,
                onClick = onChannels,
            )
            FavoritesChoiceCard(
                title = "Медиатека",
                subtitle = "Фильмы и сериалы из медиатеки",
                icon = Icons.Filled.VideoLibrary,
                onClick = onMediateka,
            )
        }
    }
}

/** Плитка выбора категории избранного. */
@Composable
private fun FavoritesChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.panel)
    Column(
        modifier = Modifier
            .width(260.dp)
            .height(150.dp)
            // Рамка рисуется до клипа: иначе клип срезает её наружную половину.
            .tvFocusRing(focused = focused, shape = shape)
            .clip(shape)
            .background(if (focused) TvColors.panelSoft else TvColors.panel)
            .onFocusChanged { focused = it.isFocused }
            // Второй focusable не нужен: clickable уже создаёт узел фокуса и обрабатывает OK.
            // Два узла мешали передать фокус на плитку (11.11.2026).
            .clickable { onClick() }
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) TvColors.focusOutline else TvColors.accent,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(text = title, style = TvType.panelTitle, color = TvColors.text)
        Spacer(Modifier.size(4.dp))
        Text(text = subtitle, style = TvType.caption, color = TvColors.muted)
    }
}
