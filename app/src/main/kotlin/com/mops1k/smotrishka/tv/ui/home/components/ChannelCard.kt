package com.mops1k.smotrishka.tv.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.mops1k.smotrishka.tv.core.designsystem.component.TvButtonGradient
import com.mops1k.smotrishka.tv.core.designsystem.component.TvCardProgressGradient
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.ui.home.LocalPreviewSuppressed
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.common.TextFormat
import com.mops1k.smotrishka.tv.ui.home.ChannelUi
import com.mops1k.smotrishka.tv.ui.home.ProgrammeRowUi
import com.mops1k.smotrishka.tv.ui.player.LivePreview
import java.time.Instant
import java.time.ZoneId

/**
 * Карточка выбранного канала: визуал, копия, блок «Сейчас» и кнопки.
 *
 * В развороте телепрограммы визуал сжимается, а под кнопками появляется список передач
 * (`docs/design/screens.md`, «Разворот телепрограммы»).
 *
 * @param channel канал с ближайшей передачей, `null` — карточка пуста;
 * @param categoryName категория канала для надписи над названием;
 * @param favorite канал в избранном;
 * @param now текущий момент;
 * @param zone часовой пояс;
 * @param showLogo грузить ли логотип;
 * @param programmeOpen развёрнута ли телепрограмма;
 * @param schedule передачи для разворота;
 * @param onToggleFavorite переключить избранное;
 * @param onWatch открыть канал в плеере;
 * @param onOpenProgramme развернуть телепрограмму;
 * @param onCollapseProgramme свернуть телепрограмму;
 * @param onScheduleClick клик по передаче в развороте.
 * @param narrow узкий режим (телефон/планшет): визуал ниже, копия прокручивается.
 * @param modifier размеры карточки.
 */
