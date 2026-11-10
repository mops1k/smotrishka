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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import com.mops1k.smotrishka.tv.core.data.SearchHit
import com.mops1k.smotrishka.tv.core.data.SearchHitKind
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing

/**
 * Оверлей поиска по каналам: диалог 650 dp, поле 16 sp, фокус сразу в поле.
 *
 * Доступен во всех разделах, кроме плеера; затемнение `.73` без размытия (Compose его не умеет).
 *
 * @param query текущая строка поиска;
 * @param onQueryChange ввод;
 * @param results найденные каналы, категории и передачи;
 * @param onPick выбор результата;
 * @param onClose закрыть поиск и сбросить запрос;
 * @param onSubmit применить запрос: окно закрывается, список каналов показывает найденное;
 * @param modifier размеры оверлея.
 */
@Composable
fun SearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchHit> = emptyList(),
    onPick: (SearchHit) -> Unit = {},
    onClose: () -> Unit,
    onSubmit: () -> Unit = onClose,
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
            Column(
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
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
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
                                    text = "Канал, категория или передача",
                                    style = TvType.field.copy(fontSize = 16.sp),
                                    color = Color(0xFF6E7583),
                                )
                            }
                            inner()
                        }
                    },
                )
                SearchSubmitButton(onSubmit = onSubmit)
              }
              SearchResults(results = results, onPick = onPick)
            }
        }
    }
}

/**
 * Список найденного: категории, каналы и передачи одной лентой.
 *
 * @param results результаты поиска;
 * @param onPick выбор строки.
 */
@Composable
private fun SearchResults(results: List<SearchHit>, onPick: (SearchHit) -> Unit) {
    if (results.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(results) { hit ->
            var focused by remember { mutableStateOf(false) }
            val kind = when (hit.kind) {
                SearchHitKind.CATEGORY -> "Категория"
                SearchHitKind.CHANNEL -> "Канал"
                SearchHitKind.PROGRAMME -> "Передача"
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusRing(focused = focused, shape = RoundedCornerShape(TvRadius.control))
                    .clip(RoundedCornerShape(TvRadius.control))
                    .background(if (focused) TvColors.rowHover else Color.Transparent)
                    .onFocusChanged { focused = it.isFocused }
                    .clickable { onPick(hit) }
                    .focusable()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = kind, style = TvType.caption, color = TvColors.accent)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hit.title,
                        style = TvType.rowChannel,
                        color = TvColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (hit.subtitle.isNotEmpty()) {
                        Text(
                            text = hit.subtitle,
                            style = TvType.caption,
                            color = TvColors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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

/** Кнопка «Искать»: применяет запрос к списку каналов и закрывает окно поиска. */
@Composable
private fun SearchSubmitButton(onSubmit: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    Box(
        modifier = Modifier
            .size(45.dp)
            .clip(shape)
            .background(if (focused) TvColors.accent else TvColors.panel)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) TvColors.focusOutline else TvColors.line,
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onSubmit() }
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Искать",
            tint = if (focused) Color(0xFF100A1E) else TvColors.searchIcon,
            modifier = Modifier.size(20.dp),
        )
    }
}
