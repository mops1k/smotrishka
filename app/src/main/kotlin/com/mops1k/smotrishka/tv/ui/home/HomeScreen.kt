package com.mops1k.smotrishka.tv.ui.home

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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.epg.EpgUpdateState
import com.mops1k.smotrishka.tv.ui.player.LivePreview
import java.time.Instant
import java.time.ZoneId

/**
 * Главный экран: слева простой список текстовых строк, справа превью канала.
 *
 * Сначала показываются категории; нажатие на категорию заменяет их каналами этой категории,
 * возврат — пунктом «← Категории» или системной кнопкой «назад». Справа — живой кадр канала
 * (без звука), название, текущая передача и кнопка «Смотреть».
 *
 * Строки специально без карточек и крупных плашек: так на экран помещается больше каналов.
 * Экран рассчитан только на альбомную ориентацию.
 *
 * @param onChannelOpen открыть канал (переход в плеер);
 * @param onOpenSettings открыть настройки (смена плейлиста);
 * @param viewModel экранная модель.
 */
@Composable
fun HomeScreen(
    onChannelOpen: (ChannelId) -> Unit = {},
    onOpenProgramme: (ChannelId) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bufferSeconds by viewModel.bufferSeconds.collectAsStateWithLifecycle()
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val epgState by viewModel.epgState.collectAsStateWithLifecycle()

    // Возврат с настроек после обновления данных должен показать свежие каналы и передачу.
    // Принудительно: каталог мог смениться вне синхронизации (импорт плейлиста из файла).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh(force = true) }

    // Время на экране идёт, пока он открыт: иначе прогресс передачи «застывает».
    LaunchedEffect(Unit) {
        while (true) {
            delay(HOME_TICK_MS)
            viewModel.tick()
        }
    }

    val showChannels = (state as? HomeUiState.Content)?.showChannels == true

    // Обработчик «назад» активен только когда экран на самом деле на переднем плане: иначе
    // одно нажатие в плеере обрабатывалось и плеером, и главным экраном — возврат уходил
    // в категории вместо списка каналов.
    val lifecycleResumed by LocalLifecycleOwner.current.lifecycle
        .currentStateAsState()
    BackHandler(enabled = showChannels && lifecycleResumed.isAtLeast(Lifecycle.State.RESUMED)) {
        viewModel.showCategories()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            HomeUiState.Loading -> CenteredMessage("Загружаем каналы…")
            HomeUiState.Empty -> CenteredMessage("Каналов нет. Проверьте источник в настройках.")
            is HomeUiState.Error -> CenteredMessage(current.reason)
            is HomeUiState.Content -> Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (current.showChannels) {
                        ChannelPane(
                            channels = current.channels,
                            unstable = current.unstable,
                            archiveVerified = current.archiveVerified,
                            selectedChannelId = current.selectedChannelId,
                            now = current.now,
                            showLogos = !dataSaver,
                            onSelect = viewModel::selectChannel,
                            onOpen = onChannelOpen,
                            onOpenProgramme = onOpenProgramme,
                            onBack = viewModel::showCategories,
                        )
                    } else {
                        CategoryPane(
                            categories = current.categories,
                            selectedCategoryId = current.selectedCategoryId,
                            onSelect = viewModel::selectCategory,
                            onOpenSettings = onOpenSettings,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                PreviewPane(
                    favorites = current.favorites,
                    onToggleFavorite = viewModel::toggleFavorite,
                    channel = current.previewChannel,
                    bufferSeconds = bufferSeconds,
                    now = current.now,
                    previewEnabled = !dataSaver,
                    onWatch = { id -> onChannelOpen(id) },
                    onOpenProgramme = { id -> onOpenProgramme(id) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                }

                // Гид догружается в фоне: список каналов уже рабочий, статус не блокирует.
                // Плашка в правом верхнем углу: маленькая, информационная, с явным значком ⓘ.
                when (val epg = epgState) {
                    is EpgUpdateState.Running -> EpgStatusPlaque(
                        text = "Программа передач догружается",
                        modifier = Modifier.align(Alignment.TopEnd),
                    )

                    is EpgUpdateState.Failed -> EpgStatusPlaque(
                        text = "Программа передач: ${epg.reason}",
                        modifier = Modifier.align(Alignment.TopEnd),
                        informational = false,
                    )

                    else -> Unit
                }
            }
        }
    }
}

/**
 * Маленькая плашка в углу: сообщает о фоновой работе, ничего не блокирует и не забирает фокус.
 *
 * Информационная — с приглушённой подложкой и значком ⓘ; предупреждение об ошибке — со значком ⚠
 * и цветом ошибки.
 *
 * @param text текст сообщения;
 * @param modifier положение на экране;
 * @param informational `true` — просто сообщение, `false` — ошибка.
 */
@Composable
private fun EpgStatusPlaque(
    text: String,
    modifier: Modifier = Modifier,
    informational: Boolean = true,
) {
    val background = if (informational) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f)
    }
    val textColor = if (informational) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Row(
        modifier = modifier
            // Угол экрана занят системными строками: без безопасной зоны плашка уходит под них.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = 8.dp, end = 12.dp)
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (informational) "ⓘ" else "⚠",
            style = MaterialTheme.typography.bodySmall,
            color = if (informational) MaterialTheme.colorScheme.primary else textColor,
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

/** Левая область: список категорий строками и вход в настройки. */
@Composable
private fun CategoryPane(
    categories: List<CategoryUi>,
    selectedCategoryId: String?,
    onSelect: (String?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Категории", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onOpenSettings() }
                    .focusable()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }

        val listState = rememberLazyListState()
        val focusRequester = remember { FocusRequester() }
        // Фокус возвращается к выбранной категории один раз за появление панели: после Back
        // из списка каналов пульт раньше улетал на «Настройки» (проверено на устройстве).
        var focusRestored by remember { mutableStateOf(false) }
        val focusIndex = categories.indexOfFirst { it.id == selectedCategoryId }.takeIf { it >= 0 } ?: 0

        LaunchedEffect(focusIndex, categories.size) {
            if (focusRestored || categories.isEmpty()) return@LaunchedEffect
            listState.scrollToItem(focusIndex)
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            focusRestored = true
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            itemsIndexed(items = categories, key = { _, category -> category.id ?: "all" }) { index, category ->
                SimpleRow(
                    text = category.name,
                    trailing = null,
                    selected = category.id == selectedCategoryId,
                    onClick = { onSelect(category.id) },
                    focusRequester = if (index == focusIndex) focusRequester else null,
                )
            }
        }
    }
}