@Composable
fun ChannelCard(
    channel: ChannelUi?,
    // Канал, по которому идёт живое видео (подтверждён OK); тексты и кнопки — по фокусу.
    previewChannel: ChannelUi? = null,
    // Заявка на фокус в телепрограмме: экран ставит фокус на текущую передачу.
    programmeFocus: FocusRequester? = null,
    // Гид ещё читается: вместо «Программы нет» показываем многоточие.
    epgPending: Boolean = false,
    categoryName: String?,
    favorite: Boolean,
    now: Instant,
    zone: ZoneId,
    showLogo: Boolean,
    livePreview: Boolean,
    bufferSeconds: Int,
    programmeOpen: Boolean,
    schedule: List<ProgrammeRowUi>,
    onToggleFavorite: () -> Unit,
    onWatch: () -> Unit,
    onOpenProgramme: () -> Unit,
    onCollapseProgramme: () -> Unit,
    onScheduleClick: (ProgrammeRowUi) -> Unit,
    modifier: Modifier = Modifier,
    narrow: Boolean = false,
    compactCard: Boolean = false,
    // Куда уходит фокус по «влево» с кнопок карточки: строки списка каналов.
    leftTarget: FocusRequester? = null,
    // Кнопка «Архив» вместо «Программы», когда передач нет, а архив у канала есть.
    onOpenArchivePicker: (() -> Unit)? = null,
    // Сдвиг гида этого канала в часах и его изменение на ±1 час.
    epgShiftHours: Int = 0,
    onChangeEpgShift: ((Int) -> Unit)? = null,
    // «Вверх» с верхних кнопок карточки ведёт на кнопку поиска в шапке.
    upTarget: FocusRequester? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(min = if (narrow) 0.dp else TvDimens.cardPanelMinHeight)
            .clip(RoundedCornerShape(TvRadius.panel))
            .background(TvColors.panel.copy(alpha = 0.82f))
            .border(1.dp, TvColors.line, RoundedCornerShape(TvRadius.panel)),
    ) {
        if (channel == null) {
            EmptyCard()
            return@Column
        }

        // Вариант A: на телефоне карточка двухколоночная — квадрат превью слева, копия справа.
        // Под превью копия не помещается по высоте (ей нужно ~190 dp, остаётся ~100).
        val copy: @Composable () -> Unit = {

        CardHeader(
            categoryName = categoryName,
            channelName = channel.channel.name,
            favorite = favorite,
            programmeOpen = programmeOpen,
            compactCard = compactCard,
            onToggleFavorite = onToggleFavorite,
            onCollapseProgramme = onCollapseProgramme,
            leftTarget = leftTarget,
            upTarget = upTarget,
        )

        if (programmeOpen) {
            Spacer(Modifier.height(15.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TvColors.line),
            )
            ProgrammeSchedule(
                schedule = schedule,
                zone = zone,
                now = now,
                onClick = onScheduleClick,
                shiftHours = epgShiftHours,
                onChangeShift = onChangeEpgShift,
            )
        } else {
            NowBlock(channel = channel, now = now, zone = zone, epgPending = epgPending)
            if (!compactCard) {
                // «Далее» с названием следующей передачи идёт сразу под полосой текущей
                // и над кнопками (указание Александра 06.11.2026).
                Spacer(Modifier.height(6.dp))
                NextBlock(channel = channel, zone = zone)
                Spacer(Modifier.height(8.dp))
                // Кнопки стоят на одной линии: «Смотреть» первой, вторая — «Программа»,
                // а при пустой программе и наличии архива — «Архив».
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        WatchButton(onClick = onWatch, leftTarget = leftTarget)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        if (schedule.isEmpty() && onOpenArchivePicker != null) {
                            ProgrammeButton(
                                onClick = onOpenArchivePicker,
                                leftTarget = leftTarget,
                                label = "Архив",
                            )
                        } else {
                            ProgrammeButton(onClick = onOpenProgramme, leftTarget = leftTarget)
                        }
                    }
                }
            }
        }
        }

        if (compactCard) {
            // Кнопки стоят на одном уровне: «Смотреть» под превью слева, «Телепрограмма канала»
            // под копией справа — оба прижаты к низу карточки.
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(108.dp)
                        .fillMaxHeight()
                        .padding(start = 10.dp, top = 10.dp, bottom = 10.dp),
                ) {
                Box(modifier = Modifier.size(96.dp)) {
            if (!programmeOpen) ChannelVisual(
                // Визуал показывает канал, который играет (подтверждён OK).
                name = (previewChannel ?: channel).channel.name,
                logoUrl = (previewChannel ?: channel).channel.logoUrl,
                showLogo = showLogo,
                livePreview = livePreview,
                streamUrl = (previewChannel ?: channel).channel.streamUrl,
                bufferSeconds = bufferSeconds,
                compact = programmeOpen,
                narrow = narrow,
                live = channel.nowTitle != null,
                onWatch = onWatch,
            )
                }
                    Spacer(Modifier.weight(1f))
                    WatchButton(onClick = onWatch, compact = true)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) { copy() }
                    Spacer(Modifier.height(8.dp))
                    ProgrammeButton(onClick = onOpenProgramme, compact = true)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
            if (!programmeOpen) ChannelVisual(
                // Визуал показывает канал, который играет (подтверждён OK).
                name = (previewChannel ?: channel).channel.name,
                logoUrl = (previewChannel ?: channel).channel.logoUrl,
                showLogo = showLogo,
                livePreview = livePreview,
                streamUrl = (previewChannel ?: channel).channel.streamUrl,
                bufferSeconds = bufferSeconds,
                compact = programmeOpen,
                narrow = narrow,
                live = channel.nowTitle != null,
                onWatch = onWatch,
            )
                Column(
                    modifier = if (programmeOpen) {
                        Modifier.padding(start = 21.dp, end = 21.dp, top = 19.dp, bottom = 21.dp)
                    } else {
                        Modifier
                            .padding(start = 21.dp, end = 21.dp, top = 19.dp, bottom = 21.dp)
                            .verticalScroll(rememberScrollState())
                    },
                ) { copy() }
            }
        }

    }
}

