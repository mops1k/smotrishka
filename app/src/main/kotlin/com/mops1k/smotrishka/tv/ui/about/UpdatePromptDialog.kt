package com.mops1k.smotrishka.tv.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import kotlinx.coroutines.delay

/**
 * Диалог при запуске: «Обновить сейчас / Позже / Не спрашивать об этой версии».
 *
 * «Позже» возвращает вопрос при следующем запуске, «не спрашивать» гасит тост и маркер
 * только для этой версии; кнопка в окне About при этом остаётся.
 *
 * @param version доступная версия;
 * @param sizeBytes размер APK в байтах;
 * @param onUpdateNow скачать и установить;
 * @param onLater закрыть до следующего запуска;
 * @param onDismissVersion больше не сообщать об этой версии.
 */
@Composable
fun UpdatePromptDialog(
    version: String,
    sizeBytes: Long,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
    onDismissVersion: () -> Unit,
) {
    // Фокус ставим сами: иначе пульт остаётся на списке каналов и кнопки не реагируют.
    val updateFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(5) {
            withFrameNanos { }
            runCatching { updateFocus.requestFocus() }
            delay(40)
        }
    }

    Dialog(
        onDismissRequest = onLater,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.scrim)
                .clickable(onClick = onLater)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(TvRadius.dialog)
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .clip(shape)
                    .background(TvColors.dialogBg)
                    .border(1.dp, TvColors.lineStrong, shape)
                    .padding(22.dp),
            ) {
                Text(
                    text = "Доступно обновление",
                    style = TvType.clock,
                    color = TvColors.text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Версия $version (${sizeLabel(sizeBytes)}). Приложение заменится, " +
                        "настройки и источник сохранятся.",
                    style = TvType.rowProgramme,
                    color = TvColors.fieldLabel,
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    PromptButton(
                        text = "Обновить сейчас",
                        primary = true,
                        focusRequester = updateFocus,
                        onClick = onUpdateNow,
                    )
                    PromptButton(text = "Позже", primary = false, onClick = onLater)
                    PromptButton(
                        text = "Не спрашивать об этой версии",
                        primary = false,
                        onClick = onDismissVersion,
                    )
                }
            }
        }
    }
}

/** Размер файла словами: «≈ 9 МБ». */
private fun sizeLabel(bytes: Long): String =
    if (bytes <= 0L) "размер неизвестен" else "≈ ${bytes / 1_048_576} МБ"

/**
 * Кнопка диалога: главная — градиент, второстепенная — рамка.
 *
 * @param text подпись;
 * @param primary главная ли кнопка;
 * @param onClick действие;
 * @param focusRequester адрес фокуса, если на кнопку надо встать сразу.
 */
@Composable
private fun PromptButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.choice)
    Box(
        modifier = Modifier
            .heightIn(min = 39.dp)
            .clip(shape)
            .then(
                if (primary) {
                    Modifier.background(
                        Brush.linearGradient(listOf(TvColors.buttonStart, TvColors.buttonEnd)),
                    )
                } else {
                    Modifier.background(Color.Transparent)
                },
            )
            .border(
                width = 1.dp,
                color = if (primary) Color.Transparent else Color(0xFF484052),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TvType.rowProgramme,
            color = if (primary) Color.White else Color(0xFFC8B7F1),
            maxLines = 1,
        )
    }
}