/** Левая область после выбора категории: список каналов строками и возврат к категориям. */
@Composable
private fun ChannelPane(
    channels: List<ChannelUi>,
    unstable: Set<String>,
    archiveVerified: Set<String>,
    selectedChannelId: ChannelId?,
    now: Instant,
    showLogos: Boolean,
    onSelect: (ChannelId) -> Unit,
    onOpen: (ChannelId) -> Unit,
    onOpenProgramme: (ChannelId) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "← Категории",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { onBack() }
                .focusable()
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )

        if (channels.isEmpty()) {
            Text(
                text = "В этой категории нет каналов",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
            return@Column
        }

        val listState = rememberLazyListState()
        val focusRequester = remember { FocusRequester() }
        var focusRestored by remember { mutableStateOf(false) }
        val focusIndex = channels.indexOfFirst { it.channel.id == selectedChannelId }.takeIf { it >= 0 } ?: 0

        // Список прокручивается к выбранному каналу: после возврата из плеера видно, где были.
        LaunchedEffect(selectedChannelId, channels.size) {
            val index = channels.indexOfFirst { it.channel.id == selectedChannelId }
            if (index >= 0) listState.scrollToItem(index)
        }

        // Пульт остаётся на том канале, который смотрели. Без этого после Back из плеера фокус
        // вставал на «← Категории» — проверено на устройстве 05.11.2026.
        LaunchedEffect(focusIndex, channels.size) {
            if (focusRestored) return@LaunchedEffect
            listState.scrollToItem(focusIndex)
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            focusRestored = true
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            itemsIndexed(items = channels, key = { _, item -> item.channel.id.value }) { index, item ->
                SimpleRow(
                    text = item.channel.name,
                    trailing = item.nowTitle,
                    logoUrl = if (showLogos) item.channel.logoUrl else null,
                    progress = progressOf(item.nowStart, item.nowEnd, now),
                    selected = item.channel.id == selectedChannelId,
                    // Заявленный провайдером архив показываем сразу; после фоновой проверки
                    // иконка остаётся только у каналов, где архив реально отдаётся.
                    hasArchive = item.channel.archive != null &&
                        (archiveVerified.isEmpty() || item.channel.id.value in archiveVerified),
                    hasUnstable = item.channel.id.value in unstable,
                    reserveTrailing = true,
                    onClick = {
                        onSelect(item.channel.id)
                        onOpen(item.channel.id)
                    },
                    onFocused = { onSelect(item.channel.id) },
                    onShowProgramme = { onOpenProgramme(item.channel.id) },
                    focusRequester = if (index == focusIndex) focusRequester else null,
                )
            }
        }
    }
}

