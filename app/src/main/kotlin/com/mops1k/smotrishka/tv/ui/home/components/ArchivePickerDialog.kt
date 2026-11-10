package com.mops1k.smotrishka.tv.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Выбор момента архива, когда программы передач нет.
 *
 * Передачи такой канал не отдаёт, но архив у него есть: вместо списка передач показываем
 * равномерную шкалу времени назад по глубине архива, шаг — 15 минут.
 *
 * @param channelName название канала для заголовка;
 * @param zone часовой пояс для подписей;
 * @param hours глубина архива в часах (сколько назад доступно);
 * @param onPick выбранный момент;
 * @param onDismiss закрытие без выбора.
 */
@Composable
fun ArchivePickerDialog(
    channelName: String,
    zone: ZoneId,
    hours: Int,
    onPick: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val now = remember { Instant.now() }
    // Шаг 15 минут: точность достаточная, чтобы попасть в нужную передачу без списка.
    val stepMinutes = 15L
    val count = ((hours.coerceIn(1, 72) * 60) / stepMinutes).toInt()
    val moments = remember(count) {
        (0 until count).map { index -> now.minusSeconds(index * stepMinutes * 60) }
    }
    val listState = rememberLazyListState()
    val firstFocus = remember { FocusRequester() }
    // Фокус ставим сами: без этого пульт оставался на экране под диалогом.
    LaunchedEffect(Unit) {
        repeat(5) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
            delay(40)
        }
    }
    val timeFormat = remember(zone) { DateTimeFormatter.ofPattern("HH:mm").withZone(zone) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.scrim)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(TvRadius.dialog)
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .background(TvColors.panel, shape)
                    .padding(20.dp),
            ) {
                Text(
                    text = "Архив: $channelName",
                    style = TvType.panelTitle,
                    color = TvColors.text,
                )
                Text(
                    text = "Программы нет — выберите время вручную",
                    style = TvType.category,
                    color = TvColors.fieldLabel,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(moments) { index, moment ->
                        var focused by remember { mutableStateOf(false) }
                        val rowShape = RoundedCornerShape(TvRadius.item)
                        val offsetMinutes = index * stepMinutes
                        // Подпись: время и сколько назад — понятнее, чем только часы.
                        val label = if (offsetMinutes == 0L) {
                            "Сейчас · ${timeFormat.format(moment)}"
                        } else {
                            "${timeFormat.format(moment)} · ${offsetMinutes} мин назад"
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                .onFocusChanged { focused = it.isFocused }
                                .background(
                                    if (focused) TvColors.rowHover else TvColors.panel,
                                    rowShape,
                                )
                                .clickable { onPick(moment) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = label,
                                style = TvType.rowProgramme,
                                color = if (focused) TvColors.accent else TvColors.text,
                            )
                        }
                    }
                }
            }
        }
    }
}
