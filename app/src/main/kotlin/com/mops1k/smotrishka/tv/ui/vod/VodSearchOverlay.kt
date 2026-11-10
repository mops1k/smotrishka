package com.mops1k.smotrishka.tv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mops1k.smotrishka.tv.core.designsystem.component.TvPanel
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Поиск по медиатеке: строка ввода и результаты портала.
 *
 * Окно открывается кнопкой поиска в шапке раздела и ищет только фильмы и сериалы — поиск
 * каналов остаётся в своих разделах (решение Александра 10.11.2026).
 *
 * @param onDismiss закрыть поиск;
 * @param onOpenTitle открыть карточку найденного фильма;
 * @param viewModel экранная модель поиска.
 */
@Composable
fun VodSearchOverlay(
    onDismiss: () -> Unit,
    onOpenTitle: (VodTitleId) -> Unit,
    viewModel: VodSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM_COLOR)
            .clickable { onDismiss() },
        contentAlignment = Alignment.TopCenter,
    ) {
        TvPanel(
            modifier = Modifier
                .widthIn(max = SEARCH_PANEL_WIDTH)
                .fillMaxWidth()
                .padding(top = 28.dp, start = 24.dp, end = 24.dp)
                // Панель перехватывает нажатия: клик по фону закрывает поиск, клик по панели — нет.
                .clickable(enabled = false) {},
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text("Поиск по медиатеке") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(10.dp))
                when {
                    state.loading -> Text("Ищем…", style = TvType.caption, color = TvColors.muted)
                    state.error != null -> Text(
                        text = state.error.orEmpty(),
                        style = TvType.caption,
                        color = TvColors.favorite,
                    )

                    state.isEmpty -> Text(
                        text = "Ничего не найдено",
                        style = TvType.caption,
                        color = TvColors.muted,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.results, key = { it.id.value }) { found ->
                        VodSearchRow(
                            title = found,
                            onClick = {
                                viewModel.clear()
                                onOpenTitle(found.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Строка результата: маленький постер, название и год. */
@Composable
private fun VodSearchRow(title: VodTitle, onClick: () -> Unit) {
    var focused by remember(title.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimens.channelRowMinHeight)
            .tvFocusRing(focused = focused, shape = shape)
            .clip(shape)
            .background(if (focused) TvColors.panelSoft else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(TvRadius.control))
                .background(TvColors.progressTrack),
        ) {
            if (title.gridPosterUrl != null) {
                AsyncImage(
                    model = title.gridPosterUrl,
                    contentDescription = title.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title.title,
                style = TvType.rowChannel,
                color = if (focused) TvColors.focusOutline else TvColors.rowTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val year = title.year
            if (year != null) {
                Text(text = year.toString(), style = TvType.caption, color = TvColors.muted)
            }
        }
        Spacer(Modifier.size(4.dp))
    }
}

/** Затемнение под окном поиска. */
private val SCRIM_COLOR = Color(0xCC05060A)

/** Предельная ширина окна поиска. */
private val SEARCH_PANEL_WIDTH = 720.dp
