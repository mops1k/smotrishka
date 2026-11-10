package com.mops1k.smotrishka.tv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mops1k.smotrishka.tv.core.designsystem.component.TvSplitButton
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.ui.player.osd.OsdCircleButton
import com.mops1k.smotrishka.tv.ui.shell.AppShell
import com.mops1k.smotrishka.tv.ui.shell.ShellDestination

/**
 * Карточка медиатеки: постер, описание, серии и избранное.
 *
 * «Смотреть» на карточке запускает фильм с сохранённого места; серия в списке запускается
 * сразу с начала. Сердце переключает избранное тем же состоянием, что и раздел «Избранное».
 *
 * @param onBack вернуться в каталог;
 * @param onWatch запустить фильм (`episodeId = null`) или конкретную серию;
 * @param onOpenSettings открыть настройки;
 * @param viewModel экранная модель.
 */
@Composable
fun VodTitleScreen(
    onBack: () -> Unit,
    onWatch: (VodTitleId, VodTitleId?, VodQuality?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: VodTitleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    AppShell(
        destination = ShellDestination.MEDIATEKA,
        onSelect = { destination ->
            when (destination) {
                ShellDestination.MEDIATEKA -> Unit
                ShellDestination.SETTINGS -> onOpenSettings()
                else -> onBack()
            }
        },
        onSearch = null,
    ) {
        when (val current = state) {
            VodTitleUiState.Loading -> VodCatalogMessage("Загружаем карточку…")

            is VodTitleUiState.Error -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = current.reason, style = TvType.panelTitle, color = TvColors.muted)
                Spacer(Modifier.size(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = viewModel::reload) { Text("Повторить") }
                    Button(onClick = onBack) { Text("Назад") }
                }
            }

            is VodTitleUiState.Content -> VodTitleCard(
                content = current,
                onWatch = onWatch,
                onToggleFavorite = viewModel::toggleFavorite,
                onBack = onBack,
            )
        }
    }
}

/** Содержимое карточки: постер слева, описание и серии справа. */
@Composable
private fun VodTitleCard(
    content: VodTitleUiState.Content,
    onWatch: (VodTitleId, VodTitleId?, VodQuality?) -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
) {
    val title = content.details.title
    Column(modifier = Modifier.fillMaxSize().padding(top = TvDimens.headerBottom)) {
        // Вторая строка шапки: назад и название фильма — под заголовком раздела.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OsdCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                onClick = onBack,
            )
            Text(
                text = title.title,
                style = TvType.h1,
                color = TvColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FavoriteButton(favorite = content.favorite, onToggle = onToggleFavorite)
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
        Box(
            modifier = Modifier
                .width(CARD_POSTER_WIDTH)
                .aspectRatio(CARD_POSTER_ASPECT)
                .clip(RoundedCornerShape(TvRadius.panel))
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

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = listOfNotNull(
                    title.year?.toString(),
                    "4K".takeIf { title.is4k },
                    "FHD".takeIf { title.isFhd },
                    "HDR".takeIf { title.isHdr },
                    title.ageLimit?.let { "$it+" },
                ).joinToString(" · "),
                style = TvType.eyebrow,
                color = TvColors.accent,
            )
            val description = title.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = TvType.caption,
                    color = TvColors.muted,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            var selectedQuality by remember { mutableStateOf<VodQuality?>(null) }
            var qualityMenuOpen by remember { mutableStateOf(false) }
            Box {
                // Сдвоенная кнопка: основная часть запускает фильм, приросшая стрелка
                // раскрывает выбор качества.
                TvSplitButton(
                    label = content.watchLabel,
                    onClick = { onWatch(title.id, null, selectedQuality) },
                    onTrailingClick = { qualityMenuOpen = true },
                )
                VodQualityMenu(
                    expanded = qualityMenuOpen,
                    qualities = content.qualities,
                    selected = selectedQuality,
                    onDismiss = { qualityMenuOpen = false },
                    // Выбор качества сразу запускает воспроизведение в нём: отдельное
                    // нажатие «Смотреть» после выбора не нужно.
                    onSelect = { quality ->
                        qualityMenuOpen = false
                        selectedQuality = quality
                        onWatch(title.id, null, quality)
                    },
                )
            }

            val episodes = content.details.episodes
            if (episodes.isNotEmpty()) {
                Text(
                    text = "Серии",
                    style = TvType.panelTitle,
                    color = TvColors.text,
                    modifier = Modifier.padding(top = 6.dp),
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(episodes, key = { it.id.value }) { episode ->
                        VodEpisodeRow(
                            episode = episode,
                            current = content.progress?.episodeId == episode.id,
                            onWatch = { onWatch(title.id, episode.id, selectedQuality) },
                        )
                    }
                }
            }
        }
        }
    }
}

/** Сердце избранного: только иконка, состояние видно по заливке и цвету. */
@Composable
private fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit) {
    OsdCircleButton(
        icon = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = if (favorite) "В избранном" else "В избранное",
        onClick = onToggle,
        tint = if (favorite) TvColors.favorite else Color.White,
        active = favorite,
    )
}

/** Строка серии: номер, название и пометка «смотрели». */
@Composable
private fun VodEpisodeRow(
    episode: VodEpisode,
    current: Boolean,
    onWatch: () -> Unit,
) {
    var focused by remember(episode.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TvDimens.menuItemMinHeight)
            .tvFocusRing(focused = focused, shape = shape)
            .clip(shape)
            .background(if (focused) TvColors.panelSoft else TvColors.panel)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onWatch() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = episode.title,
            style = TvType.rowChannel,
            color = if (focused) TvColors.focusOutline else TvColors.rowTitle,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (current) {
            Text(text = "смотрели", style = TvType.caption, color = TvColors.accent)
        }
    }
}

/** Сообщение по центру области. */
@Composable
private fun VodCatalogMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = TvType.panelTitle, color = TvColors.muted)
    }
}

/** Ширина постера в карточке. */
private val CARD_POSTER_WIDTH = 280.dp

/** Пропорция постера. */
private const val CARD_POSTER_ASPECT = 0.68f