/** Доля прошедшего времени передачи либо `null`, если время неизвестно. */
private fun progressOf(start: Instant?, end: Instant?, now: Instant): Float? {
    if (start == null || end == null) return null
    return ProgrammeProgress(start = start, end = end, now = now).fraction
}

/**
 * Одна строка списка: по желанию логотип, название, передача справа и полоса её прогресса.
 *
 * Логотип рисуется высотой со строку текста: он не отнимает места у списка, но канал
 * узнаётся сразу. Полоса прогресса прижата к правому краю и показывает, сколько передачи прошло.
 *
 * @param text основной текст строки;
 * @param trailing текст справа (текущая передача), `null` — нет;
 * @param logoUrl логотип канала, `null` — без логотипа;
 * @param progress доля прошедшего времени передачи, `null` — неизвестно;
 * @param selected выбранная строка подсвечивается;
 * @param onClick нажатие;
 * @param onFocused действие при получении фокуса;
 * @param focusRequester запрос фокуса для возврата на строку, `null` — фокус не запрашивается.
 */
@Composable
private fun SimpleRow(
    text: String,
    trailing: String?,
    logoUrl: String? = null,
    progress: Float? = null,
    selected: Boolean,
    hasArchive: Boolean = false,
    hasUnstable: Boolean = false,
    hasVerifiedArchive: Boolean = false,
    reserveTrailing: Boolean = false,
    onClick: () -> Unit,
    onFocused: () -> Unit = {},
    onShowProgramme: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val selectedBackground = MaterialTheme.colorScheme.primaryContainer
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) selectedBackground else Color.Transparent)
            .clickable { onClick() }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusable()
            .onFocusChanged { if (it.isFocused) onFocused() }
            // Пульт: «вправо» по каналу открывает его программу (долгое нажатие не годится —
            // на телевизоре основной способ управления пульт).
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onShowProgramme()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!logoUrl.isNullOrBlank()) {
            ChannelLogo(
                url = logoUrl,
                name = text,
                modifier = Modifier.size(ROW_LOGO_SIZE),
            )
            Spacer(Modifier.width(8.dp))
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        // Полоса прогресса и иконка архива занимают место всегда (только в списке каналов):
        // иначе строки с архивом и без него прыгают по ширине. В категориях этих мест нет.
        if (reserveTrailing || progress != null) {
            Spacer(Modifier.width(6.dp))
            ProgrammeBar(
                fraction = progress ?: 0f,
                modifier = Modifier
                    .width(ROW_PROGRESS_WIDTH)
                    .height(ROW_PROGRESS_HEIGHT),
                trackColor = secondaryColor.copy(alpha = if (progress == null) 0.15f else 0.35f),
                color = MaterialTheme.colorScheme.primary,
                fillFromBottom = true,
            )
        }
        if (hasUnstable) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Нестабильный поток",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(ROW_ARCHIVE_ICON_SIZE),
            )
            Spacer(Modifier.width(4.dp))
        }

        if (reserveTrailing || hasArchive) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = if (hasArchive) "Доступен архив" else null,
                tint = secondaryColor,
                modifier = Modifier
                    .size(ROW_ARCHIVE_ICON_SIZE)
                    .alpha(if (hasArchive) 1f else 0f),
            )
        }
    }
}

/**
 * Полоса прогресса передачи: фоновая дорожка и заполненная часть.
 *
 * В строке списка полоса узкая и вертикальная — там заполнение идёт снизу вверх: по ширине
 * 3 dp ничего не видно, а высота показывает, сколько передачи уже прошло.
 *
 * @param fraction доля прошедшего времени, 0…1;
 * @param modifier размеры полосы;
 * @param trackColor цвет незаполненной части;
 * @param color цвет заполненной части;
 * @param fillFromBottom заполнять снизу вверх (для вертикальной полосы в строке).
 */
@Composable
private fun ProgrammeBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color,
    color: Color,
    fillFromBottom: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(trackColor),
        contentAlignment = if (fillFromBottom) Alignment.BottomCenter else Alignment.CenterStart,
    ) {
        if (fillFromBottom) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .background(color),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(color),
            )
        }
    }
}

