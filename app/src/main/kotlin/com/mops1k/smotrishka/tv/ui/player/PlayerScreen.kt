package com.mops1k.smotrishka.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.focusable
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
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.ui.PlayerView
import com.mops1k.smotrishka.tv.SessionLog
import com.mops1k.smotrishka.tv.core.data.AspectMode
import com.mops1k.smotrishka.tv.core.data.AudioLanguage
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.ui.home.ProgrammeProgress
import com.mops1k.smotrishka.tv.ui.player.osd.PlayerOsdTop
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
    val recents by viewModel.recents.collectAsStateWithLifecycle(initialValue = emptyList<Pair<String, String>>())
    val favorites by viewModel.favorites.collectAsStateWithLifecycle(initialValue = emptySet<String>())
    val bufferPreferred by viewModel.bufferPreferred.collectAsStateWithLifecycle(initialValue = false)
    val unstable by viewModel.unstable.collectAsStateWithLifecycle(initialValue = false)
    val neighbours by viewModel.neighbours.collectAsStateWithLifecycle(initialValue = null to null)
    val channelsInScope by viewModel.channelsInScope
        .collectAsStateWithLifecycle(initialValue = emptyList<ChannelRef>())

    // Передача в OSD перепроверяется, пока плеер открыт: иначе к концу передачи остаётся
    // «Программа закончилась» и заполненная полоса.
    LaunchedEffect(Unit) {
        while (true) {
            delay(OSD_PROGRAMME_TICK_MS)
            viewModel.refreshProgramme()
        }
    }
    val retryAttempts by viewModel.retryAttempts.collectAsStateWithLifecycle()
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
                retryAttempts = retryAttempts,
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
private fun PlayerContent(
    channel: PlayerUiState.Content,
    bufferSeconds: Int,
    quality: VideoQuality,
    retryAttempts: Int,
    audioLanguage: AudioLanguage,
    direct: Boolean,
    onBack: () -> Unit,
    onOpenProgramme: (ChannelId) -> Unit,
    aspectMode: AspectMode,
    onCycleAspect: () -> Unit,
    recents: List<Pair<String, String>>,
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
) {
    val context = LocalContext.current
    var recentsVisible by remember { mutableStateOf(false) }
    var channelsVisible by remember { mutableStateOf(false) }
    var backPressedAt by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val controls = rememberPlayerControls()
    val controlsState by controls.state.collectAsStateWithLifecycle()
    val buffer = remember(bufferSeconds) { PlayerBuffer(bufferSeconds) }

    var unavailable by remember(channel.streamUrl) { mutableStateOf(false) }

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
    var autoBuffer by remember(channel.streamUrl) { mutableStateOf(bufferPreferred) }
    var playerRef by remember(channel.streamUrl) { mutableStateOf<ExoPlayer?>(null) }
    val useBuffer = !direct || autoBuffer
    // Связь пропала: показываем плашку поверх видео, но экран не блокируем —
    // поток догонит сам, как только сеть вернётся.
    var streamProblem by remember(channel.streamUrl) { mutableStateOf(false) }

    // Автодетект: пока играем прямым потоком, следим за откатами и затяжными остановками.
    // Набралось достаточно признаков — переводим этот канал на буфер, не спрашивая пользователя.
    LaunchedEffect(playerRef, useBuffer) {
        val active = playerRef ?: return@LaunchedEffect
        if (useBuffer) return@LaunchedEffect
        val events = mutableListOf<Long>()
        var lastPosition = 0L
        var bufferingSince = 0L
        while (true) {
            delay(ANOMALY_POLL_MS)
            val now = System.currentTimeMillis()
            val position = active.currentPosition
            // Чёрный экран без первого кадра — тоже признак проблемы: переключаем на буфер.
            if (!firstFrame && now - playerCreatedAt >= StreamAnomaly.STALL_MILLIS * 2) events += now
            if (StreamAnomaly.isRewind(lastPosition, position)) events += now
            // Обычную буферизацию признаком не считаем: она бывает и на здоровых каналах.
            bufferingSince = 0L
            lastPosition = position
            if (StreamAnomaly.shouldSwitch(events, now)) {
                autoBuffer = true
                // Запоминаем: в следующий раз канал сразу пойдёт через буфер.
                onRememberBuffered()
                return@LaunchedEffect
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
        val loader = LiveBufferLoader(PlayerHttp.client, context.cacheDir)
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
    val player = remember(channel.streamUrl, liveReady, streamUrl, buffer, quality, archiveUri, archiveProgressSeconds >= ARCHIVE_MIN_SEGMENT_SECONDS, archiveGeneration) {
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
            }.onFailure { unavailable = true }.getOrNull().also { playerRef = it }
        }
        val url = streamUrl
        android.util.Log.d("SmotrishkaBuffer", "remember player: liveReady=$liveReady url=$url useBuffer=$useBuffer")
        if (!liveReady || url == null) return@remember null
        playerCreatedAt = System.currentTimeMillis()
        android.util.Log.d("SmotrishkaBuffer", "creating player for $url")
        runCatching { buildPlayer(context = context, url = url, buffer = buffer, quality = quality) }
            .onFailure { unavailable = true }
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

    // Свёрнутое приложение не должно продолжать звук: уход в фон ставит паузу.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
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
    DisposableEffect(player) {
        var attempts = 0
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (!PlayerRecovery.canRetry(attempts, retryAttempts)) {
                    unavailable = true
                    return
                }
                attempts++
                scope.launch {
                    delay(PlayerRecovery.delayBefore(attempts))
                    player.setMediaItem(PlayerLive.mediaItem(channel.streamUrl))
                    player.prepare()
                    player.playWhenReady = true
                }
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
                    attempts = 0
                    unavailable = false
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

    // Плеер стартует сам: пауза возможна только по действию пользователя.
    LaunchedEffect(controlsState.muted) {
        player.volume = if (controlsState.muted) 0f else 1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // Касание пустого места переключает OSD: показано — скрываем, скрыто — показываем.
                // Пока открыт список каналов, касание OSD не трогает — иначе он закроет список.
                if (!channelsVisible) {
                    if (controls.state.value.osdVisible) controls.hideOsd() else controls.onInteraction()
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
                if (event.key == Key.Back) {
                    val now = System.currentTimeMillis()
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            backPressedAt = now
                            true
                        }
                        KeyEventType.KeyUp -> {
                            val held = now - backPressedAt
                            when {
                                // Оверлей списка открыт: «назад» закрывает только его.
                                channelsVisible -> {
                                    channelsVisible = false
                                    true
                                }
                                // Панель открыта: «назад» только скрывает её, из плеера не выходим.
                                recentsVisible -> {
                                    recentsVisible = false
                                    true
                                }
                                held >= LONG_BACK_PRESS_MS -> {
                                    controls.onInteraction()
                                    recentsVisible = true
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
                } else if (event.key == Key.DirectionLeft) {
                    // Клавиша «влево» целиком наша: если отпустить её в общую ветку, KeyUp
                    // показывал OSD поверх открытого списка и прятал его собой.
                    val ownKey = !controlsState.osdVisible && channels.isNotEmpty()
                    when {
                        !ownKey -> false
                        event.type != KeyEventType.KeyDown -> true
                        else -> {
                            // Список перекрывает OSD: иначе кнопки и полоса программы
                            // остаются видны поверх него.
                            controls.hideOsd()
                            channelsVisible = true
                            true
                        }
                    }
                } else if (event.key == Key.DirectionUp || event.key == Key.DirectionDown) {
                    if (channelsVisible) {
                        // Пока открыт список, крестовина ходит по строкам, а не переключает канал.
                        false
                    } else {
                        if (event.type == KeyEventType.KeyDown) {
                            val target =
                                if (event.key == Key.DirectionUp) neighbours.first else neighbours.second
                            target?.let(onSwitchChannel)
                        }
                        true
                    }
                } else {
                    // Пока список открыт, OSD не показываем: он рисуется поверх и закрывает его.
                    if (!channelsVisible) controls.onInteraction()
                    false
                }
            },
    ) {
        run {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        useController = false
                        this.player = player
                    }
                },
                update = { view ->
                    // При смене экземпляра плеера (перемотка архива) поверхность перепривязываем:
                    // иначе остаётся чёрная картинка при работающем звуке.
                    if (view.player !== player) view.player = player
                    view.resizeMode = AspectModes.resizeMode(aspectMode)
                },
            )
        }

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

        if (recentsVisible) {
            // Панель недавних каналов: свежие сверху, выбор открывает канал и остаётся в плеере.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
            ) {
                Text(
                    text = "Недавние каналы",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                recents.forEach { (id, name) ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                recentsVisible = false
                                onOpenRecent(id)
                            }
                            .padding(vertical = 8.dp),
                    )
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
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (current) MaterialTheme.colorScheme.primary else Color.White,
                            fontWeight = if (current) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
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
                                .padding(vertical = 8.dp),
                        )
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

        if (unavailable) {
            UnavailableStream(onBack = onBack)
        }

        AnimatedVisibility(
            visible = controlsState.osdVisible && !unavailable,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
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
                        onOpenProgramme = { onOpenProgramme(channel.channelId) },
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
                        IconButton(onClick = { seekBy(player, -SEEK_STEP_MS) }) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "Назад на 10 секунд",
                                tint = Color.White,
                            )
                        }

                        FilledIconButton(
                            onClick = { togglePlayback(player, controls) },
                            modifier = Modifier.size(PLAY_BUTTON_SIZE),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Icon(
                                imageVector = if (controlsState.playing) {
                                    Icons.Filled.Pause
                                } else {
                                    Icons.Filled.PlayArrow
                                },
                                contentDescription = if (controlsState.playing) "Пауза" else "Играть",
                            )
                        }

                        IconButton(onClick = { seekBy(player, SEEK_STEP_MS) }) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "Вперёд на 10 секунд",
                                tint = Color.White,
                            )
                        }

                        IconButton(onClick = { toggleSound(player, controls) }) {
                            Icon(
                                imageVector = if (controlsState.muted) {
                                    Icons.Filled.VolumeOff
                                } else {
                                    Icons.Filled.VolumeUp
                                },
                                contentDescription = if (controlsState.muted) "Включить звук" else "Выключить звук",
                                tint = Color.White,
                            )
                        }
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

/**
 * Сообщение о недоступном потоке.
 *
 * @param onBack возврат к списку каналов.
 */
@Composable
private fun UnavailableStream(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Поток недоступен",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack) { Text("Назад к каналам") }
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

    // Вся высота блока — зона касания пальцем; сама полоса тонкая и рисуется по центру.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SEEK_BAR_TOUCH_HEIGHT)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            // Пульт: полоса получает фокус, влево/вправо перематывают на шаг.
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
        // Дорожка: тонкая, но по всей ширине.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SEEK_BAR_HEIGHT)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.35f)),
        )
        // Заполненная часть.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(SEEK_BAR_HEIGHT)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White),
        )
        // Кружок текущего момента: за него тянут полосу, как в плеерах на телефоне.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((fraction * widthPx - THUMB_SIZE.toPx() / 2f).toInt(), 0) }
                .size(THUMB_SIZE)
                .clip(CircleShape)
                .background(Color.White),
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
fun LivePreview(
    url: String,
    bufferSeconds: Int,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val buffer = remember(bufferSeconds) { PlayerBuffer(bufferSeconds) }
    var ready by remember { mutableStateOf(false) }

    val player = remember { buildPlayer(context = context, url = url, buffer = buffer) }

    // Смена канала — только новый источник: сам плеер остаётся живым.
    LaunchedEffect(url) {
        ready = false
        player.setMediaItem(PlayerLive.mediaItem(url))
        player.prepare()
        player.playWhenReady = true
    }

    DisposableEffect(player) {
        var attempts = 0
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                ready = true
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
            modifier = Modifier.fillMaxSize(),
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
private fun togglePlayback(player: ExoPlayer, controls: PlayerControls) {
    val playing = !controls.state.value.playing
    if (playing) player.play() else player.pause()
    controls.togglePlayback()
}

/** Переключает звук и сообщает логике OSD новое состояние. */
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
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

/** Перематывает поток на заданное смещение в пределах буфера и длительности. */
private fun seekBy(player: ExoPlayer, offsetMs: Long) {
    val target = (player.currentPosition + offsetMs).coerceAtLeast(0L)
    player.seekTo(target)
}

/** Состояние и таймер OSD, живущие вместе с экраном плеера. */
@Composable
private fun rememberPlayerControls(): PlayerControls {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember(scope) { PlayerControls(scope = scope, hideDelay = OSD_HIDE_DELAY) }
}

private val OSD_HIDE_DELAY = 4.seconds

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

/** Сколько секунд назад ещё считается «живым» эфиром без архива. */
private const val LIVE_WINDOW_SECONDS = 60L

/** Через сколько превью молча перезапускается, если поток встал (три интервала сторожа). */
private val PREVIEW_RESTART_AFTER = PlayerRecovery.WATCHDOG_INTERVAL * 3

/** Порог удержания «назад» для панели недавних каналов. */
private const val LONG_BACK_PRESS_MS = 600L

/** Как часто проверяем поведение прямого потока. */
/** Диаметр кружка-ползунка на полосе прогресса. */
private val THUMB_SIZE = 18.dp

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

/** Текущее время без секунд для правого верхнего угла OSD. */
private fun currentClockText(): String =
    java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
