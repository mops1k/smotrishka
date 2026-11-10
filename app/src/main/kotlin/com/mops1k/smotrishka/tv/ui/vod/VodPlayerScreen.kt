package com.mops1k.smotrishka.tv.ui.vod

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.mops1k.smotrishka.tv.R
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.player.PlayerBuffer
import com.mops1k.smotrishka.tv.ui.common.WithoutFontScale
import com.mops1k.smotrishka.tv.ui.player.AspectIcons
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.ui.player.AspectModes
import com.mops1k.smotrishka.tv.ui.player.PlayerEngine
import com.mops1k.smotrishka.tv.ui.player.osd.PlayerOsdButtons
import com.mops1k.smotrishka.tv.ui.player.osd.PlayerOsdPanel
import com.mops1k.smotrishka.tv.ui.player.osd.PlayerOsdTop
import com.mops1k.smotrishka.tv.ui.player.osd.rememberPlayerOsdFocus
import com.mops1k.smotrishka.tv.ui.player.osd.rememberPlayerClockText
import com.mops1k.smotrishka.tv.ui.player.osd.PlayerSeekBar
import kotlinx.coroutines.delay

/**
 * VOD-плеер медиатеки: HLS-поток фильма или серии, пауза, перемотка ±10 секунд, сохранение
 * позиции и автопереход к следующей серии с отменяемым отсчётом.
 *
 * Плеер создаётся общим [PlayerEngine]: буфер, повторы и ограничение качества — те же настройки,
 * что у живого эфира. Отличие только в источнике: запись не «живая», поэтому окно не плавает.
 *
 * @param onBack вернуться к каталогу;
 * @param viewModel экранная модель.
 */
