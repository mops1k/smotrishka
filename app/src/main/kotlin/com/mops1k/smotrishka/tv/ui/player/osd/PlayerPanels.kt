package com.mops1k.smotrishka.tv.ui.player.osd

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.ui.common.TextFormat
import com.mops1k.smotrishka.tv.ui.home.components.ArchiveBadge
import com.mops1k.smotrishka.tv.ui.home.components.ChannelLogo
import com.mops1k.smotrishka.tv.ui.player.ChannelRef
import com.mops1k.smotrishka.tv.ui.player.RecentChannelRef
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/**
 * Оверлей «Каналы»: список каналов категории, у выбранного канала под строкой — описание
 * текущей передачи.
 *
 * @param channels каналы в порядке переключения;
 * @param currentChannelId канал, который играет: на нём фокус при открытии;
 * @param onSelect выбор канала (OK переключает канал и закрывает панель).
 */
@Composable
fun ChannelListOverlay(
    channels: List<ChannelRef>,
    currentChannelId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChannelOverlay(
        rows = channels.map { it.toPanelRow() },
        currentChannelId = currentChannelId,
        title = "Каналы",
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * Оверлей «Недавние каналы»: тот же список с передачами и архивом, что и у «Каналов».
 *
 * @param recents недавние каналы, свежие первыми;
 * @param currentChannelId канал, который играет: на нём фокус при открытии;
 * @param onSelect выбор канала (OK переключает канал и закрывает панель).
 */
@Composable
fun RecentsOverlay(
    recents: List<RecentChannelRef>,
    currentChannelId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ChannelOverlay(
        rows = recents.map { it.toPanelRow() },
        currentChannelId = currentChannelId,
        title = "Недавние каналы",
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * Строка панели в общем виде: «Каналы» и «Недавние» показывают одно и то же, но приходят
 * из разных источников — каталога и истории просмотров.
 */
private data class PanelRow(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val hasArchive: Boolean,
    val programmeTitle: String?,
    val programmeDescription: String?,
    val programmeStart: Instant?,
    val programmeEnd: Instant?,
)

/** Перевод канала оверлея в строку панели. */
private fun ChannelRef.toPanelRow() = PanelRow(
    id = id,
    name = name,
    logoUrl = logoUrl,
    hasArchive = hasArchive,
    programmeTitle = programmeTitle,
    programmeDescription = programmeDescription,
    programmeStart = programmeStart,
    programmeEnd = programmeEnd,
)

/** Перевод недавнего канала в строку панели. */
private fun RecentChannelRef.toPanelRow() = PanelRow(
    id = id,
    name = name,
    logoUrl = logoUrl,
    hasArchive = hasArchive,
    programmeTitle = programmeTitle,
    programmeDescription = programmeDescription,
    programmeStart = programmeStart,
    programmeEnd = programmeEnd,
)

/**
 * Общий оверлей: заголовок и список каналов на всю ширину панели.
 *
 * Описание передачи показывается не отдельной колонкой справа, а под выбранной строкой:
 * при делении экрана на две части правые элементы строки уезжали влево и при листании
 * скакали (замечание Александра 09.11.2026).
 *
 * @param rows строки списка;
 * @param currentChannelId играющий канал: на нём фокус при открытии;
 * @param title заголовок панели;
 * @param onSelect выбор строки;
 * @param modifier размеры панели.
 */
@Composable
private fun ChannelOverlay(
    rows: List<PanelRow>,
    currentChannelId: String,
    title: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // «Сейчас» тикает, пока панель открыта: полоса прогресса должна идти, а не застывать
    // на моменте открытия. Пятнадцати секунд хватает — полоса тонкая.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(OVERLAY_TICK_MS)
        }
    }
    val zone = remember { ZoneId.systemDefault() }
    val listState = rememberLazyListState()
    val currentFocus = remember { FocusRequester() }
    val currentIndex = rows.indexOfFirst { it.id == currentChannelId }

    LaunchedEffect(Unit) {
        // Фокус сразу на играющем канале: иначе пульт начинал бы с первой строки.
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
        withFrameNanos { }
        runCatching { currentFocus.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f))
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(items = rows, key = { it.id }) { row ->
                ChannelOverlayRow(
                    row = row,
                    current = row.id == currentChannelId,
                    now = now,
                    zone = zone,
                    onClick = { onSelect(row.id) },
                    focusRequester = if (row.id == currentChannelId) currentFocus else null,
                )
            }
        }
    }
}