/**
 * Правая область: живой кадр канала и под ним передача.
 *
 * @param channel выбранный канал, `null` — канал ещё не выбран;
 * @param bufferSeconds сколько секунд держать в буфере;
 * @param onWatch открыть канал в плеере;
 * @param modifier размеры области.
 */
@Composable
private fun PreviewPane(
    channel: ChannelUi?,
    bufferSeconds: Int,
    now: Instant,
    previewEnabled: Boolean,
    favorites: Set<String>,
    onWatch: (ChannelId) -> Unit,
    onOpenProgramme: (ChannelId) -> Unit,
    onToggleFavorite: (ChannelId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (channel == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Выберите канал",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                .clickable { onWatch(channel.channel.id) },
            contentAlignment = Alignment.Center,
        ) {
            if (previewEnabled) {
                LivePreview(
                    url = channel.channel.streamUrl,
                    bufferSeconds = bufferSeconds,
                    modifier = Modifier.fillMaxSize(),
                    fallback = {
                        ChannelLogo(
                            url = channel.channel.logoUrl,
                            name = channel.channel.name,
                            modifier = Modifier.size(PREVIEW_LOGO_SIZE),
                        )
                    },
                )
            } else {
                ChannelLogo(
                    url = channel.channel.logoUrl,
                    name = channel.channel.name,
                    modifier = Modifier.size(PREVIEW_LOGO_SIZE),
                )
            }

            // Прозрачный слой поверх видео: касание по превью открывает канал в плеере,
            // хотя сам видеоповерхностный слой забирает события.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onWatch(channel.channel.id) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!channel.channel.logoUrl.isNullOrBlank()) {
                ChannelLogo(
                    url = channel.channel.logoUrl,
                    name = channel.channel.name,
                    modifier = Modifier.size(ROW_LOGO_SIZE),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = channel.channel.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = channel.nowTitle ?: "Программы нет",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        val progress = if (channel.nowStart != null && channel.nowEnd != null) {
            ProgrammeProgress(start = channel.nowStart, end = channel.nowEnd, now = now)
        } else {
            null
        }

        if (progress != null) {
            Spacer(Modifier.height(6.dp))
            ProgrammeBar(
                fraction = progress.fraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_PROGRESS_HEIGHT),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${progress.timeRange(ZoneId.systemDefault())} · ${progress.remainingText()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        channel.nextTitle?.let { next ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Далее: $next",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onToggleFavorite(channel.channel.id) }) {
                Icon(
                    imageVector = if (channel.channel.id.value in favorites) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Filled.FavoriteBorder
                    },
                    contentDescription = if (channel.channel.id.value in favorites) {
                        "Убрать из избранного"
                    } else {
                        "В избранное"
                    },
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(onClick = { onWatch(channel.channel.id) }) { Text("Смотреть") }
            TextButton(onClick = { onOpenProgramme(channel.channel.id) }) { Text("Программа") }
        }
    }
}

/**
 * Логотип канала с заглушкой.
 *
 * Буква рисуется только когда логотипа нет или он не загрузился: иначе она просвечивала бы
 * через прозрачные PNG.
 *
 * @param url адрес логотипа, `null` — логотипа нет;
 * @param name название канала для заглушки и доступности;
 * @param modifier размеры и форма плашки.
 */
@Composable
private fun ChannelLogo(url: String?, name: String, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank() || failed) {
            Text(
                text = initialOf(name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                onState = { state -> failed = state is AsyncImagePainter.State.Error },
            )
        }
    }
}

/** Первая буква названия канала для заглушки логотипа. */
private fun initialOf(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "ТВ"

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}

private val PREVIEW_LOGO_SIZE = 140.dp

/** Высота логотипа в строке списка — под высоту шрифта. */
private val ROW_LOGO_SIZE = 20.dp

/** Полоса прогресса в строке канала: тонкая, прижата к правому краю. */
private val ROW_PROGRESS_WIDTH = 3.dp

/** Размер иконки «доступен архив» в строке канала. */
private val ROW_ARCHIVE_ICON_SIZE = 14.dp

/** Как часто обновляем время на главном экране. */
private const val HOME_TICK_MS = 20_000L
private val ROW_PROGRESS_HEIGHT = 28.dp

/** Полоса прогресса передачи под превью. */
private val PREVIEW_PROGRESS_HEIGHT = 4.dp