@Composable
fun VodPlayerScreen(
    onBack: () -> Unit,
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bufferSeconds by viewModel.bufferSeconds.collectAsStateWithLifecycle()
    val quality by viewModel.videoQuality.collectAsStateWithLifecycle()
    val aspectMode by viewModel.aspectMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val content = state as? VodPlayerUiState.Content

    var osdVisible by remember { mutableStateOf(true) }
    // Действия в панели продлевают показ: панель не должна исчезать из-под пальца.
    var osdInteraction by remember { mutableIntStateOf(0) }
    // Фокус меню плеера — общий компонент с эфирным плеером: одно правило для обоих.
    val osdFocus = rememberPlayerOsdFocus(osdVisible)

    // Плеер пересоздаётся при смене серии: адрес потока — ключ.
    val player: ExoPlayer? = remember(content?.streamUrl) {
        content?.let {
            PlayerEngine.build(
                context = context,
                url = it.streamUrl,
                buffer = PlayerBuffer(bufferSeconds),
                quality = quality,
                live = false,
                startPositionMs = it.startPositionMillis,
            )
        }
    }

    // «Назад» сохраняет позицию: вернувшись, пользователь увидит «Продолжить».
    fun leave() {
        player?.let { viewModel.saveProgress(it.currentPosition, it.duration.takeIf { d -> d > 0 }) }
        onBack()
    }

    BackHandler { leave() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) viewModel.onPlaybackEnded()
            }
        }
        player?.addListener(listener)
        onDispose {
            player?.removeListener(listener)
            player?.release()
        }
    }

    // Позиция сохраняется раз в 10 секунд, а не на каждый кадр.
    LaunchedEffect(player) {
        while (true) {
            delay(PROGRESS_SAVE_MILLIS)
            player?.let {
                viewModel.saveProgress(it.currentPosition, it.duration.takeIf { d -> d > 0 })
            }
        }
    }

    LaunchedEffect(osdVisible, osdInteraction) {
        if (osdVisible) {
            delay(OSD_HIDE_MILLIS)
            osdVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Любое нажатие пульта продлевает показ меню, а из закрытого состояния — открывает
            // его: раньше меню гасло прямо во время навигации.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                osdInteraction++
                val dpad = event.key == Key.DirectionCenter ||
                    event.key == Key.DirectionUp ||
                    event.key == Key.DirectionDown ||
                    event.key == Key.DirectionLeft ||
                    event.key == Key.DirectionRight ||
                    event.key == Key.Enter
                if (!osdVisible && dpad) {
                    osdVisible = true
                    true
                } else {
                    false
                }
            },
    ) {
        if (player != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    (LayoutInflater.from(viewContext)
                        .inflate(R.layout.player_surface, null) as PlayerView).apply {
                        useController = false
                        this.player = player
                        resizeMode = AspectModes.resizeMode(aspectMode)
                    }
                },
                update = { view -> view.resizeMode = AspectModes.resizeMode(aspectMode) },
            )
        }

        // Касание видео показывает OSD, повторное касание пустого места — скрывает;
        // OK на пульте делает то же. Без этого слоя меню после автоскрытия вернуть нечем.
        // Кнопки панели рисуются выше и забирают нажатия себе.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(osdFocus.surface)
                // Пока меню открыто, поверхность видео в фокус не пускаем: иначе крестовина
                // уводила фокус в невидимый узел, и кольца на кнопках не было видно.
                .focusProperties { canFocus = !osdVisible }
                .focusable(enabled = !osdVisible)
                .pointerInput(Unit) {
                    detectTapGestures { osdVisible = !osdVisible }
                },
        )

        when (val current = state) {
            VodPlayerUiState.Loading -> VodCenteredText("Загружаем фильм…")

            is VodPlayerUiState.Error -> Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = current.reason, style = TvType.panelTitle, color = TvColors.muted)
                Spacer(Modifier.size(14.dp))
                Button(onClick = onBack) { Text("Назад") }
            }

            is VodPlayerUiState.Content -> if (osdVisible) {
                VodPlayerOsd(
                    content = current,
                    player = player,
                    onBack = ::leave,
                    onCancelCountdown = viewModel::cancelCountdown,
                    onNextEpisode = viewModel::playNextEpisode,
                    onPreviousEpisode = viewModel::playPreviousEpisode,
                    onInteract = { osdInteraction++ },
                    onSelectQuality = viewModel::selectQuality,
                    playFocus = osdFocus.play,
                    aspectIcon = AspectIcons.of(aspectMode),
                    aspectLabel = AspectModes.label(aspectMode),
                    onCycleAspect = { viewModel.cycleAspectMode(aspectMode) },
                )
            }
        }
    }
}

/**
 * Наложение плеера медиатеки: название, серия и нижняя панель с временем, полосой и кнопками.
 *
 * Панель та же, что в эфирном плеере. Любое действие в ней продлевает показ: иначе панель
 * исчезала через четыре секунды прямо из-под пальца.
 *
 * @param content текущая серия;
 * @param player экземпляр плеера;
 * @param onBack выход из плеера;
 * @param onCancelCountdown отмена автоперехода;
 * @param onNextEpisode переход к следующей серии;
 * @param onPreviousEpisode переход к предыдущей серии;
 * @param onInteract действие в панели — продлевает показ.
 */