/**
 * Строка канала в оверлее плеера.
 *
 * Колонки фиксированы (`38 | 1fr | 92 | 105`), как у строки каналов на главном экране:
 * пустые места резервируют ширину, поэтому метка архива и время с прогрессом стоят на одном
 * месте во всех строках и не скачут при листании. Описание передачи выбранного канала
 * показывается под строкой и растягивает только её высоту.
 *
 * @param row данные строки;
 * @param current канал играет прямо сейчас;
 * @param now текущий момент для прогресса;
 * @param zone часовой пояс;
 * @param onClick выбор строки;
 * @param focusRequester запрос фокуса при открытии панели.
 */
@Composable
private fun ChannelOverlayRow(
    row: PanelRow,
    current: Boolean,
    now: Instant,
    zone: ZoneId,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    // Тёмные чернила на акцентной плашке: строка под пультом должна читаться сразу.
    val focusInk = Color(0xFF100A1E)
    val titleColor = when {
        focused -> focusInk
        current -> TvColors.accent
        else -> Color.White
    }
    val secondaryColor = when {
        focused -> focusInk.copy(alpha = 0.75f)
        current -> TvColors.accent.copy(alpha = 0.8f)
        else -> Color.White.copy(alpha = 0.65f)
    }
    val progress = if (row.programmeStart != null && row.programmeEnd != null) {
        TextFormat.progressFraction(row.programmeStart, row.programmeEnd, now)
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) TvColors.accent else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable { onClick() }
            .focusable()
            .then(if (focused) Modifier.border(3.dp, TvColors.focusOutline, shape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(TvDimens.rowLogo), contentAlignment = Alignment.CenterStart) {
                ChannelLogo(name = row.name, url = row.logoUrl, size = 32.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    fontWeight = if (current || focused) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.programmeTitle?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Light,
                        color = secondaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // Колонка метки архива есть всегда: нет архива — пусто, но ширина занята,
            // иначе время и прогресс в соседних строках съезжали (замечание 09.11.2026).
            Row(
                modifier = Modifier.width(TvDimens.rowBadges),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (row.hasArchive) {
                    ArchiveBadge(selected = focused, days = 0)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.width(TvDimens.rowTime),
                horizontalAlignment = Alignment.End,
            ) {
                // Время и полоса появляются только там, где передача известна: пустая
                // дорожка прогресса выглядела как прогресс без данных.
                if (row.programmeEnd != null) {
                    Text(
                        text = "до ${TextFormat.time(row.programmeEnd, zone)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryColor,
                        maxLines = 1,
                    )
                    if (progress != null) {
                        Spacer(Modifier.size(4.dp))
                        ProgressStrip(progress = progress, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        // Описание — под выбранной строкой: экран не делится на две части, а правая часть
        // строки остаётся на своём месте.
        val description = row.programmeDescription?.takeIf { it.isNotBlank() }
        if (focused && description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = focusInk.copy(alpha = 0.8f),
                maxLines = DESCRIPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = TvDimens.rowLogo + 10.dp, top = 4.dp, end = 8.dp),
            )
        }
    }
}

/**
 * Полоса прогресса передачи: дорожка и заполнение по доле прошедшего времени.
 *
 * @param progress доля прошедшего времени 0…1;
 * @param modifier ширина полосы.
 */
@Composable
private fun ProgressStrip(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(TvDimens.rowProgressHeight)
            .clip(RoundedCornerShape(5.dp))
            .background(TvColors.progressTrack),
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .background(TvColors.progressFill),
            )
        }
    }
}

/** Как часто обновляется «сейчас» для прогресса, пока панель открыта. */
private const val OVERLAY_TICK_MS = 15_000L

/** Сколько строк описания помещается под строкой канала. */
private const val DESCRIPTION_MAX_LINES = 3