/**
 * Визуал карточки: градиент, сетка 32 dp, два свечения и логотип.
 *
 * @param name название канала;
 * @param logoUrl логотип;
 * @param showLogo грузить ли картинку;
 * @param compact сжатый вариант разворота;
 * @param live идёт ли передача (плашка «Сейчас в эфире»).
 */
@Composable
private fun ChannelVisual(
    name: String,
    logoUrl: String?,
    showLogo: Boolean,
    livePreview: Boolean,
    streamUrl: String,
    bufferSeconds: Int,
    compact: Boolean,
    narrow: Boolean,
    live: Boolean,
    onWatch: () -> Unit,
) {
    val height = when {
        compact -> TvDimens.cardVisualCompact
        narrow -> 96.dp
        else -> TvDimens.cardVisual
    }
    val logoSize = when {
        compact -> TvDimens.logoMedium
        narrow -> 48.dp
        else -> TvDimens.logoLarge
    }
    val logoShape = RoundedCornerShape(if (compact) 19.dp else TvRadius.logoLarge)
    val logoText = when {
        compact -> LogoMediumText
        narrow -> LogoMediumText
        else -> LogoLargeText
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // С живым превью визуал держит пропорции кадра и занимает верх карточки целиком:
            // раньше видео болталось по центру блока, и превью выглядело «посреди карточки».
            .then(if (livePreview) Modifier.aspectRatio(16f / 9f) else Modifier.heightIn(min = height))
            .drawBehind {
                val step = 32.dp.toPx()
                val lineColor = Color(0x04FFFFFF)
                var x = 0f
                while (x < size.width) {
                    drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f)
                    y += step
                }
                // Два пятна свечения: левое — фиолетовое, правое — розовое (в макете blur 50).
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x598857EC), Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 0.10f),
                        radius = 160.dp.toPx() * 0.5f,
                    ),
                    radius = 160.dp.toPx() * 0.5f,
                    center = Offset(size.width * 0.08f, size.height * 0.10f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x59E2546C), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 1.05f),
                        radius = 160.dp.toPx() * 0.5f,
                    ),
                    radius = 160.dp.toPx() * 0.5f,
                    center = Offset(size.width * 0.95f, size.height * 1.05f),
                )
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF3C3055), Color(0xFF191825), Color(0xFF10131B)),
                    center = Offset.Unspecified,
                    radius = Float.POSITIVE_INFINITY,
                ),
            ),
        contentAlignment = if (livePreview) Alignment.TopStart else Alignment.Center,
    ) {
        ChannelLogo(
            name = name,
            url = logoUrl,
            size = logoSize,
            shape = logoShape,
            textStyle = logoText,
            showImage = showLogo,
        )
        if (livePreview && !LocalPreviewSuppressed.current) {
            // Фокус может попасть и на превью: плашка и обводка показывают, где пульт.
            var previewFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onFocusChanged { previewFocused = it.isFocused }
                    .clickable { onWatch() }
                    // Фокус на превью: акцентная обводка поверх кадра — видно, что пульт здесь.
                    .then(
                        if (previewFocused) {
                            Modifier.border(3.dp, TvColors.accent, RoundedCornerShape(TvRadius.panel))
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                LivePreview(
                    url = streamUrl,
                    bufferSeconds = bufferSeconds,
                    modifier = Modifier.fillMaxSize(),
                    fallback = {
                        ChannelLogo(
                            name = name,
                            url = logoUrl,
                            size = logoSize,
                            shape = logoShape,
                            textStyle = logoText,
                            showImage = showLogo,
                        )
                    },
                )
                // Прозрачный слой поверх видео: касание открывает канал в плеере.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onWatch() },
                )
            }
        }
        if (live && !narrow) {
            LivePlaque(modifier = Modifier.align(Alignment.BottomStart).padding(17.dp))
        }
    }
}

/**
 * Плашка «Сейчас в эфире»: зелёная точка с свечением и текст 9.
 *
 * @param modifier положение плашки.
 */