@Composable
private fun VodPlayerOsd(
    content: VodPlayerUiState.Content,
    player: ExoPlayer?,
    onBack: () -> Unit,
    onCancelCountdown: () -> Unit,
    onNextEpisode: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onInteract: () -> Unit,
    onSelectQuality: (VodQuality?) -> Unit,
    playFocus: FocusRequester,
    aspectIcon: ImageVector,
    aspectLabel: String,
    onCycleAspect: () -> Unit,
) {
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(true) }

    LaunchedEffect(player) {
        while (true) {
            player?.let {
                positionMs = it.currentPosition.coerceAtLeast(0L)
                durationMs = it.duration.takeIf { duration -> duration > 0L } ?: 0L
                // Иконка паузы показывает намерение, а не момент: во время буферизации
                // isPlaying ложно, и кнопка выглядела «неотзывчивой».
                playing = it.playWhenReady
            }
            delay(POSITION_TICK_MILLIS)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
    ) {
        PlayerOsdTop(
            channelTitle = content.title,
            programmeTitle = content.episodeTitle,
            isArchive = false,
            clock = rememberPlayerClockText(),
            onBack = onBack,
            aspectIcon = aspectIcon,
            aspectLabel = aspectLabel,
            onCycleAspect = onCycleAspect,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            val countdown = content.countdownSeconds
            if (countdown != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Следующая серия через $countdown с",
                        style = TvType.caption,
                        color = TvColors.accent,
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = onCancelCountdown) { Text("Отмена") }
                }
                Spacer(Modifier.height(10.dp))
            }

            PlayerOsdPanel(
                positionLabel = formatPlaybackTime(positionMs),
                durationLabel = formatPlaybackTime(durationMs),
                fraction = if (durationMs > 0L) {
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                },
                onSeek = { fraction ->
                    onInteract()
                    if (durationMs > 0L) player?.seekTo((fraction * durationMs).toLong())
                },
                onPreview = {},
                onSeekBy = { delta ->
                    onInteract()
                    player?.let { if (delta < 0) seekBack(it) else seekForward(it) }
                },
            ) {
                // Кнопки не масштабируются вместе с текстом: подписи не должны вылезать
                // из кружков при большом размере шрифта.
                WithoutFontScale {
                    PlayerOsdButtons(
                        focusRequester = playFocus,
                        playing = playing,
                        onSeekBack = {
                            onInteract()
                            player?.let { seekBack(it) }
                        },
                        onTogglePlay = {
                            onInteract()
                            player?.let { togglePlay(it) }
                        },
                        onSeekForward = {
                            onInteract()
                            player?.let { seekForward(it) }
                        },
                        onPreviousEpisode = {
                            onInteract()
                            onPreviousEpisode()
                        }.takeIf { content.previousEpisodeId != null },
                        onNextEpisode = {
                            onInteract()
                            onNextEpisode()
                        }.takeIf { content.nextEpisodeId != null },
                        muted = null,
                    )
                }
            }
        }

        // Переключатель качества — правый низ панели, рядом с кнопками.
        VodQualityPicker(
            qualities = content.qualities,
            selected = content.selectedQuality,
            onSelect = {
                onInteract()
                onSelectQuality(it)
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** Сообщение по центру экрана. */
@Composable
private fun VodCenteredText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = TvType.panelTitle, color = TvColors.muted)
    }
}

/** Перематывает назад на шаг, не уходя в отрицательную позицию. */
private fun seekBack(player: ExoPlayer) {
    player.seekTo((player.currentPosition - SEEK_STEP_MILLIS).coerceAtLeast(0L))
}

/** Перематывает вперёд на шаг. */
private fun seekForward(player: ExoPlayer) {
    val end = player.duration
    val target = player.currentPosition + SEEK_STEP_MILLIS
    player.seekTo(if (end > 0) target.coerceAtMost(end) else target)
}

/** Переключает паузу и воспроизведение. */
private fun togglePlay(player: ExoPlayer) {
    // Переключаем по намерению: во время буферизации isPlaying ложно, и пауза не срабатывала.
    if (player.playWhenReady) player.pause() else player.play()
}

/** Как часто обновлять время и полосу в OSD. */
private const val POSITION_TICK_MILLIS = 500L

/** Шаг перемотки кнопками OSD. */
private const val SEEK_STEP_MILLIS = 10_000L

/** Как часто сохранять позицию просмотра. */
private const val PROGRESS_SAVE_MILLIS = 10_000L

/** Через сколько OSD гаснет без действий. */
private const val OSD_HIDE_MILLIS = 4_000L

/** Время плеера: `мм:сс`, а от часа — `ч:мм:сс`. */
private fun formatPlaybackTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
