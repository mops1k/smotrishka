package com.mops1k.smotrishka.tv.ui.programme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mops1k.smotrishka.tv.core.model.ChannelId
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Как часто пересобирается список передач: передача «сейчас» должна уезжать вовремя. */
private const val PROGRAMME_TICK_MS = 60_000L

/**
 * Экран программы канала: прошедшие передачи, текущая и будущие.
 *
 * Прошедшие и текущая запускают канал (архив — в следующей версии), будущие только читаются.
 * Строки простые, без карточек: так на экран влезает больше передач.
 *
 * @param onPlay открыть канал в плеере;
 * @param onBack возврат назад (пульт и системная кнопка);
 * @param viewModel экранная модель.
 */
@Composable
fun ProgrammeScreen(
    onPlay: (ChannelId, Instant?) -> Unit,
    onBack: () -> Unit,
    viewModel: ProgrammeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            ProgrammeUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is ProgrammeUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = current.reason, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "← Назад",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onBack() }.focusable().padding(vertical = 8.dp),
                )
            }

            is ProgrammeUiState.Content -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "← Назад",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onBack() }.focusable().padding(8.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = current.channelName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (current.items.isEmpty()) {
                    Text(
                        text = "Программы нет",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                    return@Column
                }

                val listState = rememberLazyListState()
                // Передача сменяется по ходу эфира: раз в минуту пересобираем список,
                // иначе «сейчас» остаётся на передачи, которая уже закончилась.
                LaunchedEffect(current.channelId) {
                    while (true) {
                        delay(PROGRAMME_TICK_MS)
                        viewModel.refresh()
                    }
                }
                // Открываем список на передаче, которая идёт сейчас: пользователь выбирает,
                // отталкиваясь от неё, а не от начала суток.
                LaunchedEffect(current.channelId, current.items.size) {
                    val nowIndex = current.items.indexOfFirst { it.isCurrent }
                    if (nowIndex >= 0) listState.scrollToItem(nowIndex)
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(items = current.items) { item ->
                        ProgrammeRow(
                            item = item,
                            onClick = {
                                // Прошедшую передачу смотрим из архива, текущую — с её начала.
                                if (item.inArchive) onPlay(current.channelId, item.start)
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Строка передачи: время слева, название справа.
 *
 * @param item передача;
 * @param onClick открыть канал.
 */
@Composable
private fun ProgrammeRow(item: ProgrammeUi, onClick: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    val background = when {
        item.isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        item.isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        item.isPast && !item.inArchive -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        item.isPast -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(enabled = item.inArchive) { onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${formatter.format(item.start)}–${formatter.format(item.end)}",
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                fontWeight = if (item.isCurrent) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Описание — у текущей передачи: именно её обычно смотрят (замечание
            // Александра 09.11.2026). Провайдер отдаёт его не всегда.
            if (item.isCurrent && !item.description.isNullOrBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (item.isCurrent) {
            Text(
                text = "сейчас",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (item.isPast && !item.inArchive) {
            Text(
                text = "вне архива",
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
            )
        }
    }
}