@Composable
private fun LivePlaque(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(TvRadius.plaque))
            .background(Color(0xB8090B10))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(TvColors.green),
        )
        Text(text = "Сейчас в эфире", style = TvType.caption, color = TvColors.text)
    }
}

/**
 * Шапка карточки: категория 9/700 uppercase, название 22/600, избранное и «Свернуть».
 */
@Composable
private fun CardHeader(
    categoryName: String?,
    channelName: String,
    favorite: Boolean,
    programmeOpen: Boolean,
    narrow: Boolean = false,
    onToggleFavorite: () -> Unit,
    onCollapseProgramme: () -> Unit,
    compactCard: Boolean = false,
    // «Влево» с сердечка уводит фокус в список каналов.
    leftTarget: FocusRequester? = null,
    // «Вверх» с сердечка ведёт на кнопку поиска в шапке.
    upTarget: FocusRequester? = null,
) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            if (!categoryName.isNullOrBlank()) {
                Text(
                    text = categoryName.uppercase(),
                    style = TvType.eyebrow,
                    color = TvColors.eyebrow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = channelName,
                style = if (compactCard) {
                    val nameSize = (LocalConfiguration.current.screenWidthDp * 0.0165f).coerceIn(10.5f, 15f)
                    TvType.cardChannel.copy(fontSize = nameSize.sp, lineHeight = (nameSize * 1.24f).sp)
                } else {
                    TvType.cardChannel
                },
                color = TvColors.text,
                maxLines = if (compactCard) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (programmeOpen) {
            Spacer(Modifier.width(9.dp))
            CollapseButton(onClick = onCollapseProgramme)
        }
        Spacer(Modifier.width(9.dp))
        FavoriteButton(
            favorite = favorite,
            onClick = onToggleFavorite,
            leftTarget = leftTarget,
            upTarget = upTarget,
        )
    }
}

/**
 * Кнопка избранного: 39×39, сердце `#777e8d` в покое, `#e76983` с заливкой в избранном.
 *
 * @param favorite канал в избранном;
 * @param onClick переключение.
 */
@Composable
private fun FavoriteButton(
    favorite: Boolean,
    onClick: () -> Unit,
    leftTarget: FocusRequester? = null,
    upTarget: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.small)
    Box(
        modifier = Modifier
            .size(TvDimens.favoriteButton)
            .clip(shape)
            // Фокус на «сердечке»: акцентная заливка и толстая обводка — на тёмной карточке
            // тонкое кольцо не читалось (замечание Александра 05.11.2026).
            .background(
                when {
                    focused -> TvColors.accent
                    favorite -> TvColors.favoriteBg
                    else -> Color.Transparent
                },
            )
            .border(
                if (focused) 3.dp else 1.dp,
                when {
                    focused -> TvColors.focusOutline
                    favorite -> TvColors.favoriteLine
                    else -> TvColors.line
                },
                shape,
            )
            // «Влево» с сердечка идёт в список каналов, а не в боковое меню.
            .then(
                if (leftTarget != null) Modifier.focusProperties { left = leftTarget } else Modifier,
            )
            // «Вверх» с сердечка — на кнопку поиска: геометрически ближайшим оказывался
            // заголовок шапки, и фокус оставался на месте.
            .then(
                if (upTarget != null) Modifier.focusProperties { up = upTarget } else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (favorite) "Убрать из избранного" else "В избранное",
            tint = when {
                focused -> Color(0xFF100A1E)
                favorite -> TvColors.favorite
                else -> Color(0xFF777E8D)
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Кнопка «Свернуть» в развороте телепрограммы: назад и текст 10/600.
 *
 * @param onClick свернуть телепрограмму.
 */
@Composable
private fun CollapseButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.small)
    Row(
        modifier = Modifier
            .heightIn(min = TvDimens.collapseButtonMinHeight)
            .clip(shape)
            .border(1.dp, TvColors.line, shape)
            .background(if (focused) TvColors.rowHover else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TvColors.muted,
            modifier = Modifier.size(14.dp),
        )
        Text(text = "Свернуть", style = TvType.rowProgramme, color = TvColors.muted)
    }
}

/**
 * Блок «Сейчас»: подпись, название передачи, интервал и полоса прогресса.
 */
@Composable
private fun NowBlock(
    channel: ChannelUi,
    now: Instant,
    zone: ZoneId,
    // Гид ещё читается: вместо «Программы нет» — многоточие.
    epgPending: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TvColors.line),
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = channel.nowTitle ?: if (epgPending) "…" else "Программы нет",
                style = TvType.programme,
                color = TvColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (channel.nowStart != null && channel.nowEnd != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = TextFormat.timeRange(channel.nowStart, channel.nowEnd, zone),
                    style = TvType.caption,
                    color = TvColors.programmePastTime,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(11.dp))
        val fraction = if (channel.nowStart != null && channel.nowEnd != null) {
            TextFormat.progressFraction(channel.nowStart, channel.nowEnd, now)
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TvDimens.cardProgressHeight)
                .clip(RoundedCornerShape(5.dp))
                .background(TvColors.cardProgressTrack),
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(TvDimens.cardProgressHeight)
                        .background(TvCardProgressGradient),
                )
            }
        }
    }
}

