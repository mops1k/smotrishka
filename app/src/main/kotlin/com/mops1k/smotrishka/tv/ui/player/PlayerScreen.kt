package com.mops1k.smotrishka.tv.ui.player

import android.graphics.Matrix
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.mops1k.smotrishka.tv.core.designsystem.component.LocalReduceMotion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.ui.PlayerView
import com.mops1k.smotrishka.tv.SessionLog
import com.mops1k.smotrishka.tv.core.data.AspectMode
import com.mops1k.smotrishka.tv.core.data.AudioLanguage
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.ui.common.WithoutFontScale
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.ui.home.ProgrammeProgress
import com.mops1k.smotrishka.tv.ui.home.ProgrammeRowUi
import com.mops1k.smotrishka.tv.ui.home.ProgrammeStatus
import com.mops1k.smotrishka.tv.ui.home.components.ChannelLogo
import com.mops1k.smotrishka.tv.ui.player.osd.OsdCircleButton
import com.mops1k.smotrishka.tv.ui.player.osd.PlayerOsdTop
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import com.mops1k.smotrishka.tv.R
import androidx.compose.ui.focus.onFocusChanged
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors

/**
 * Полноэкранный плеер канала.
 *
 * Поведение рассчитано на слабый интернет: поток идёт через OkHttp с увеличенными таймаутами
 * и повторами, буфер впереди берётся из настроек. Картинка стартует сама — пауза ставится
 * только по просьбе пользователя.
 *
 * OSD (название канала, текущая передача и обычные кнопки плеера с иконками) показывается
 * по касанию или кнопке пульта и сам гаснет через несколько секунд. Системные панели скрыты.
 *
 * @param onBack возврат на главный экран;
 * @param viewModel экранная модель.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onGoLive: (ChannelId) -> Unit = {},
    onOpenRecent: (String) -> Unit = {},
    onOpenProgramme: (ChannelId) -> Unit = {},
    onOpenArchive: (ChannelId, Instant) -> Unit = { _, _ -> },
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bufferSeconds by viewModel.bufferSeconds.collectAsStateWithLifecycle()
    val quality by viewModel.videoQuality.collectAsStateWithLifecycle()
    val direct by viewModel.directStream.collectAsStateWithLifecycle(initialValue = true)
    val aspectMode by viewModel.aspectMode.collectAsStateWithLifecycle(initialValue = AspectMode.FIT)
    val recents by viewModel.recents.collectAsStateWithLifecycle(initialValue = emptyList<RecentChannelRef>())
    val favorites by viewModel.favorites.collectAsStateWithLifecycle(initialValue = emptySet<String>())
    val bufferPreferred by viewModel.bufferPreferred.collectAsStateWithLifecycle(initialValue = false)
    val unstable by viewModel.unstable.collectAsStateWithLifecycle(initialValue = false)
    val neighbours by viewModel.neighbours.collectAsStateWithLifecycle(initialValue = null to null)
    val channelsInScope by viewModel.channelsInScope
        .collectAsStateWithLifecycle(initialValue = emptyList<ChannelRef>())
    val schedule by viewModel.schedule
        .collectAsStateWithLifecycle(initialValue = emptyList<ProgrammeRowUi>())
    val osdHideSeconds by viewModel.osdHideSeconds.collectAsStateWithLifecycle()

    // Передача в OSD перепроверяется, пока плеер открыт: иначе к концу передачи остаётся
    // «Программа закончилась» и заполненная полоса.
    LaunchedEffect(Unit) {
        while (true) {
            delay(OSD_PROGRAMME_TICK_MS)
            viewModel.refreshProgramme()
        }
    }
    val recoveryDelaySeconds by viewModel.recoveryDelaySeconds.collectAsStateWithLifecycle()
    val audioLanguage by viewModel.audioLanguage.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)
    ImmersiveMode()

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        when (val current = state) {
            PlayerUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is PlayerUiState.Error -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = current.reason,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack) { Text("Назад к каналам") }
            }

            is PlayerUiState.Content -> PlayerContent(
                channel = current,
                bufferSeconds = bufferSeconds,
                quality = quality,
                recoveryDelaySeconds = recoveryDelaySeconds,
                audioLanguage = audioLanguage,
                direct = direct,
                onBack = onBack,
                onOpenProgramme = onOpenProgramme,
                aspectMode = aspectMode,
                onCycleAspect = { viewModel.cycleAspectMode(aspectMode) },
                recents = recents,
                onOpenRecent = onOpenRecent,
                channels = channelsInScope,
                favorites = favorites,
                onToggleFavorite = viewModel::toggleFavorite,
                bufferPreferred = bufferPreferred,
                onRememberBuffered = viewModel::rememberBuffered,
                unstable = unstable,
                onMarkUnstable = viewModel::markUnstable,
                neighbours = neighbours,
                onSwitchChannel = onOpenRecent,
                onGoLive = { onGoLive(current.channelId) },
                onOpenArchive = onOpenArchive,
                schedule = schedule,
                osdHideSeconds = osdHideSeconds,
            )
        }
    }
}

/**
 * Экран канала: видео и OSD поверх него.
 *
 * @param channel данные канала;
 * @param bufferSeconds сколько секунд держать в буфере;
 * @param onBack возврат к списку каналов.
 */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PlayerContent(
    channel: PlayerUiState.Content,
    bufferSeconds: Int,
    quality: VideoQuality,
    recoveryDelaySeconds: Int,
    audioLanguage: AudioLanguage,
    direct: Boolean,
    onBack: () -> Unit,
    onOpenProgramme: (ChannelId) -> Unit,
    aspectMode: AspectMode,
    onCycleAspect: () -> Unit,
    recents: List<RecentChannelRef>,
    onOpenRecent: (String) -> Unit,
    channels: List<ChannelRef>,
    favorites: Set<String>,
    onToggleFavorite: () -> Unit,
    bufferPreferred: Boolean,
    onRememberBuffered: () -> Unit,
    unstable: Boolean,
    onMarkUnstable: () -> Unit,
    neighbours: Pair<String?, String?>,
    onSwitchChannel: (String) -> Unit,
    onGoLive: () -> Unit,
    onOpenArchive: (ChannelId, Instant) -> Unit,
    schedule: List<ProgrammeRowUi>,
    // Сколько секунд меню плеера остаётся на экране (значение из настроек).
    osdHideSeconds: Int,
) {
    val context = LocalContext.current
    var recentsVisible by remember { mutableStateOf(false) }
    var channelsVisible by remember { mutableStateOf(false) }
    // Фокус внутри OSD: пока он там, влево/вправо принадлежат кнопкам и полосе программы,
    // иначе корневой обработчик перехватывал их и открывал список каналов.
    var osdFocused by remember { mutableStateOf(false) }
    // Телепрограмма открывается панелью поверх видео, как список каналов и «Недавние»,
    // а не отдельным экраном (указание Александра 06.11.2026).
    var programmeVisible by remember { mutableStateOf(false) }
    // Таймер удержания «назад»: короткое нажатие выходит, удержание открывает недавние.
    var backHeld by remember { mutableStateOf(false) }
    var backPressJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    // Отдельный Job на распознавание удержания «назад».
    val backPressScope = scope
    val controls = rememberPlayerControls(hideSeconds = osdHideSeconds)
    val controlsState by controls.state.collectAsStateWithLifecycle()
    // Фокус в OSD при открытии: иначе он остаётся на плеере и кнопки не работают.
    val osdFirstFocus = remember { FocusRequester() }
    LaunchedEffect(controlsState.osdVisible) {
        if (!controlsState.osdVisible) return@LaunchedEffect
        // Несколько попыток: OSD появляется через AnimatedVisibility, и первая заявка
        // на фокус может прийти до того, как кнопка попадёт в композицию.
        repeat(8) {
            if (osdFocused) return@LaunchedEffect
            withFrameNanos { }
            runCatching { osdFirstFocus.requestFocus() }
            delay(60)
        }
    }
    val buffer = remember(bufferSeconds) { PlayerBuffer(bufferSeconds) }

    // Восстановление потока не заканчивается само: счётчик нужен для паузы и для счётчика
    // на крутилке, поколение — чтобы каждую третью попытку пересоздать плеер целиком.
    var recoveryAttempts by remember(channel.streamUrl) { mutableStateOf(0) }
    var playerGeneration by remember(channel.streamUrl) { mutableStateOf(0) }
    // Сколько ждать до следующей попытки: дедлайн и длина паузы — из них считается
    // прогресс и обратный отсчёт на экране.
    var retryDeadlineMillis by remember(channel.streamUrl) { mutableStateOf<Long?>(null) }
    var retryDelayMillis by remember(channel.streamUrl) { mutableLongStateOf(0L) }
    // Размер кадра нужен масштабу TextureView: у SurfaceView это делал PlayerView сам.
    var videoSize by remember(channel.streamUrl) { mutableStateOf(VideoSize.UNKNOWN) }

    // Эфир пишется в растущий файл, а файл отдаётся плееру как непрерывный локальный поток:
    // так нет ни «стены» длительности, ни перепривязки к окну провайдера.
    var streamUrl by remember(channel.streamUrl) { mutableStateOf<String?>(null) }
    var bufferedSeconds by remember(channel.streamUrl) { mutableStateOf(0L) }
    var liveReady by remember(channel.streamUrl) { mutableStateOf(false) }
    // Архивная передача играется из локального файла: провайдер отдаёт её кусками,
    // поэтому сначала скачиваем передачу целиком (с прогрессом), затем отдаём плееру.
    var archiveUri by remember(channel.streamUrl) { mutableStateOf<String?>(null) }
    var archiveProgressSeconds by remember(channel.streamUrl) { mutableStateOf(0L) }
    var archiveError by remember(channel.streamUrl) { mutableStateOf<String?>(null) }
    // Позиция воспроизведения доступна загрузчику архива, чтобы не убегать вперёд.
    var playbackPositionMs by remember { mutableStateOf(0L) }
    var archiveStarted by remember { mutableStateOf(false) }
    // Позиция и поколение перезапуска: плеер доходит до конца скачанного и продолжает дальше.
    var archiveRestartPosition by remember(channel.streamUrl) { mutableStateOf(0L) }
    var archiveGeneration by remember(channel.streamUrl) { mutableStateOf(0) }
    // Сколько секунд передачи уже проиграно: следующее окно запрашиваем с этого места.
    var archiveWindowOffset by remember(channel.streamUrl) { mutableStateOf(0L) }
    // Позиция, которую пользователь тянет по полосе: показываем её сразу, применяем на отпускании.
    var seekPreviewFraction by remember { mutableStateOf<Float?>(null) }
    // Сколько миллисекунд перемотать внутри нового окна, чтобы попасть ровно в выбранный момент.
    var archiveSeekInsideMs by remember { mutableStateOf(0L) }
    val archiveDurationSeconds = channel.programmeStart?.let { start ->
        ((channel.programmeEnd?.epochSecond ?: 0L) - start.epochSecond).coerceAtLeast(0L)
    } ?: 0L
    val archivePercent = if (archiveDurationSeconds > 0L) {
        (archiveProgressSeconds * 100 / archiveDurationSeconds).toInt().coerceIn(0, 100)
    } else {
        0
    }
    var firstFrame by remember(channel.streamUrl) { mutableStateOf(false) }
    // Поверхность перепривязывается, когда поток дошёл до READY: без этого на части устройств
    // кадр не показывается, пока пользователь не тронет экран (загрузка висит, звук идёт).
    var playerReady by remember(channel.streamUrl) { mutableStateOf(false) }
    var playerCreatedAt by remember(channel.streamUrl) { mutableStateOf(0L) }
    var server by remember(channel.streamUrl) { mutableStateOf<LocalHlsServer?>(null) }
    // Прокси не смог отдать ни одного сегмента — возвращаемся к прямому потоку.
    var bufferFailed by remember(channel.streamUrl) { mutableStateOf(false) }
    var autoBuffer by remember(channel.streamUrl) { mutableStateOf(bufferPreferred) }
    var playerRef by remember(channel.streamUrl) { mutableStateOf<ExoPlayer?>(null) }
    val useBuffer = (!direct || autoBuffer) && !bufferFailed
    // Связь пропала: показываем плашку поверх видео, но экран не блокируем —
    // поток догонит сам, как только сеть вернётся.
    var streamProblem by remember(channel.streamUrl) { mutableStateOf(false) }

    // Наблюдение за откатами и затяжными остановками прямого потока.
    // Режим эфира выбирает только настройка: раньше здесь канал автоматически уводился
    // на локальный буфер, и на приставке это давало обратный эффект — плеер создавался
    // заново, стартовал почти без запаса и постоянно переключался «буферизация ↔ играет»,
    // что и выглядело как микрофризы. ott-play на этой же приставке играет напрямую и плавно.
    LaunchedEffect(playerRef, useBuffer) {
        val active = playerRef ?: return@LaunchedEffect
        if (useBuffer) return@LaunchedEffect
        val events = mutableListOf<Long>()
        var lastPosition = 0L
        while (true) {
            delay(ANOMALY_POLL_MS)
            val now = System.currentTimeMillis()
            val position = active.currentPosition
            if (!firstFrame && now - playerCreatedAt >= StreamAnomaly.STALL_MILLIS * 2) events += now
            if (StreamAnomaly.isRewind(lastPosition, position)) {
                events += now
                android.util.Log.d("SmotrishkaBuffer", "anomaly: откат позиции $lastPosition → $position")
            }
            lastPosition = position
            if (StreamAnomaly.shouldSwitch(events, now)) {
                android.util.Log.w("SmotrishkaBuffer", "поток нестабилен: признаков ${events.size}")
                events.clear()
            }
        }
    }

    // Архив играем напрямую с провайдера, как ott-play: он отдаёт окно ~минуту с нужного
    // момента. Локальный сервер и подготовка не нужны — старт мгновенный.
    LaunchedEffect(channel.channelId, channel.programmeStart, channel.isArchive, archiveWindowOffset) {
        if (!channel.isArchive) return@LaunchedEffect
        val start = channel.programmeStart
        if (start == null) {
            archiveError = "Не удалось определить передачу"
            return@LaunchedEffect
        }
        archiveError = null
        val base = channel.streamUrl.substringBefore('?')
        val utc = start.epochSecond + archiveWindowOffset
        val nowSeconds = System.currentTimeMillis() / 1000
        streamUrl = "$base?utc=$utc&lutc=$nowSeconds"
        liveReady = true
        android.util.Log.d(
            "SmotrishkaArchive",
            "direct window utc=$utc offset=$archiveWindowOffset url=$streamUrl",
        )
    }

    LaunchedEffect(channel.streamUrl, direct, useBuffer) {
        // Архив наполняет тот же локальный сервер своими сегментами: живой загрузчик здесь
        // не должен подменять адрес потоком провайдера, иначе экран висит на «Готовим архив».
        if (channel.isArchive) return@LaunchedEffect
        liveReady = false
        bufferedSeconds = 0L
        if (!useBuffer) {
            // Прямой поток провайдера: самый простой путь, на здоровых каналах без нареканий.
            streamUrl = channel.streamUrl
            liveReady = true
            return@LaunchedEffect
        }
        streamUrl = null
        // Свой HLS: плеер получает плейлист с сегментами и пометками разрывов, поэтому
        // не спотыкается ни на конце файла, ни на скачке меток времени.
        val hls = LocalHlsServer().apply { directory = context.cacheDir }
        server = hls
        streamUrl = hls.start()
        android.util.Log.d("SmotrishkaBuffer", "hls server url=$streamUrl")
        val loader = LiveBufferLoader(client = PlayerHttp.client, cacheDir = context.cacheDir)
        // Второй рубеж: даже неожиданная ошибка загрузчика не должна ронять экран
        // (непойманное исключение в корутине LaunchedEffect закрывает приложение).
        runCatching {
            loader.load(
                streamUrl = channel.streamUrl,
                onSegment = { file, discontinuity ->
                    hls.add(file, discontinuity)
                    bufferedSeconds += SEGMENT_SECONDS
                    android.util.Log.d("SmotrishkaBuffer", "screen got segment, buffered=$bufferedSeconds liveReady=$liveReady")
                    if (!liveReady && bufferedSeconds >= START_BUFFER_SECONDS) liveReady = true
                },
                onGap = onMarkUnstable,
                isActive = { true },
                onConnectivity = { available -> streamProblem = !available },
                onHopeless = {
                    // Прокси не отдал ни одного сегмента: переключаем канал на прямой поток.
                    bufferFailed = true
                    autoBuffer = false
                },
            )
        }.onFailure {
            android.util.Log.e("SmotrishkaBuffer", "buffer loader stopped", it)
        }
    }

    DisposableEffect(server) {
        onDispose { server?.stop() }
    }

    // Не ждём запас бесконечно: на медленном канале стартуем с тем, что успели накопить.
    LaunchedEffect(channel.streamUrl, useBuffer) {
        if (!useBuffer) return@LaunchedEffect
        // Проверяем не один раз: если к моменту первого таймаута буфера ещё нет, ждём дальше,
        // иначе чёрный экран с «Загрузкой» висит вечно.
        while (true) {
            delay(START_TIMEOUT_MS)
            if (!liveReady && streamUrl != null && bufferedSeconds >= MIN_BUFFER_SECONDS) {
                liveReady = true
                return@LaunchedEffect
            }
        }
    }

    // archiveProgressSeconds в ключах: плеер архива должен создаться заново, когда появятся сегменты.
    val player = remember(channel.streamUrl, liveReady, streamUrl, buffer, quality, archiveUri, archiveProgressSeconds >= ARCHIVE_MIN_SEGMENT_SECONDS, archiveGeneration, playerGeneration) {
        // Архив играем из файла и без live-конфигурации: у файла есть конец.
        if (channel.isArchive) {
            val archiveUrl = streamUrl ?: return@remember null
            playerCreatedAt = System.currentTimeMillis()
            return@remember runCatching {
                buildPlayer(
                    context = context,
                    url = archiveUrl,
                    buffer = buffer,
                    quality = quality,
                    live = false,
                    startPositionMs = archiveRestartPosition,
                )
            }.onFailure { error ->
                android.util.Log.e("SmotrishkaPlayer", "не удалось создать плеер архива", error)
            }.getOrNull().also { playerRef = it }
        }
        val url = streamUrl
        android.util.Log.d("SmotrishkaBuffer", "remember player: liveReady=$liveReady url=$url useBuffer=$useBuffer")
        if (!liveReady || url == null) return@remember null
        playerCreatedAt = System.currentTimeMillis()
        android.util.Log.d("SmotrishkaBuffer", "creating player for $url")
        runCatching { buildPlayer(context = context, url = url, buffer = buffer, quality = quality) }
            .onFailure { error ->
                android.util.Log.e("SmotrishkaPlayer", "не удалось создать плеер", error)
            }
            .getOrNull()
            .also { playerRef = it }
    }

    // Язык звука: русская дорожка по умолчанию, но выбор пользователя важнее.
    LaunchedEffect(player, audioLanguage) {
        if (player == null) return@LaunchedEffect
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setPreferredAudioLanguage(audioLanguage.code)
            .build()
    }

    // Индикатор качества: видно, хватает ли сети и сколько буфера в запасе.
    var stats by remember { mutableStateOf("") }

    // Текущее время в правом верхнем углу OSD, без секунд.
    var clock by remember { mutableStateOf(currentClockText()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentClockText()
            delay(CLOCK_REFRESH_MS)
        }
    }
    LaunchedEffect(player, controlsState.osdVisible) {
        if (player == null) return@LaunchedEffect
        while (controlsState.osdVisible) {
            val bufferMs = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0L)
            // Единственный честный запас — сколько уже готово впереди позиции воспроизведения;
            // «всего скачано» с позицией HLS-окна не сравнимо и давало растущую цифру.
            if (channel.isArchive && player.playbackState == Player.STATE_ENDED) {
                // Окно провайдера доиграло — берём следующее с того же места передачи.
                archiveWindowOffset += (player.duration.coerceAtLeast(30_000L) / 1000L)
                archiveGeneration++
                android.util.Log.d(
                    "SmotrishkaArchive",
                    "window ended, next offset=${archiveWindowOffset}s gen=$archiveGeneration",
                )
            }
            playbackPositionMs = player.currentPosition
            android.util.Log.d(
                "SmotrishkaArchive",
                "tick pos=${player.currentPosition} dur=${player.duration} state=${player.playbackState} buffered=${player.bufferedPosition} ready=$liveReady",
            )
            stats = PlayerStats.describe(
                height = player.videoFormat?.height,
                bitrate = player.videoFormat?.bitrate,
                bufferMs = bufferMs,
            ) + if (useBuffer && direct) " · буфер (авто)" else "" +
                if (unstable) " · нестабильный поток" else ""
            delay(STATS_REFRESH_MS)
        }
    }

    // Долгий сеанс разбирается по журналу: раз в минуту память, запас буфера и число сегментов.
    // Пятичасовой просмотр по заказу не воспроизвести, а по журналу видно рост после обычного.
    LaunchedEffect(player, server) {
        val active = player ?: return@LaunchedEffect
        val appContext = context.applicationContext
        while (true) {
            delay(SESSION_LOG_INTERVAL_MS)
            val runtime = Runtime.getRuntime()
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / MEGABYTE
            val aheadSeconds = (active.bufferedPosition - active.currentPosition).coerceAtLeast(0L) / 1000
            SessionLog.append(
                appContext,
                "память=${usedMb}МБ буфер=${aheadSeconds}с сегментов=${server?.entryCount ?: 0}",
            )
        }
    }

    // Архив не отдаётся: показываем причину и выход в эфир, а не «Готовим архив» заново.
    if (channel.isArchive && archiveError != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = archiveError!!,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGoLive) { Text("Перейти к прямому эфиру") }
        }
        return
    }

    if (player == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    archiveError != null -> archiveError!!
                    channel.isArchive -> "Готовим архив… $archivePercent %"
                    direct -> "Загрузка…"
                    else -> "Готовим буфер… $bufferedSeconds с"
                },
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        return
    }

    // Свёрнутое приложение не должно продолжать звук и качать поток: уход в фон останавливает,
    // возврат запускает заново с прямого эфира (замечание Александра 09.11.2026).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    player.pause()
                    player.stop()
                }

                Lifecycle.Event.ON_START -> {
                    if (player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
                        player.prepare()
                        player.play()
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Перемотка архива: тот же плеер получает окно с нового момента — без пересоздания,
    // иначе картинка отваливается, а звук продолжается от старого источника.
    LaunchedEffect(archiveWindowOffset) {
        if (!channel.isArchive || archiveWindowOffset <= 0L) return@LaunchedEffect
        val active = playerRef ?: return@LaunchedEffect
        val url = streamUrl ?: return@LaunchedEffect
        android.util.Log.d("SmotrishkaArchive", "seek window offset=$archiveWindowOffset url=$url")
        active.setMediaItem(MediaItem.fromUri(url))
        active.prepare()
        active.playWhenReady = true
    }

    // Архив всегда начинаем с начала передачи: Media3 иначе встаёт у края окна.
    LaunchedEffect(player) {
        val active = player ?: return@LaunchedEffect
        if (!channel.isArchive) return@LaunchedEffect
        active.seekTo(ARCHIVE_START_POSITION_MS)
        android.util.Log.d("SmotrishkaArchive", "start position forced to 0")
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Поток на слабом канале может оборваться: плеер сам повторяет попытку,
    // пользователю не нужно вручную нажимать «играть».
    // Адрес, который играет сейчас: повтор должен идти туда же, а не в поток провайдера —
    // в режиме буфера и архива это разные адреса.
    val currentStreamUrl by rememberUpdatedState(streamUrl)

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Плеер не сдаётся: человек мог уснуть перед телевизором, а поток вернётся сам.
                // Пауза растёт до потолка из настроек, попытки не заканчиваются.
                val attempt = recoveryAttempts + 1
                recoveryAttempts = attempt
                val pausedFor = PlayerRecovery.delayBefore(attempt, recoveryDelaySeconds.seconds)
                android.util.Log.w(
                    "SmotrishkaPlayer",
                    "ошибка потока ${error.errorCodeName}, попытка $attempt через $pausedFor",
                )
                scope.launch {
                    retryDelayMillis = pausedFor.inWholeMilliseconds
                    retryDeadlineMillis = System.currentTimeMillis() + pausedFor.inWholeMilliseconds
                    delay(pausedFor.inWholeMilliseconds)
                    retryDeadlineMillis = null
                    if (PlayerRecovery.shouldRecreate(attempt)) {
                        playerGeneration++
                    } else {
                        player.setMediaItem(PlayerLive.mediaItem(currentStreamUrl ?: channel.streamUrl))
                        player.prepare()
                        player.playWhenReady = true
                    }
                }
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }

            override fun onRenderedFirstFrame() {
                firstFrame = true
                android.util.Log.d(
                    "SmotrishkaBuffer",
                    "first frame after ${(System.currentTimeMillis() - playerCreatedAt) / 1000}s buffered=${bufferedSeconds}s",
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playerReady = playbackState == androidx.media3.common.Player.STATE_READY
                android.util.Log.d(
                    "SmotrishkaBuffer",
                    "player state=$playbackState position=${player.currentPosition} duration=${player.duration}",
                )
                if (playbackState == Player.STATE_READY) {
                    if (recoveryAttempts > 0) {
                        android.util.Log.i(
                            "SmotrishkaPlayer",
                            "поток восстановлен после $recoveryAttempts попыток",
                        )
                    }
                    recoveryAttempts = 0
                    retryDeadlineMillis = null
                }
                // Дошли до конца записанного: файл ещё растёт, продолжаем с той же позиции,
                // когда в буфере прибавится данных.
                if (playbackState == Player.STATE_ENDED) {
                    val position = player.currentPosition
                    scope.launch {
                        delay(END_OF_BUFFER_WAIT_MS)
                        player.prepare()
                        player.seekTo(position)
                        player.playWhenReady = true
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Плеер вообще не создался: пробуем снова с той же растущей паузой, без сообщений.
    LaunchedEffect(channel.streamUrl, streamUrl, liveReady, player, playerGeneration, recoveryAttempts) {
        if (streamUrl == null || player != null) return@LaunchedEffect
        if (!liveReady && !channel.isArchive) return@LaunchedEffect
        val attempt = recoveryAttempts + 1
        recoveryAttempts = attempt
        val pausedFor = PlayerRecovery.delayBefore(attempt, recoveryDelaySeconds.seconds)
        android.util.Log.w("SmotrishkaPlayer", "плеер не создан, попытка $attempt через $pausedFor")
        retryDelayMillis = pausedFor.inWholeMilliseconds
        retryDeadlineMillis = System.currentTimeMillis() + pausedFor.inWholeMilliseconds
        delay(pausedFor.inWholeMilliseconds)
        retryDeadlineMillis = null
        playerGeneration++
    }

    // Плеер создан, но так и не дошёл до READY: пересоздаём, иначе экран застынет на крутилке.
    LaunchedEffect(player, playerGeneration) {
        val active = player ?: return@LaunchedEffect
        delay(PlayerRecovery.FIRST_FRAME_TIMEOUT.inWholeMilliseconds)
        if (active.playbackState != Player.STATE_READY) {
            android.util.Log.w(
                "SmotrishkaPlayer",
                "плеер не дошёл до READY за ${PlayerRecovery.FIRST_FRAME_TIMEOUT}, пересоздаём",
            )
            recoveryAttempts += 1
            playerGeneration++
        }
    }

    // Сторож зависания: эфир играется из растущего файла, поэтому нельзя перескакивать
    // в начало (`seekToDefaultPosition`) — это и давало повтор куска. Продолжаем с той же позиции,
    // когда загрузчик допишет данные.
    LaunchedEffect(player) {
        var buffering = Duration.ZERO
        while (true) {
            delay(PlayerRecovery.WATCHDOG_INTERVAL)
            if (player.playbackState == Player.STATE_BUFFERING) {
                buffering += PlayerRecovery.WATCHDOG_INTERVAL
                if (PlayerRecovery.shouldRestart(buffering)) {
                    buffering = Duration.ZERO
                    val position = player.currentPosition
                    player.prepare()
                    player.seekTo(position)
                    player.playWhenReady = true
                }
            } else {
                buffering = Duration.ZERO
            }
        }
    }

    // Пульт: Compose рассылает клавиши только по цепочке фокуса. Пока на экране нет ни
    // одного сфокусированного элемента, onPreviewKeyEvent ниже не вызывается вовсе —
    // отсюда были «мёртвые» ОК и стрелки при работающей кнопке «назад» (её ловит навигация).
    val rootFocus = remember { FocusRequester() }

    // Фокус живёт на корне, пока не открыт OSD или панели: иначе пульт перестаёт
    // доходить до обработчика клавиш (например, после закрытия списка каналов).
    LaunchedEffect(controlsState.osdVisible, channelsVisible, recentsVisible) {
        if (!controlsState.osdVisible && !channelsVisible && !recentsVisible) {
            withFrameNanos { }
            runCatching { rootFocus.requestFocus() }
        }
    }

    // Плеер стартует сам: пауза возможна только по действию пользователя.
    LaunchedEffect(controlsState.muted) {
        player.volume = if (controlsState.muted) 0f else 1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            // Касание пустого места переключает OSD. Именно касание, а не clickable:
            // clickable на ТВ ловит и нажатие OK с пульта (фокус на этом же Box), из-за чего
            // OSD показывался обработчиком клавиши и тут же прятался кликом.
            .pointerInput(controlsState.osdVisible, channelsVisible) {
                detectTapGestures {
                    if (!channelsVisible) {
                        if (controlsState.osdVisible) controls.hideOsd() else controls.onInteraction()
                    }
                }
            }
            // Свайп по видео: вверх — следующий канал, вниз — предыдущий. Для пульта остаются
            // Up/Down; жест нужен тачу, где крестовины нет. При открытом OSD жесты принадлежат
            // его элементам (полоса программы, кнопки), иначе свайп случайно уводил в архив.
            .pointerInput(neighbours, controlsState.osdVisible) {
                if (controlsState.osdVisible) return@pointerInput
                var totalDy = 0f
                val thresholdPx = SWIPE_THRESHOLD.toPx()
                detectVerticalDragGestures(
                    onDragStart = { totalDy = 0f },
                    onDragEnd = {
                        when (ChannelSwipe.decide(totalDyPx = totalDy, thresholdPx = thresholdPx)) {
                            SwipeChannel.Next -> neighbours.second?.let(onSwitchChannel)
                            SwipeChannel.Previous -> neighbours.first?.let(onSwitchChannel)
                            SwipeChannel.None -> Unit
                        }
                        totalDy = 0f
                    },
                    onDragCancel = { totalDy = 0f },
                ) { change, dragAmount ->
                    totalDy += dragAmount
                    // Жест принадлежит нам: иначе он же прокрутит что-нибудь под видео.
                    change.consume()
                }
            }
            .onPreviewKeyEvent { event ->
                // «Назад»: короткое нажатие — выход из плеера, долгое удержание — панель
                // недавних каналов. Из плеера при удержании не выходим.
                // OSD открыт, но фокус остался на плеере: переводим его внутрь OSD,
                // иначе крестовина и OK «не отзываются».
                if (controlsState.osdVisible && !osdFocused && !channelsVisible && !recentsVisible) {
                    runCatching { osdFirstFocus.requestFocus() }
                }
                // Любое действие пультом при открытом OSD сбрасывает таймер автоскрытия:
                // раньше счёт шёл от открытия меню, и оно гасло прямо во время навигации.
                if (event.type == KeyEventType.KeyDown && controlsState.osdVisible) {
                    controls.onInteraction()
                }
                // Единое правило: пока открыт любой оверлей (список каналов, недавние,
                // телепрограмма), OK принадлежит ему, а не OSD плеера (указание 07.11.2026).
                val overlayOpen = channelsVisible || recentsVisible || programmeVisible
                if (event.key == Key.Back) {
                    // Выход больше не ждёт KeyUp: на этой приставке отпускание клавиши до
                    // обработчика не доходило, и из плеера нельзя было выйти вообще.
                    // Короткое нажатие выходит сразу; удержание (повторные KeyDown) открывает
                    // панель недавних каналов.
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.repeatCount == 0) {
                                backHeld = false
                                backPressJob?.cancel()
                                backPressJob = scope.launch {
                                    delay(LONG_BACK_PRESS_MS)
                                    backHeld = true
                                    // OSD прячем: иначе он рисуется поверх панели недавних
                                    // и перекрывает её.
                                    controls.hideOsd()
                                    recentsVisible = true
                                }
                            }
                            true
                        }
                        KeyEventType.KeyUp -> {
                            backPressJob?.cancel()
                            backPressJob = null
                            when {
                                // Удержание уже открыло панель — просто оставляем её.
                                backHeld -> {
                                    backHeld = false
                                    true
                                }
                                // Оверлей списка открыт: «назад» закрывает только его.
                                channelsVisible -> {
                                    channelsVisible = false
                                    true
                                }
                                // Панель открыта: «назад» скрывает её, из плеера не выходим.
                                recentsVisible -> {
                                    recentsVisible = false
                                    true
                                }
                                programmeVisible -> {
                                    programmeVisible = false
                                    true
                                }
                                else -> {
                                    onBack()
                                    true
                                }
                            }
                        }
                        else -> false
                    }
                } else if (
                    event.key == Key.Enter ||
                    event.key == Key.DirectionCenter ||
                    event.key == Key.NumPadEnter
                ) {
                    // OK: при скрытом OSD показывает его и забирает событие себе, чтобы оно
                    // не «проваливалось» в общую ветку и не проходило дважды. Если открыта
                    // панель телепрограммы, OK принадлежит её строкам.
                    if (!controlsState.osdVisible && !overlayOpen) {
                        if (event.type == KeyEventType.KeyDown) controls.onInteraction()
                        true
                    } else {
                        false
                    }
                } else if (event.key == Key.DirectionLeft) {
                    // Фокус внутри OSD: влево/вправо отдаём элементам OSD (кнопки, полоса
                    // программы), иначе перемотка полосой была недоступна с пульта.
                    if (osdFocused) {
                        return@onPreviewKeyEvent false
                    }
                    // Клавиша «влево» целиком наша: если отпустить её в общую ветку, KeyUp
                    // показывал OSD поверх открытого списка и прятал его собой.
                    when {
                        event.type != KeyEventType.KeyDown -> true
                        // Панель «Недавние» закрываем: список каналов открывается вместо неё.
                        recentsVisible -> {
                            recentsVisible = false
                            true
                        }
                        programmeVisible -> {
                            programmeVisible = false
                            true
                        }
                        else -> {
                            // Список перекрывает OSD: иначе кнопки и полоса программы
                            // остаются видны поверх него.
                            controls.hideOsd()
                            channelsVisible = true
                            true
                        }
                    }
                } else if (event.key == Key.DirectionRight) {
                    // «Вправо» при закрытом OSD открывает телепрограмму-surface.
                    // Когда OSD открыт, клавиша принадлежит элементам OSD (полоса программы
                    // перематывает вправо), поэтому событие не перехватываем.
                    if (channelsVisible || recentsVisible || programmeVisible || controlsState.osdVisible) {
                        false
                    } else {
                        if (event.type == KeyEventType.KeyDown) {
                            controls.hideOsd()
                            programmeVisible = true
                        }
                        true
                    }                } else if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) {
                    if (channelsVisible || recentsVisible || programmeVisible || controlsState.osdVisible) {
                        // Список каналов, недавние или OSD: крестовина принадлежит им.
                        false
                    } else {
                        if (event.type == KeyEventType.KeyDown) {
                            val target =
                                if (event.key == Key.DirectionUp) neighbours.first else neighbours.second
                            target?.let(onSwitchChannel)
                        }
                        true
                    }
                } else if (event.key == Key.ChannelUp || event.key == Key.ChannelDown) {
                    // Аппаратные кнопки «канал вверх/вниз» на пульте: переключают канал всегда,
                    // открыт OSD или закрыт (замечание Александра 09.11.2026).
                    if (channelsVisible || recentsVisible || programmeVisible) {
                        false
                    } else {
                        if (event.type == KeyEventType.KeyDown) {
                            val target =
                                if (event.key == Key.ChannelUp) neighbours.first else neighbours.second
                            target?.let(onSwitchChannel)
                        }
                        true
                    }
                } else if (
                    event.key == Key.MediaPlayPause ||
                    event.key == Key.MediaPlay ||
                    event.key == Key.MediaPause
                ) {
                    // Медиа-кнопки пульта: воспроизведение и пауза работают в любом состоянии OSD
                    // (замечание Александра 09.11.2026).
                    if (event.type == KeyEventType.KeyDown) {
                        playerRef?.let { active ->
                            when (event.key) {
                                Key.MediaPlay -> {
                                    active.play()
                                    if (!controls.state.value.playing) controls.togglePlayback()
                                }

                                Key.MediaPause -> {
                                    active.pause()
                                    if (controls.state.value.playing) controls.togglePlayback()
                                }

                                else -> togglePlayback(active, controls)
                            }
                        }
                    }
                    true
                } else {
                    // Пока список открыт, OSD не показываем: он рисуется поверх и закрывает его.
                    if (!channelsVisible && !recentsVisible) controls.onInteraction()
                    false
                }
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                // TextureView вместо PlayerView/SurfaceView: на ТВ-приставке t950s_be311
                // поверхность SurfaceView приходила нулевого размера (Media3 писал
                // surfaceSize 0x0 → videoDisabled), и кадра не было вовсе. TextureView живёт
                // внутри иерархии вью и рисуется под Compose-оверлеями.
                VideoSurfaceView(viewContext).also { it.bind(player) }
            },
            update = { view ->
                // При смене экземпляра плеера поверхность перепривязываем: иначе остаётся
                // чёрная картинка при работающем звуке.
                view.bind(player)
                view.applyAspect(aspectMode, videoSize)
            },
        )

        if (!firstFrame && !playerReady) {
            // Держим надпись до первого кадра: иначе между буфером и картинкой виден чёрный экран.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                    archiveError != null -> archiveError!!
                    channel.isArchive -> "Готовим архив… $archivePercent %"
                    direct -> "Загрузка…"
                    else -> "Готовим буфер… $bufferedSeconds с"
                },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        if (streamProblem) {
            // Плашка вместо чёрного экрана: управление и звук работают, поток догоняет сам,
            // как только сеть вернётся. Ничего не блокирует и не забирает фокус.
            Text(
                text = "Проблемы с подключением: не удаётся получить поток",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (programmeVisible) {
            // Телепрограмма текущего канала: то же оформление, что у списка каналов.
            val programmeState = rememberLazyListState()
            val currentProgramme = remember { FocusRequester() }
            val nowIndex = schedule.indexOfFirst { it.status == ProgrammeStatus.NOW }
            LaunchedEffect(programmeVisible, schedule.size) {
                val visible = programmeState.layoutInfo.visibleItemsInfo.size.takeIf { it > 0 } ?: 5
                programmeState.scrollToItem((nowIndex.coerceAtLeast(0) - visible / 2).coerceAtLeast(0))
                withFrameNanos { }
                runCatching { currentProgramme.requestFocus() }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
            ) {
                Text(
                    text = "Телепрограмма",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                if (schedule.isEmpty()) {
                    Text(
                        text = "Программы нет",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                } else {
                    // Две колонки: список передач слева, описание выбранной справа
                    // (решение Александра 09.11.2026).
                    var detailRow by remember {
                        mutableStateOf(
                            schedule.firstOrNull { it.status == ProgrammeStatus.NOW } ?: schedule.first(),
                        )
                    }
                    Row(modifier = Modifier.weight(1f)) {
                    LazyColumn(state = programmeState, modifier = Modifier.weight(0.56f)) {
                        itemsIndexed(
                            items = schedule,
                            key = { _, row -> "${row.programme.start}_${row.programme.title}" },
                        ) { index, row ->
                            val current = row.status == ProgrammeStatus.NOW
                            val canOpen = !(row.status == ProgrammeStatus.PAST && !row.hasArchive)
                            var rowFocused by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            rowFocused -> TvColors.accent
                                            current -> TvColors.rowSelectedStart
                                            else -> Color.Transparent
                                        },
                                    )
                                    .onFocusChanged {
                                        rowFocused = it.isFocused
                                        if (it.isFocused) detailRow = row
                                    }
                                    .then(if (index == nowIndex) Modifier.focusRequester(currentProgramme) else Modifier)
                                    .clickable {
                                        if (!canOpen) {
                                            // Прошедшая передача без архива: смотреть нечего —
                                            // OK просто убирает OSD с экрана (указание 06.11.2026).
                                            programmeVisible = false
                                            controls.hideOsd()
                                        } else {
                                            programmeVisible = false
                                            if (
                                                row.status == ProgrammeStatus.PAST ||
                                                row.status == ProgrammeStatus.NOW
                                            ) {
                                                onOpenArchive(channel.channelId, row.programme.start)
                                            }
                                        }
                                    }
                                    .focusable()
                                    .padding(vertical = 8.dp, horizontal = 8.dp),
                            ) {
                                Text(
                                    text = row.programme.start.atZone(ZoneId.systemDefault())
                                        .toLocalTime().toString().take(5) +
                                        " – " +
                                        row.programme.end.atZone(ZoneId.systemDefault())
                                            .toLocalTime().toString().take(5),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        rowFocused -> Color(0xFF100A1E).copy(alpha = 0.75f)
                                        !canOpen -> TvColors.programmePast.copy(alpha = 0.8f)
                                        else -> Color.White.copy(alpha = 0.65f)
                                    },
                                    maxLines = 1,
                                )
                                Text(
                                    text = row.programme.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    // Прошедшая передача без архива — серым: смотреть её нельзя.
                                    color = when {
                                        rowFocused -> Color(0xFF100A1E)
                                        current -> TvColors.accent
                                        !canOpen -> TvColors.programmePast
                                        row.status == ProgrammeStatus.PAST -> Color.White.copy(alpha = 0.55f)
                                        else -> Color.White
                                    },
                                    fontWeight = if (current || rowFocused) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    ProgrammeDetails(
                        row = detailRow,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 32.dp),
                    )
                    }
                }
            }
        }

        if (recentsVisible) {
            // Панель недавних каналов устроена так же, как список каналов: тот же LazyColumn,
            // тот же фокус и та же подсветка. Раньше здесь был простой Column, и пульт
            // не мог по строкам ходить.
            val recentsState = rememberLazyListState()
            val currentRecent = remember { FocusRequester() }
            LaunchedEffect(recentsVisible, recents.size) {
                withFrameNanos { }
                runCatching { currentRecent.requestFocus() }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
            ) {
                Text(
                    text = "Недавние каналы",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(state = recentsState, modifier = Modifier.weight(1f)) {
                    items(items = recents, key = { it.id }) { item ->
                        val current = item.id == channel.channelId.value
                        var rowFocused by remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (rowFocused) TvColors.accent else Color.Transparent)
                                .onFocusChanged { rowFocused = it.isFocused }
                                .then(
                                    if (current) Modifier.focusRequester(currentRecent) else Modifier,
                                )
                                .clickable {
                                    recentsVisible = false
                                    onOpenRecent(item.id)
                                }
                                .focusable()
                                .then(
                                    if (rowFocused) {
                                        Modifier.border(
                                            3.dp,
                                            TvColors.focusOutline,
                                            RoundedCornerShape(8.dp),
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChannelLogo(name = item.name, url = item.logoUrl, size = 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = when {
                                        rowFocused -> Color(0xFF100A1E)
                                        current -> TvColors.accent
                                        else -> Color.White
                                    },
                                    fontWeight = if (current || rowFocused) FontWeight.Medium else FontWeight.Normal,
                                    maxLines = 1,
                                )
                            }
                            // Текущая передача — как в списке каналов OSD, тонким шрифтом.
                            item.programmeTitle?.let { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Light,
                                    modifier = Modifier.padding(start = 42.dp),
                                    color = when {
                                        rowFocused -> Color(0xFF100A1E).copy(alpha = 0.75f)
                                        current -> TvColors.accent.copy(alpha = 0.8f)
                                        else -> Color.White.copy(alpha = 0.65f)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (channelsVisible) {
            // Полный список каналов категории: открывается «влево» при скрытом OSD,
            // выбор переключает канал и остаётся в плеере, «назад» закрывает список.
            val listState = rememberLazyListState()
            val currentRequester = remember { FocusRequester() }
            val currentIndex = channels.indexOfFirst { it.id == channel.channelId.value }
            LaunchedEffect(channelsVisible, channels.size) {
                // Фокус сразу на текущем канале: иначе пульт начинал бы с первой строки.
                if (currentIndex >= 0) listState.scrollToItem(currentIndex)
                withFrameNanos { }
                runCatching { currentRequester.requestFocus() }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
            ) {
                Text(
                    text = "Каналы",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    items(items = channels, key = { it.id }) { item ->
                        val current = item.id == channel.channelId.value
                        var rowFocused by remember { mutableStateOf(false) }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (rowFocused) TvColors.accent else Color.Transparent)
                                .onFocusChanged { rowFocused = it.isFocused }
                                .clickable {
                                    channelsVisible = false
                                    onSwitchChannel(item.id)
                                }
                                .then(
                                    if (current) {
                                        Modifier.focusRequester(currentRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                                .focusable()
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                        ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChannelLogo(
                                name = item.name,
                                url = item.logoUrl,
                                size = 32.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = when {
                                // Фокус в списке каналов поверх плеера: инверсия, чтобы
                                // строка под пультом читалась сразу.
                                rowFocused -> Color(0xFF100A1E)
                                current -> MaterialTheme.colorScheme.primary
                                else -> Color.White
                            },
                            fontWeight = if (current || rowFocused) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        }
                            item.programmeTitle?.let { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Light,
                                    modifier = Modifier.padding(start = 42.dp),
                                    color = when {
                                        rowFocused -> Color(0xFF100A1E).copy(alpha = 0.75f)
                                        current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                        else -> Color.White.copy(alpha = 0.65f)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Кнопки переключения каналов для тача: по краям, внизу, пока показан OSD.
        if (controlsState.osdVisible && !recentsVisible && !channelsVisible && !channel.isArchive) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                FilledIconButton(onClick = { neighbours.first?.let(onSwitchChannel) }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowLeft,
                        contentDescription = "Предыдущий канал",
                    )
                }
                FilledIconButton(onClick = { neighbours.second?.let(onSwitchChannel) }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = "Следующий канал",
                    )
                }
            }
        }

        if (recoveryAttempts > 0 && !playerReady) {
            // Крутилка вместо сообщения об ошибке: видно, что плеер жив и пробует снова,
            // а не завис навсегда. Пока идёт пауза — обратный отсчёт и прогресс ожидания.
            // Фокус не перехватывает — «назад» и OSD работают.
            val deadline = retryDeadlineMillis
            val totalDelay = retryDelayMillis
            var nowMillis by remember(deadline) { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(deadline) {
                if (deadline == null) return@LaunchedEffect
                while (true) {
                    nowMillis = System.currentTimeMillis()
                    if (nowMillis >= deadline) break
                    delay(RETRY_TICK_MS)
                }
            }
            val waitingSince = deadline?.minus(totalDelay)
            val elapsed = (nowMillis - (waitingSince ?: nowMillis)).coerceAtLeast(0L)
            val progress = PlayerRecovery.retryProgress(elapsed, totalDelay)
            val secondsLeft = PlayerRecovery.secondsLeft(elapsed, totalDelay)
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (deadline == null) {
                    CircularProgressIndicator()
                } else {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Восстанавливаем поток…",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (secondsLeft > 0) {
                        "Повтор через $secondsLeft с · попытка $recoveryAttempts"
                    } else {
                        "Подключаемся… · попытка $recoveryAttempts"
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val reduceMotion = LocalReduceMotion.current
        AnimatedVisibility(
            // OSD не прячем даже при ошибке потока: иначе на экране «Поток недоступен»
            // кнопка OK не даёт ничего, и из плеера нельзя ни выйти, ни переключиться.
            visible = controlsState.osdVisible,
            // Слабая приставка: OSD появляется сразу, без анимации.
            enter = if (reduceMotion) EnterTransition.None else fadeIn(),
            exit = if (reduceMotion) ExitTransition.None else fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .onFocusChanged { osdFocused = it.hasFocus },
            ) {
                // Меню плеера не масштабируется вместе с интерфейсом (решение 09.11.2026);
                // списки каналов и программы вне этой обёртки — они масштабируются.
                WithoutFontScale {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    PlayerOsdTop(
                        channelTitle = channel.title,
                        programmeTitle = channel.programmeTitle,
                        isArchive = channel.isArchive,
                        clock = clock,
                        favorite = channel.channelId.value in favorites,
                        aspectIcon = AspectIcons.of(aspectMode),
                        aspectLabel = AspectModes.label(aspectMode),
                        onBack = onBack,
                        onGoLive = onGoLive,
                        // Кнопка «Программа» в OSD открывает панель поверх видео (а не экран).
                        onOpenProgramme = {
                            controls.hideOsd()
                            programmeVisible = true
                        },
                        onToggleFavorite = onToggleFavorite,
                        onOpenRecents = { recentsVisible = true },
                        onCycleAspect = onCycleAspect,
                    )

                    Spacer(Modifier.weight(1f))

                    // Полоса текущей передачи: видно, сколько прошло, и можно перемотать.
                    val programmeStart = channel.programmeStart
                    val programmeEnd = channel.programmeEnd
                    var seekHint by remember { mutableStateOf<String?>(null) }
                    if (programmeStart != null && programmeEnd != null) {
                        val durationMs = java.time.Duration.between(programmeStart, programmeEnd).toMillis()
                        val nowOffsetMs = java.time.Duration.between(programmeStart, Instant.now()).toMillis()
                        val progress = ProgrammeProgress(programmeStart, programmeEnd, Instant.now())
                        // В архиве полоса идёт по позиции записи, а не по времени суток:
                        // иначе передача из прошлого выглядит завершённой и полоса залита.
                        // Общая позиция в архиве = проигранные окна + позиция внутри текущего.
                        val archivePositionMs = if (channel.isArchive) {
                            archiveWindowOffset * 1000L + player.currentPosition
                        } else {
                            null
                        }
                        val positionFraction = archivePositionMs?.let { position ->
                            if (durationMs <= 0L) 0f else (position.toFloat() / durationMs).coerceIn(0f, 1f)
                        } ?: progress.fraction
                        val fraction = seekPreviewFraction ?: positionFraction
                        val archivePositionText = archivePositionMs?.let { position ->
                            val seconds = (position / 1000).coerceAtLeast(0)
                            " · ${seconds / 60}:%02d".format(seconds % 60)
                        }.orEmpty()

                        Text(
                            text = "${progress.timeRange(ZoneId.systemDefault())} · " +
                                if (channel.isArchive) "воспроизведение$archivePositionText" else progress.remainingText(),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(4.dp))
                        ProgrammeSeekBar(
                            fraction = fraction,
                            onSeek = { fraction ->
                                val targetOffset = (fraction * durationMs).toLong()
                                val targetInstant = programmeStart.plusMillis(targetOffset)
                                val ageSeconds = java.time.Duration.between(targetInstant, Instant.now()).seconds

                                seekPreviewFraction = null
                                if (channel.isArchive) {
                                    // Архив: перезапрашиваем окно с нужного места, экран не перезапускаем.
                                    seekHint = "Переход к ${progress.timeRange(ZoneId.systemDefault())
                                        .substringBefore('-').trim()} + ${targetOffset / 60_000} мин"
                                    // Окно берём чуть раньше: иначе старт попадает в середину GOP
                                    // и видео остаётся чёрным, пока не дойдёт до ключевого кадра.
                                    archiveWindowOffset = (targetOffset / 1000 - ARCHIVE_SEEK_PAD_SECONDS)
                                        .coerceAtLeast(0L)
                                    archiveSeekInsideMs = ARCHIVE_SEEK_PAD_SECONDS * 1000L
                                    // Свежий экземпляр плеера: смена источника на живом экземпляре
                                    // оставляла чёрную картинку при работающем звуке.
                                    archiveGeneration++
                                } else if (ageSeconds > LIVE_WINDOW_SECONDS) {
                                    // За пределы живого окна тянется только архив.
                                    if (channel.archiveAvailable) {
                                        onOpenArchive(channel.channelId, targetInstant)
                                    } else {
                                        seekHint = "Архив недоступен: перемотка назад ограничена эфиром"
                                    }
                                } else {
                                    seekHint = null
                                    ProgrammeSeek.target(
                                        currentPositionMs = player.currentPosition,
                                        nowOffsetMs = nowOffsetMs,
                                        targetOffsetMs = targetOffset,
                                        isLive = !channel.archiveAvailable,
                                    )?.let(player::seekTo)
                                }
                            },
                            onPreview = { seekPreviewFraction = it },
                            onSeekBy = { deltaMs ->
                                if (channel.isArchive) {
                                    // В архиве шагаем по самой записи.
                                    player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
                                } else {
                                    // В эфире — по времени передачи, с оглядкой на живое окно.
                                    val targetOffset = (nowOffsetMs + deltaMs).coerceIn(0L, durationMs)
                                    ProgrammeSeek.target(
                                        currentPositionMs = player.currentPosition,
                                        nowOffsetMs = nowOffsetMs,
                                        targetOffsetMs = targetOffset,
                                        isLive = !channel.archiveAvailable,
                                    )?.let(player::seekTo)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(SEEK_BAR_HEIGHT),
                        )
                        seekHint?.let { hint ->
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Нижние кнопки — тот же OsdCircleButton, что и в верхней панели:
                        // у стандартных IconButton наше оформление фокуса не работало.
                        OsdCircleButton(
                            icon = Icons.Filled.Replay10,
                            contentDescription = "Назад на 10 секунд",
                            onClick = { seekBy(player, -SEEK_STEP_MS) },
                        )

                        OsdCircleButton(
                            icon = if (controlsState.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (controlsState.playing) "Пауза" else "Играть",
                            onClick = { togglePlayback(player, controls) },
                            // Фокус при открытии OSD — на этой кнопке.
                            focusRequester = osdFirstFocus,
                            size = PLAY_BUTTON_SIZE,
                        )

                        OsdCircleButton(
                            icon = Icons.Filled.Forward10,
                            contentDescription = "Вперёд на 10 секунд",
                            onClick = { seekBy(player, SEEK_STEP_MS) },
                        )

                        OsdCircleButton(
                            icon = if (controlsState.muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = if (controlsState.muted) "Включить звук" else "Выключить звук",
                            onClick = { toggleSound(player, controls) },
                        )
                    }

                    if (stats.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stats,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                }
            }
        }
    }
}

/**
 * Полоса перемотки по передаче: касание в точку перематывает поток.
 *
 * @param fraction доля прошедшего времени передачи, 0…1;
 * @param onSeek выбор новой доли;
 * @param modifier размеры полосы.
 */
@Composable
private fun ProgrammeSeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    onSeekBy: (Long) -> Unit = {},
    onPreview: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableStateOf(1f) }
    // Фокус на полосе: без явного оформления с пульта не видно, что перемотка «в руках».
    var focused by remember { mutableStateOf(false) }

    // Вся высота блока — зона касания пальцем; сама полоса тонкая и рисуется по центру.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SEEK_BAR_TOUCH_HEIGHT)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            // Пульт: полоса получает фокус, влево/вправо перематывают на шаг.
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekBy(-SEEK_STEP_MS); true }
                    Key.DirectionRight -> { onSeekBy(SEEK_STEP_MS); true }
                    else -> false
                }
            }
            // Палец: касание перематывает сразу, перетаскивание — с предпросмотром.
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                var last = fraction
                detectDragGestures(
                    onDragStart = { offset ->
                        last = (offset.x / widthPx).coerceIn(0f, 1f)
                        onPreview(last)
                    },
                    onDragEnd = { onSeek(last) },
                    onDragCancel = { onSeek(last) },
                ) { change, _ ->
                    change.consume()
                    last = (change.position.x / widthPx).coerceIn(0f, 1f)
                    onPreview(last)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Дорожка: тонкая, но по всей ширине. В фокусе она акцентная и выше — видно издалека.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (focused) SEEK_BAR_FOCUSED_HEIGHT else SEEK_BAR_HEIGHT)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (focused) {
                        TvColors.accent.copy(alpha = 0.45f)
                    } else {
                        Color.White.copy(alpha = 0.35f)
                    },
                )
                .then(if (focused) Modifier.border(2.dp, TvColors.focusOutline, RoundedCornerShape(4.dp)) else Modifier),
        )
        // Заполненная часть.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(if (focused) SEEK_BAR_FOCUSED_HEIGHT else SEEK_BAR_HEIGHT)
                .clip(RoundedCornerShape(4.dp))
                .background(if (focused) TvColors.accent else Color.White),
        )
        // Кружок текущего момента: за него тянут полосу, как в плеерах на телефоне.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val size = if (focused) THUMB_FOCUSED_SIZE else THUMB_SIZE
                    IntOffset((fraction * widthPx - size.toPx() / 2f).toInt(), 0)
                }
                .size(if (focused) THUMB_FOCUSED_SIZE else THUMB_SIZE)
                .clip(CircleShape)
                .background(if (focused) TvColors.accent else Color.White)
                // Светлая обводка: кружок должен читаться и на светлом кадре.
                .then(
                    if (focused) {
                        Modifier.border(2.dp, Color.White, CircleShape)
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

/**
 * Живой кадр канала для превью: видео со звуком, без органов управления.
 *
 * Плеер один на всю область превью: при смене канала меняется только источник, сам плеер
 * не пересоздаётся — иначе на быстрой прокрутке списка превью оставалось чёрным. Пока новый
 * канал не показал кадр, виден логотип; при обрыве потока превью повторяет попытку само.
 *
 * @param url адрес потока;
 * @param bufferSeconds сколько секунд держать в буфере;
 * @param modifier размеры области;
 * @param fallback что показать, пока видео не готово.
 */
@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun LivePreview(
    url: String,
    bufferSeconds: Int,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {},
    // Сообщает наружу, есть ли кадры потока: пока их нет, плашка «Сейчас в эфире»
    // показываться не должна (замечание Александра 09.11.2026).
    onFramesReady: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val buffer = remember(bufferSeconds) { PlayerBuffer(bufferSeconds) }
    var ready by remember { mutableStateOf(false) }

    val player = remember { buildPlayer(context = context, url = url, buffer = buffer) }

    // Смена канала — только новый источник: сам плеер остаётся живым.
    LaunchedEffect(url) {
        ready = false
        onFramesReady(false)
        // Старый кадр не должен висеть, пока готовится новый поток: гасим прежнюю сессию,
        // иначе Live Preview показывает предыдущий канал (замечание Александра 09.11.2026).
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(PlayerLive.mediaItem(url))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(player) {
        var attempts = 0
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                ready = true
                onFramesReady(true)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (attempts >= PlayerRecovery.MAX_ATTEMPTS) return
                attempts++
                scope.launch {
                    delay(PlayerRecovery.delayBefore(attempts))
                    player.prepare()
                    player.playWhenReady = true
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) attempts = 0
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // В фоне превью не качает поток и не играет звук; на возврате запускается с живого края
    // (замечание Александра 09.11.2026).
    val previewLifecycle = LocalLifecycleOwner.current
    DisposableEffect(previewLifecycle, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    player.pause()
                    player.stop()
                }

                Lifecycle.Event.ON_START -> {
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                        player.play()
                    }
                }

                else -> Unit
            }
        }
        previewLifecycle.lifecycle.addObserver(observer)
        onDispose { previewLifecycle.lifecycle.removeObserver(observer) }
    }

    // Сторож зависания: превью не должно навсегда остаться на одном кадре или чёрным.
    // Молча перезапускаем поток с живого края — это короткая пауза, а не «залипание».
    LaunchedEffect(player) {
        var buffering = Duration.ZERO
        while (true) {
            delay(PlayerRecovery.WATCHDOG_INTERVAL)
            if (player.playbackState == Player.STATE_BUFFERING) {
                buffering += PlayerRecovery.WATCHDOG_INTERVAL
                if (buffering >= PREVIEW_RESTART_AFTER) {
                    buffering = Duration.ZERO
                    player.setMediaItem(PlayerLive.mediaItem(url))
                    player.prepare()
                    player.playWhenReady = true
                }
            } else {
                buffering = Duration.ZERO
            }
        }
    }

    Box(modifier = modifier) {
        if (!ready) {
            fallback()
        }
        AndroidView(
            // Пока новый поток не отрисовал первый кадр, видео скрыто: иначе поверх
            // заглушки виден последний кадр прежнего канала.
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (ready) 1f else 0f),
            factory = { viewContext ->
                // TextureView: SurfaceView в Compose на этом устройстве не получает кадр
                // до касания экрана, и картинка теряется при пересоздании вьюхи.
                (android.view.LayoutInflater.from(viewContext)
                    .inflate(R.layout.player_surface, null) as PlayerView).apply {
                    useController = false
                    this.player = player
                }
            },
        )
    }
}

/**
 * Создаёт настроенный ExoPlayer.
 *
 * @param context контекст;
 * @param url адрес потока;
 * @param buffer параметры буфера из настроек.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun buildPlayer(
    context: android.content.Context,
    url: String,
    buffer: PlayerBuffer,
    quality: VideoQuality = VideoQuality.AUTO,
    live: Boolean = true,
    startPositionMs: Long = 0L,
): ExoPlayer {
    val dataSourceFactory = OkHttpDataSource.Factory(PlayerHttp.client)

    val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
        as? android.app.ActivityManager
    val lowRam = activityManager?.isLowRamDevice == true

    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            buffer.minBufferMs,
            buffer.maxBufferMs,
            buffer.forPlaybackMs,
            buffer.forPlaybackAfterRebufferMs,
        )
        // Потолок по байтам: при достижении плеер выбрасывает старые сегменты, а не копит их.
        .setTargetBufferBytes(PlayerMemory.targetBufferBytes(lowRam))
        .setPrioritizeTimeOverSizeThresholds(false)
        .build()

    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(LOAD_RETRIES))

    val trackSelector = DefaultTrackSelector(context).also { PlayerQuality.apply(it, quality) }

    // Расширения (FFmpeg-декодер) включаются, когда аппаратного декодера нет:
    // каналы с AC-3 иначе молчат на устройствах без поддержки этого кодека.
    val renderersFactory = DefaultRenderersFactory(context)
        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

    return ExoPlayer.Builder(context)
        .setRenderersFactory(renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .setTrackSelector(trackSelector)
        .build()
        .apply {
            // Диагностика Media3: выбранные дорожки, инициализация кодеков, первый кадр, ошибки.
            // Нужна на слабых приставках, где «Загрузка…» висит без явной ошибки.
            addAnalyticsListener(EventLogger())
        }
        .apply {
            // Живой отступ нужен только сетевому потоку; для локального сегмента он включает
            // «живой» режим и плеер бесконечно буферизуется.
            // Архив — не эфир: live-конфигурация заставляла плеер прыгать к «краю» окна.
            val item = if (url.startsWith("file://") || !live) {
                MediaItem.fromUri(url)
            } else {
                PlayerLive.mediaItem(url)
            }
            setMediaItem(item, if (live) 0L else startPositionMs)
            prepare()
            playWhenReady = true
        }
}

/** Переключает паузу и сообщает логике OSD новое намерение. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun togglePlayback(player: ExoPlayer, controls: PlayerControls) {
    val playing = !controls.state.value.playing
    if (playing) player.play() else player.pause()
    controls.togglePlayback()
}

/** Переключает звук и сообщает логике OSD новое состояние. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun toggleSound(player: ExoPlayer, controls: PlayerControls) {
    val muted = !controls.state.value.muted
    player.volume = if (muted) 0f else 1f
    controls.toggleSound()
}

/**
 * Скрывает системные панели на время экрана плеера и возвращает их при выходе.
 *
 * Скрывает повторно: при смене поверхности видео (переключение канала) ColorOS возвращает и
 * строку состояния, и панель навигации, поэтому одного скрытия при входе недостаточно.
 */
@Composable
private fun ImmersiveMode() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            val job = CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    delay(IMMERSIVE_REAPPLY_MS)
                }
            }
            onDispose {
                job.cancel()
                // Панели НЕ возвращаем: приложение полноэкранное на всех экранах, и именно
                // этот show() показывал статусные строки после выхода из плеера
                // (указание Александра 09.11.2026).
            }
        }
    }
}

/** Перематывает поток на заданное смещение в пределах буфера и длительности. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun seekBy(player: ExoPlayer, offsetMs: Long) {
    val target = (player.currentPosition + offsetMs).coerceAtLeast(0L)
    player.seekTo(target)
}

/** Состояние и таймер OSD, живущие вместе с экраном плеера. */
@Composable
private fun rememberPlayerControls(hideSeconds: Int): PlayerControls {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Время показа меню приходит из настроек: 4, 8, 15 или 30 секунд.
    return remember(scope, hideSeconds) {
        PlayerControls(scope = scope, hideDelay = hideSeconds.seconds)
    }
}

/** Порог вертикального свайпа по видео: короче — считается случайным касанием. */
private val SWIPE_THRESHOLD = 60.dp

private const val SEEK_STEP_MS = 10_000L

/** Размер центральной кнопки «пауза / играть». */
private val PLAY_BUTTON_SIZE = 64.dp

/** Сколько раз плеер повторит загрузку сегмента, прежде чем показать ошибку. */
private const val LOAD_RETRIES = 6

/** Как часто обновляется индикатор качества в OSD. */
private const val STATS_REFRESH_MS = 1_000L

/** Толщина полосы перемотки по передаче. */
/** Высота зоны касания полосы прогресса. */
/** Высота зоны касания полосы прогресса: под палец, а не под толщину полосы. */
private val SEEK_BAR_TOUCH_HEIGHT = 48.dp

private val SEEK_BAR_HEIGHT = 6.dp

/** Высота дорожки в фокусе: по ней видно, что перемотка доступна с пульта. */
private val SEEK_BAR_FOCUSED_HEIGHT = 12.dp

/** Сколько секунд назад ещё считается «живым» эфиром без архива. */
private const val LIVE_WINDOW_SECONDS = 60L

/** Через сколько превью молча перезапускается, если поток встал (три интервала сторожа). */
private val PREVIEW_RESTART_AFTER = PlayerRecovery.WATCHDOG_INTERVAL * 3

/** Порог удержания «назад» для панели недавних каналов. */
private const val LONG_BACK_PRESS_MS = 600L

/** Как часто проверяем поведение прямого потока. */
/** Диаметр кружка-ползунка на полосе прогресса. */
private val THUMB_SIZE = 18.dp

/** Кружок в фокусе крупнее и акцентный — видно, где перемотка. */
private val THUMB_FOCUSED_SIZE = 26.dp

/** Сколько ждём первый сегмент архива, прежде чем сказать, что его нет. */
private const val ARCHIVE_START_TIMEOUT_MS = 25_000L

/** Запас перед точкой перемотки: попадаем в ключевой кадр, а не в чёрный экран. */
private const val ARCHIVE_SEEK_PAD_SECONDS = 12L

/** Сколько секунд архива ждём до создания плеера (пустой плейлист Media3 не пережёвывает). */
private const val ARCHIVE_MIN_SEGMENT_SECONDS = 30L

/** Архив начинаем с нуля записи. */
private const val ARCHIVE_START_POSITION_MS = 0L

private const val ANOMALY_POLL_MS = 1_000L

/** Как часто повторяем скрытие системных строк. */
private const val IMMERSIVE_REAPPLY_MS = 500L

/** Как часто перепроверяем передачу для OSD. */
private const val OSD_PROGRAMME_TICK_MS = 20_000L

/**
 * Правая половина оверлея телепрограммы: название, время и описание выбранной передачи.
 *
 * @param row выбранная строка, `null` — показывать нечего;
 * @param modifier размеры панели.
 */
@Composable
private fun ProgrammeDetails(row: ProgrammeRowUi?, modifier: Modifier = Modifier) {
    if (row == null) return
    val formatter = remember { java.time.format.DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    Column(modifier = modifier.fillMaxHeight()) {
        Text(
            text = row.programme.title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatter.format(row.programme.start.atZone(zone)) +
                " – " +
                formatter.format(row.programme.end.atZone(zone)),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = row.programme.description?.takeIf { it.isNotBlank() } ?: "Описания нет",
            style = MaterialTheme.typography.bodyMedium,
            color = if (row.programme.description.isNullOrBlank()) {
                Color.White.copy(alpha = 0.45f)
            } else {
                Color.White.copy(alpha = 0.9f)
            },
        )
    }
}

/** Запас, при котором начинаем показ эфира из буфера: реальный запас, а не «загружено позади». */
private const val START_BUFFER_SECONDS = 20L

/** Сколько секунд эфира в одном сегменте провайдера. */
private const val SEGMENT_SECONDS = 10L

/** Предел ожидания запаса: на медленном канале стартуем с тем, что успели накопить. */
private const val START_TIMEOUT_MS = 10_000L

/** Минимум запаса, ниже которого не начинаем: иначе старт «в край» снова даёт остановки. */
private const val MIN_BUFFER_SECONDS = 20L

/** Пауза перед продолжением, когда доиграли до конца растущего файла. */
private const val END_OF_BUFFER_WAIT_MS = 1_000L

/** Как часто обновляются часы в OSD. */
private const val CLOCK_REFRESH_MS = 15_000L

/** Как часто состояние плеера пишется в журнал долгого сеанса. */
private const val SESSION_LOG_INTERVAL_MS = 60_000L

/** Байт в мегабайте: память в журнале пишется в МБ. */
private const val MEGABYTE = 1024L * 1024

/** Как часто обновляется обратный отсчёт восстановления потока. */
private const val RETRY_TICK_MS = 200L

/** Текущее время без секунд для правого верхнего угла OSD. */
private fun currentClockText(): String =
    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

/**
 * Поверхность видео на `TextureView`.
 *
 * `PlayerView` выводит кадр через `SurfaceView`, который композитится отдельным слоем под окном.
 * На ТВ-приставке `t950s_be311` его поверхность приходила нулевого размера, Media3 писал
 * `surfaceSize 0x0`, отключал видео (`videoDisabled`) и кадра не было вообще. `TextureView`
 * рисуется внутри иерархии вью, поэтому не зависит от слоёв окна и виден под Compose-оверлеями.
 *
 * Масштаб кадра считается [AspectTransform] — по тем же режимам, что были у `PlayerView`.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private class VideoSurfaceView(context: android.content.Context) : TextureView(context) {

    private var boundPlayer: ExoPlayer? = null

    /** Привязывает поверхность к плееру, отвязывая прежний экземпляр. */
    fun bind(target: ExoPlayer) {
        if (boundPlayer === target) return
        boundPlayer?.clearVideoTextureView(this)
        boundPlayer = target
        target.setVideoTextureView(this)
    }

    private var lastMode: AspectMode = AspectMode.FIT
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    /** Пересчитывает масштаб после изменения размера поверхности. */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && lastWidth > 0 && lastHeight > 0) {
            applyAspect(lastMode, VideoSize(lastWidth, lastHeight))
        }
    }

    /** Применяет режим кадра (вписать, растянуть, обрезать, 1:1). */
    fun applyAspect(mode: AspectMode, size: VideoSize) {
        lastMode = mode
        lastWidth = size.width
        lastHeight = size.height
        val (scaleX, scaleY) = AspectTransform.scale(
            mode = mode,
            videoWidth = size.width,
            videoHeight = size.height,
            viewWidth = width,
            viewHeight = height,
        )
        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, width / 2f, height / 2f)
        setTransform(matrix)
    }
}
