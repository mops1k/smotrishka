package com.mops1k.smotrishka.tv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodFavorite
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.ui.common.BackArrowRow
import com.mops1k.smotrishka.tv.ui.shell.AppShell
import com.mops1k.smotrishka.tv.ui.shell.ShellDestination

/**
 * Избранное медиатеки: сетка снапшотов карточек, добавленных сердцем.
 *
 * Экран открывается из раздела «Избранное», когда медиатека подключена: там сначала
 * выбирается категория — телеканалы или медиатека.
 *
 * @param onBack вернуться в «Избранное»;
 * @param onOpenTitle открыть карточку;
 * @param onWatch запустить просмотр;
 * @param onOpenSettings открыть настройки;
 * @param viewModel экранная модель.
 */
@Composable
fun VodFavoritesScreen(
    onBack: () -> Unit,
    onOpenTitle: (VodTitleId) -> Unit,
    onWatch: (VodTitleId) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: VodFavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    AppShell(
        destination = ShellDestination.FAVORITES,
        onSelect = { destination ->
            when (destination) {
                ShellDestination.FAVORITES -> Unit
                ShellDestination.SETTINGS -> onOpenSettings()
                else -> onBack()
            }
        },
        onSearch = null,
    ) {
        // Возврат к выбору категорий: «Избранное» каналов и медиатеки — соседние ветки.
        BackArrowRow(label = "Избранное медиатеки", onBack = onBack)

        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "В избранном медиатеки пока ничего нет. Добавьте фильм сердцем на карточке.",
                    style = TvType.panelTitle,
                    color = TvColors.muted,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 124.dp),
                modifier = Modifier.fillMaxSize().padding(top = TvDimens.headerBottom),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                // Отступ сверху: кольцо фокуса рисуется вокруг карточки и срезается
                // краем сетки у первого ряда.
                contentPadding = PaddingValues(start = 4.dp, top = 6.dp, bottom = 16.dp),
            ) {
                items(favorites, key = { it.title.id.value }) { favorite ->
                    VodFavoriteCard(
                        favorite = favorite,
                        onOpen = { onOpenTitle(favorite.title.id) },
                        onWatch = { onWatch(favorite.title.id) },
                    )
                }
            }
        }
    }
}

/** Карточка избранного: постер, название и год. */
@Composable
private fun VodFavoriteCard(
    favorite: VodFavorite,
    onOpen: () -> Unit,
    onWatch: () -> Unit,
) {
    var focused by remember(favorite.title.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.control)
    Column(
        modifier = Modifier
            .tvFocusRing(focused = focused, shape = shape)
            .clip(shape)
            .background(if (focused) TvColors.panelSoft else TvColors.panel)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onOpen() }
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(TvRadius.control))
                .background(TvColors.progressTrack),
        ) {
            val poster = favorite.title.gridPosterUrl
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = favorite.title.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = favorite.title.title,
            style = TvType.caption,
            color = if (focused) TvColors.text else TvColors.rowTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val year = favorite.title.year
        if (year != null) {
            Text(text = year.toString(), style = TvType.badge, color = TvColors.muted)
        }
    }
}