/**
 * Кнопка «Программа»: единственный вход в разворот телепрограммы.
 *
 * @param onClick развернуть телепрограмму.
 */
@Composable
private fun ProgrammeButton(
    onClick: () -> Unit,
    compact: Boolean = false,
    leftTarget: FocusRequester? = null,
    // Подпись кнопки: «Программа» либо «Архив», когда передач нет.
    label: String = "Программа",
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.control)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 38.dp else TvDimens.programmeButtonMinHeight)
            .clip(shape)
            .border(3.dp, if (focused) TvColors.focusOutline else TvColors.line, shape)
            // Фокус — тёмная заливка с яркой обводкой: акцентная плашка сливалась с градиентом.
            .background(if (focused) Color(0xFF05060A) else Color.Transparent)
            .then(
                if (leftTarget != null) Modifier.focusProperties { left = leftTarget } else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.List,
            contentDescription = null,
            tint = if (focused) TvColors.focusOutline else TvColors.accent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = if (compact) {
                TvType.programmeButton.copy(fontSize = 11.sp)
            } else {
                TvType.programmeButton
            },
            maxLines = 1,
            softWrap = false,
            color = if (focused) TvColors.focusOutline else TvColors.programmeButtonText,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = if (focused) TvColors.focusOutline else TvColors.nowLabel,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * Кнопка «Смотреть»: градиент, круг с треугольником и текст 13/700.
 *
 * @param onClick открыть канал в плеере.
 */
@Composable
private fun WatchButton(
    onClick: () -> Unit,
    compact: Boolean = false,
    leftTarget: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 38.dp else TvDimens.watchButtonMinHeight)
            .clip(shape)
            .background(TvButtonGradient)
            .then(
                if (leftTarget != null) Modifier.focusProperties { left = leftTarget } else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape, color = TvColors.accent)
            .focusable()
            .padding(horizontal = if (compact) 6.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 20.dp else 28.dp)
                .clip(CircleShape)
                .background(Color(0x26FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compact) 12.dp else 16.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Смотреть",
            style = if (compact) TvType.watchButton.copy(fontSize = 10.5.sp) else TvType.watchButton,
            color = Color.White,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Строка «Далее в HH:MM»: подпись слева, название следующей передачи справа.
 */
@Composable
private fun NextBlock(channel: ChannelUi, zone: ZoneId) {
    if (channel.nextTitle.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 13.dp, start = 3.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = "Далее", style = TvType.caption, color = TvColors.nowLabel)
        Text(
            text = channel.nextTitle,
            style = TvType.programme,
            color = TvColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
    }
}

/** Пустая карточка: канал ещё не выбран. */
@Composable
private fun EmptyCard() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Выберите канал", style = TvType.panelTitle, color = TvColors.muted)
    }
}
