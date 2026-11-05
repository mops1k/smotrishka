package com.mops1k.smotrishka.tv.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType

/**
 * Оверлей поиска по каналам: диалог 650 dp, поле 16 sp, фокус сразу в поле.
 *
 * Доступен во всех разделах, кроме плеера; затемнение `.73` без размытия (Compose его не умеет).
 *
 * @param query текущая строка поиска;
 * @param onQueryChange ввод;
 * @param onClose закрыть поиск и сбросить запрос;
 * @param modifier размеры оверлея.
 */
@Composable
fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(TvColors.scrim)
                // Клик мимо диалога закрывает поиск.
                .clickable(onClick = onClose)
                .padding(top = 96.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val shape = RoundedCornerShape(TvRadius.dialog)
            Row(
                modifier = Modifier
                    .widthIn(max = 650.dp)
                    .fillMaxWidth(0.9f)
                    // Клик по самому диалогу не должен закрывать поиск.
                    .clickable(enabled = false) {}
                    .clip(shape)
                    .background(TvColors.dialogBg)
                    .border(1.dp, TvColors.lineStrong, shape)
                    .padding(10.dp)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onClose()
                            true
                        } else {
                            false
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                BackButton(onClick = onClose)
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = TvColors.searchIcon,
                    modifier = Modifier.size(22.dp),
                )
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TvType.field.copy(fontSize = 16.sp, color = TvColors.text),
                    cursorBrush = SolidColor(TvColors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onClose() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Название канала",
                                    style = TvType.field.copy(fontSize = 16.sp),
                                    color = Color(0xFF6E7583),
                                )
                            }
                            inner()
                        }
                    },
                )
                EscHint()
            }
        }
    }
}

/**
 * Круглая кнопка «Назад» в диалоге поиска.
 *
 * @param onClick закрыть поиск.
 */
@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(45.dp)
            .clip(CircleShape)
            .background(TvColors.panel)
            .clickable { onClick() }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Закрыть поиск",
            tint = TvColors.searchIcon,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Подпись клавиши `Esc`: 9 sp в рамке. */
@Composable
private fun EscHint() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(TvRadius.badge))
            .background(Color(0xFF222833))
            .border(1.dp, Color(0xFF343B48), RoundedCornerShape(TvRadius.badge))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(text = "Esc", style = TvType.caption, color = Color(0xFF727987))
    }
}
