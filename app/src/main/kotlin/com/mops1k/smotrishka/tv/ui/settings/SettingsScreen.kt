package com.mops1k.smotrishka.tv.ui.settings

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mops1k.smotrishka.tv.core.data.AudioLanguage
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.core.model.EpgGuide
import com.mops1k.smotrishka.tv.ui.common.LocalToastController
import com.mops1k.smotrishka.tv.ui.player.AspectModes
import com.mops1k.smotrishka.tv.ui.shell.AppShell
import com.mops1k.smotrishka.tv.ui.shell.ShellDestination
import androidx.compose.foundation.layout.width

/**
 * Настройки: страница карточек — «Источник каналов», «Воспроизведение», «Программа передач»
 * и «Плеер».
 *
 * Карточки повторяют макет: иконка с цветной подложкой, заголовок и подпись, внутри — строки
 * «параметр — значение — действие — подсказка» и группы вариантов.
 *
 * @param onChangeSource переход к смене источника;
 * @param onOpenAbout переход в «О приложении»;
 * @param onBack возврат на главный экран;
 * @param viewModel экранная модель.
 */
@Composable
fun SettingsScreen(
    onChangeSource: () -> Unit,
    onOpenAbout: () -> Unit = {},
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bufferSeconds by viewModel.bufferSeconds.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val updating by viewModel.updating.collectAsStateWithLifecycle()
    val fileSource by viewModel.fileSource.collectAsStateWithLifecycle()
    val quality by viewModel.videoQuality.collectAsStateWithLifecycle()
    val recoveryDelaySeconds by viewModel.recoveryDelaySeconds.collectAsStateWithLifecycle()
    val epgWindowHours by viewModel.epgWindowHours.collectAsStateWithLifecycle()
    val globalEpgShift by viewModel.globalEpgShift.collectAsStateWithLifecycle()
    // Языковой фильтр списков: выбранный язык и показ каналов без метки.
    val channelLanguage by viewModel.channelLanguage.collectAsStateWithLifecycle()
    val languageFromName by viewModel.languageFromName.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    // Размер шрифта интерфейса: общий масштаб для всех текстов приложения.
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val audioLanguage by viewModel.audioLanguage.collectAsStateWithLifecycle()
    val archiveDepthHours by viewModel.archiveDepthHours.collectAsStateWithLifecycle()
    val osdHideSeconds by viewModel.osdHideSeconds.collectAsStateWithLifecycle()
    val guides by viewModel.guides.collectAsStateWithLifecycle()
    val epgRunning by viewModel.epgRunning.collectAsStateWithLifecycle()
    val guideBusy by viewModel.guideBusy.collectAsStateWithLifecycle()
    val guideMessage by viewModel.guideMessage.collectAsStateWithLifecycle()
    val aspectMode by viewModel.aspectMode.collectAsStateWithLifecycle()
    val directStream by viewModel.directStream.collectAsStateWithLifecycle()
    val watchNextEnabled by viewModel.watchNextEnabled.collectAsStateWithLifecycle()
    val watchNextLimit by viewModel.watchNextLimit.collectAsStateWithLifecycle()
    var guideDialog by remember { mutableStateOf(false) }
    val toast = LocalToastController.current
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    // Импорт плейлиста файлом: системный проводник, затем разбор содержимого.
    val pickPlaylist = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // Persistable-разрешение: файл остаётся источником и перечитывается после
            // перезапуска приложения.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val resolver = context.contentResolver
            val text = runCatching {
                resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.getOrElse { error ->
                android.util.Log.e("SmotrishkaSync", "read playlist file failed", error)
                ""
            }
            val name = runCatching {
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull()
            viewModel.importPlaylist(text, name, uri.toString())
        }
    }

    AppShell(
        destination = ShellDestination.SETTINGS,
        onSelect = { destination ->
            when (destination) {
                ShellDestination.SETTINGS -> Unit
                ShellDestination.ABOUT -> onOpenAbout()
                else -> onBack()
            }
        },
        onSearch = null,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(modifier = Modifier.widthIn(max = TvDimens.settingsMaxWidth)) {
                    SourceCard(
                        state = state,
                        fileSource = fileSource,
                        updating = updating,
                        status = status,
                        onChangeSource = onChangeSource,
                        onPickFile = { pickPlaylist.launch(arrayOf("*/*")) },
                        onReturnToProvider = {
                            viewModel.returnToProviderSource()
                            toast.show("Каталог возвращён к провайдеру")
                        },
                        onUpdate = {
                            viewModel.updateData()
                            toast.show("Плейлист обновляется")
                        },
                    )
                    Spacer(Modifier.height(17.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
                        PlaybackCard(
                            bufferSeconds = bufferSeconds,
                            quality = quality,
                            recoveryDelaySeconds = recoveryDelaySeconds,
                            audioLanguage = audioLanguage,
                            dataSaver = dataSaver,
                            onBuffer = viewModel::setBufferSeconds,
                            onQuality = viewModel::setVideoQuality,
                            onRecoveryDelay = viewModel::setRecoveryDelaySeconds,
                            onLanguage = viewModel::setAudioLanguage,
                            onDataSaver = viewModel::setDataSaver,
                            modifier = Modifier.weight(1f),
                        )
                        EpgCard(
                            epgWindowHours = epgWindowHours,
                            archiveDepthHours = archiveDepthHours,
                            globalEpgShift = globalEpgShift,
                            osdHideSeconds = osdHideSeconds,
                            guides = guides,
                            guideMessage = guideMessage,
                            epgRunning = epgRunning,
                            onRefreshEpg = viewModel::refreshEpgCompletely,
                            onEpgWindow = viewModel::setEpgWindowHours,
                            onArchiveDepth = viewModel::setArchiveDepthHours,
                            onGlobalShift = viewModel::setGlobalEpgShift,
                            onOsdHide = viewModel::setOsdHideSeconds,
                            onAddGuide = { guideDialog = true },
                            onRemoveGuide = { guide ->
                                viewModel.removeGuide(guide.id)
                                toast.show("Гид удалён")
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(17.dp))
                    LanguagesCard(
                        selected = channelLanguage,
                        languageFromName = languageFromName,
                        languages = languages,
                        onSelect = viewModel::setChannelLanguage,
                        onLanguageFromName = viewModel::setLanguageFromName,
                        // Карточка во всю ширину: в Column weight(1f) дал бы нулевую высоту.
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(17.dp))
                    InterfaceCard(
                        fontScale = fontScale,
                        onFontScale = viewModel::setFontScale,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(17.dp))
                    PlayerCard(
                        osdHideSeconds = osdHideSeconds,
                        onOsdHide = viewModel::setOsdHideSeconds,
                        aspectModeLabel = AspectModes.label(aspectMode),
                        aspectOptions = AspectModes.ORDER.map { AspectModes.label(it) },
                        onAspect = { index ->
                            viewModel.setAspectMode(AspectModes.ORDER[index])
                            toast.show("Режим кадра: ${AspectModes.label(AspectModes.ORDER[index])}")
                        },
                        directStream = directStream,
                        onDirectStream = { direct ->
                            viewModel.setDirectStream(direct)
                            toast.show(if (direct) "Режим эфира: прямой поток" else "Режим эфира: свой буфер")
                        },
                    )
                    Spacer(Modifier.height(17.dp))
                    HomeScreenCard(
                        watchNextEnabled = watchNextEnabled,
                        watchNextLimit = watchNextLimit,
                        onWatchNext = { enabled ->
                            viewModel.setWatchNextEnabled(enabled)
                            toast.show(
                                if (enabled) {
                                    "Лента «Сейчас идёт» включена"
                                } else {
                                    "Лента «Сейчас идёт» выключена"
                                },
                            )
                        },
                        onWatchNextLimit = viewModel::setWatchNextLimit,
                    )
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
    if (guideDialog) {
        AddGuideDialog(
            busy = guideBusy,
            message = guideMessage,
            onConfirm = { url ->
                viewModel.addGuide(url)
            },
            onDismiss = { guideDialog = false },
        )
    }

    // Итог проверки гида показываем тостом, когда диалог уже закрыт: побочный эффект
    // запускается из LaunchedEffect, а не из композиции.
    LaunchedEffect(guideMessage, guideDialog) {
        val message = guideMessage
        if (message != null && !guideDialog) toast.show(message)
    }
}

/**
 * Карточка «Источник каналов»: подключение и обновление плейлиста.
 */
@Composable
private fun SourceCard(
    state: SettingsUiState,
    fileSource: String?,
    updating: Boolean,
    status: String?,
    onChangeSource: () -> Unit,
    onPickFile: () -> Unit,
    onReturnToProvider: () -> Unit,
    onUpdate: () -> Unit,
) {
    SettingsCard(
        icon = Icons.Filled.LiveTv,
        iconTint = Color(0xFFBDA5FC),
        iconBackground = Color(0x29956FEE),
        title = "Источник каналов",
        subtitle = "Подключение и обновление плейлиста",
    ) {
        SettingsRow(
            title = "Источник",
            value = fileSource?.let { "файл «$it»" } ?: when (val current = state) {
                SettingsUiState.Loading -> "…"
                SettingsUiState.Empty -> "не настроен"
                is SettingsUiState.Content -> "${current.sourceLabel} · ${current.sourceDetail}"
                is SettingsUiState.Error -> current.reason
            },
            action = "Сменить",
            onAction = onChangeSource,
        )
        SettingsRow(
            title = "Плейлист из файла",
            hint = "M3U или M3U8 с устройства или из облака",
            action = "Выбрать файл",
            onAction = onPickFile,
        )
        SettingsRow(
            title = "Каталог по коду провайдера",
            hint = "Вернуть каталог из сохранённого источника",
            action = "Вернуть",
            onAction = onReturnToProvider,
        )
        SettingsRow(
            title = "Плейлист",
            value = when {
                updating -> "идёт обновление…"
                status != null -> status
                else -> "не загружался"
            },
            hint = "Скачать каналы и программу передач заново",
            action = if (updating) "Обновляется…" else "Загрузить заново",
            onAction = onUpdate,
        )
    }
}

/**
 * Карточка «Воспроизведение»: буфер, качество, пауза между попытками восстановления,
 * язык звука и экономия трафика.
 */
@Composable
private fun PlaybackCard(
    bufferSeconds: Int,
    quality: VideoQuality,
    recoveryDelaySeconds: Int,
    audioLanguage: AudioLanguage,
    dataSaver: Boolean,
    onBuffer: (Int) -> Unit,
    onQuality: (VideoQuality) -> Unit,
    onRecoveryDelay: (Int) -> Unit,
    onLanguage: (AudioLanguage) -> Unit,
    onDataSaver: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        icon = Icons.Filled.PlayArrow,
        iconTint = Color(0xFF61DFA5),
        iconBackground = Color(0x2145DF9A),
        title = "Воспроизведение",
        subtitle = "Буфер, качество и звук",
        modifier = modifier,
    ) {
        SettingsRow(
            title = "Буферизация вперёд",
            value = "$bufferSeconds с",
            hint = "Больше — спокойнее при обрывах сети",
        )
        ChoiceGroup(
            options = PlayerSettingsStore.BUFFER_CHOICES.map { "$it с" to (it == bufferSeconds) },
            columns = PlayerSettingsStore.BUFFER_CHOICES.size,
            onPick = { index -> onBuffer(PlayerSettingsStore.BUFFER_CHOICES[index]) },
        )

        SettingsRow(
            title = "Качество видео",
            value = quality.label(),
            hint = "Потолок для каналов с несколькими вариантами качества",
        )
        ChoiceGroup(
            options = QUALITY_CHOICES.map { it.label() to (it == quality) },
            columns = QUALITY_CHOICES.size,
            onPick = { index -> onQuality(QUALITY_CHOICES[index]) },
        )

        SettingsRow(
            title = "Пауза между попытками",
            value = "$recoveryDelaySeconds с",
            hint = "Плеер пробует восстановить поток бесконечно: чаще этого не получится",
        )
        ChoiceGroup(
            options = PlayerSettingsStore.RECOVERY_DELAY_CHOICES.map {
                "$it с" to (it == recoveryDelaySeconds)
            },
            columns = PlayerSettingsStore.RECOVERY_DELAY_CHOICES.size,
            onPick = { index -> onRecoveryDelay(PlayerSettingsStore.RECOVERY_DELAY_CHOICES[index]) },
        )

        SettingsRow(
            title = "Язык звука",
            value = audioLanguage.label(),
            hint = "Какую дорожку выбирать, если у канала их несколько",
        )
        ChoiceGroup(
            options = listOf(
                "Русский" to (audioLanguage == AudioLanguage.RUSSIAN),
                "Английский" to (audioLanguage == AudioLanguage.ENGLISH),
                "Оригинал" to (audioLanguage == AudioLanguage.ORIGINAL),
            ),
            columns = 3,
            onPick = { index ->
                onLanguage(
                    when (index) {
                        1 -> AudioLanguage.ENGLISH
                        2 -> AudioLanguage.ORIGINAL
                        else -> AudioLanguage.RUSSIAN
                    },
                )
            },
        )

        ToggleRow(
            title = "Экономия трафика",
            hint = "Превью не запускается само, логотипы не грузятся",
            checked = dataSaver,
            onToggle = onDataSaver,
        )
    }
}

/**
 * Карточка «Домашний экран»: лента «Сейчас идёт» в лаунчере Android TV.
 *
 * @param watchNextEnabled включена ли лента;
 * @param watchNextLimit сколько карточек публикуется;
 * @param onWatchNext переключение ленты;
 * @param onWatchNextLimit изменение количества карточек.
 */
@Composable
private fun HomeScreenCard(
    watchNextEnabled: Boolean,
    watchNextLimit: Int,
    onWatchNext: (Boolean) -> Unit,
    onWatchNextLimit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        icon = Icons.Filled.Home,
        iconTint = Color(0xFF8FB8FF),
        iconBackground = Color(0x213E8BE0),
        title = "Домашний экран",
        subtitle = "Лента на главном экране Android TV",
        modifier = modifier,
    ) {
        ToggleRow(
            title = "Лента «Сейчас идёт»",
            hint = "Канал приложения с текущими передачами; выключение убирает его из лаунчера",
            checked = watchNextEnabled,
            onToggle = onWatchNext,
        )
        if (watchNextEnabled) {
            SettingsRow(
                title = "Карточек в ленте",
                value = "$watchNextLimit",
                hint = "Сначала избранное и недавние, затем последняя категория и остальные каналы",
            )
            ChoiceGroup(
                options = PlayerSettingsStore.WATCH_NEXT_LIMIT_CHOICES.map {
                    "$it" to (it == watchNextLimit)
                },
                columns = PlayerSettingsStore.WATCH_NEXT_LIMIT_CHOICES.size,
                onPick = { index -> onWatchNextLimit(PlayerSettingsStore.WATCH_NEXT_LIMIT_CHOICES[index]) },
            )
        }
    }
}

/**
 * Карточка «Программа передач»: окно, глубина архива и гиды XMLTV.
 */
/**
 * Подпись размера шрифта.
 *
 * @param scale масштаб шрифта из настроек.
 */
private fun fontScaleLabel(scale: Float): String = when {
    scale <= 0.5f -> "Мелкий"
    scale <= 0.75f -> "Средний"
    scale <= 1.0f -> "Обычный"
    scale <= 1.5f -> "Крупный"
    else -> "Очень крупный"
}

/**
 * Карточка «Интерфейс»: размер шрифта всего приложения.
 *
 * @param fontScale текущий масштаб шрифта;
 * @param onFontScale выбор масштаба;
 * @param modifier размеры карточки.
 */
@Composable
private fun InterfaceCard(
    fontScale: Float,
    onFontScale: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        icon = Icons.Filled.Settings,
        iconTint = Color(0xFF6FB3F0),
        iconBackground = Color(0x216FB3F0),
        title = "Интерфейс",
        subtitle = "Размер шрифта",
        modifier = modifier,
    ) {
        SettingsRow(
            title = "Размер шрифта",
            value = fontScaleLabel(fontScale),
            hint = "Меняет все тексты приложения; меню плеера не масштабируется",
        )
        val choices = PlayerSettingsStore.FONT_SCALE_CHOICES
        val selectedRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        // После смены размера шрифта возвращаем фокус на выбранный вариант: экран остаётся
        // на строке «Размер шрифта», а не прокручивается в начало (замечание 09.11.2026).
        LaunchedEffect(fontScale) {
            runCatching { selectedRequester.requestFocus() }
        }
        ChoiceGroup(
            options = choices.map { fontScaleLabel(it) to (it == fontScale) },
            columns = 3,
            selectedRequester = selectedRequester,
            onPick = { index -> choices.getOrNull(index)?.let(onFontScale) },
        )
    }
}

/**
 * Подпись выбора языка в настройках.
 *
 * @param language сохранённое значение: `null` — все, [PlayerSettingsStore.UNLABELLED_LANGUAGE] — основные.
 */
private fun languageLabel(language: String?): String = when (language) {
    null -> "все"
    PlayerSettingsStore.UNLABELLED_LANGUAGE -> "Основные"
    else -> language
}

/**
 * Карточка «Язык каналов».
 *
 * Фильтр работает по языку канала: `tvg-language` из плейлиста, иначе языковая группа.
 * Каналы без метки показываются всегда (решение Александра 09.11.2026).
 *
 * @param selected выбранный язык, `null` — все;
 * @param languageFromName определять язык по названию канала (`... HD ES`, `... US`);
 * @param languages языки, найденные в плейлисте;
 * @param onSelect выбор языка;
 * @param onLanguageFromName переключение определения языка по названию;
 * @param modifier размеры карточки.
 */
@Composable
private fun LanguagesCard(
    selected: String?,
    languageFromName: Boolean,
    languages: List<String>,
    onSelect: (String?) -> Unit,
    onLanguageFromName: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        icon = Icons.Filled.Language,
        iconTint = Color(0xFF8F7BF0),
        iconBackground = Color(0x218F7BF0),
        title = "Язык каналов",
        subtitle = "Только каналы нужного языка",
        modifier = modifier,
    ) {
        SettingsRow(
            title = "Язык каналов",
            value = languageLabel(selected),
            hint = "Язык берётся из метки tvg-language, иначе из языковой группы канала",
        )
        // «Основные» — каналы без метки языка: выбор отсекает все явные языки.
        val options = listOf<String?>(null, PlayerSettingsStore.UNLABELLED_LANGUAGE) + languages
        ChoiceGroup(
            options = options.map { languageLabel(it) to (it == selected) },
            columns = 3,
            onPick = { index -> onSelect(options.getOrNull(index)) },
        )
        ToggleRow(
            title = "Определять язык по названию",
            hint = "Хвосты вида «... HD ES», «... US» задают язык канала",
            checked = languageFromName,
            onToggle = onLanguageFromName,
        )
    }
}

@Composable
private fun EpgCard(
    epgWindowHours: Int,
    archiveDepthHours: Int,
    // Общее смещение гида: применяется к каналам без своего сдвига.
    globalEpgShift: Int,
    // Сколько секунд меню плеера показывается без действий.
    osdHideSeconds: Int,
    guides: List<EpgGuide>,
    guideMessage: String?,
    // Идёт полное обновление программы: кнопка блокируется и показывает прогресс.
    epgRunning: Boolean,
    onRefreshEpg: () -> Unit,
    onEpgWindow: (Int) -> Unit,
    onArchiveDepth: (Int) -> Unit,
    onGlobalShift: (Int) -> Unit,
    onOsdHide: (Int) -> Unit,
    onAddGuide: () -> Unit,
    onRemoveGuide: (EpgGuide) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsCard(
        icon = Icons.Filled.DateRange,
        iconTint = Color(0xFFF0B069),
        iconBackground = Color(0x21F0B069),
        title = "Программа передач",
        subtitle = "Окно, архив и гиды XMLTV",
        modifier = modifier,
    ) {
        SettingsRow(
            title = "Окно программы передач",
            value = "${depthLabel(epgWindowHours)} назад",
            hint = "Меньше окно — быстрее обновление; глубину задаёт архив",
        )
        ChoiceGroup(
            options = PlayerSettingsStore.EPG_WINDOW_CHOICES.map { hours ->
                windowLabel(hours) to (hours == epgWindowHours)
            },
            columns = 4,
            onPick = { index -> onEpgWindow(PlayerSettingsStore.EPG_WINDOW_CHOICES[index]) },
        )

        SettingsRow(
            title = "Глубина архива",
            value = "${depthLabel(archiveDepthHours)} назад",
            hint = "Сколько назад доступны прошедшие передачи; провайдер может отдавать меньше",
        )
        ChoiceGroup(
            options = PlayerSettingsStore.ARCHIVE_DEPTH_CHOICES.map { hours ->
                "${hours / 24} сут" to (hours == archiveDepthHours)
            },
            columns = 4,
            onPick = { index ->
                onArchiveDepth(PlayerSettingsStore.ARCHIVE_DEPTH_CHOICES[index])
            },
        )

        SettingsRow(
            title = "Смещение гида",
            value = shiftLabel(globalEpgShift),
            hint = "Если вся сетка провайдера в чужом поясе; сдвиг отдельного канала важнее общего",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            ChoiceButton(
                label = "−1 ч",
                selected = false,
                modifier = Modifier.weight(1f),
                onClick = { onGlobalShift(globalEpgShift - 1) },
            )
            ChoiceButton(
                label = "0",
                selected = globalEpgShift == 0,
                modifier = Modifier.weight(1f),
                onClick = { onGlobalShift(0) },
            )
            ChoiceButton(
                label = "+1 ч",
                selected = false,
                modifier = Modifier.weight(1f),
                onClick = { onGlobalShift(globalEpgShift + 1) },
            )
        }

        SettingsRow(
            title = "Программа передач целиком",
            value = if (epgRunning) "обновляется…" else null,
            hint = "Сбросить кэш и скачать программу заново со всех источников",
            action = if (epgRunning) "Обновляется…" else "Обновить",
            onAction = onRefreshEpg,
        )
        SettingsRow(
            title = "Источники программы передач",
            value = if (guides.isEmpty()) "нет" else "${guides.size}",
            hint = "Адрес проверяется перед сохранением; недоступный не добавляется",
            action = "Добавить",
            onAction = onAddGuide,
        )
        if (guides.isEmpty()) {
            Text(
                text = "Гиды не добавлены",
                style = TvType.rowProgramme,
                color = Color(0xFF747C8A),
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                guides.forEach { guide ->
                    GuideItem(guide = guide, onRemove = { onRemoveGuide(guide) })
                }
            }
        }
        guideMessage?.let { message ->
            Text(
                text = message,
                style = TvType.caption,
                color = TvColors.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Карточка «Плеер»: режим кадра и режим эфира (решение Д4 — те же настройки, что в OSD).
 */
@Composable
private fun PlayerCard(
    // Сколько секунд меню плеера остаётся на экране без действий.
    osdHideSeconds: Int,
    onOsdHide: (Int) -> Unit,
    aspectModeLabel: String,
    aspectOptions: List<String>,
    onAspect: (Int) -> Unit,
    directStream: Boolean,
    onDirectStream: (Boolean) -> Unit,
) {
    SettingsCard(
        icon = Icons.Filled.List,
        iconTint = Color(0xFFBDA5FC),
        iconBackground = Color(0x29956FEE),
        title = "Плеер",
        subtitle = "Кадр и режим эфира",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                // Время показа меню плеера: относится к плееру, поэтому живёт в этой карточке.
                SettingsRow(
                    title = "Меню плеера на экране",
                    value = "${osdHideSeconds} с",
                    hint = "Сколько секунд OSD виден без действий с пультом",
                )
                ChoiceGroup(
                    options = PlayerSettingsStore.OSD_HIDE_CHOICES.map { seconds ->
                        "$seconds с" to (seconds == osdHideSeconds)
                    },
                    columns = 4,
                    onPick = { index -> onOsdHide(PlayerSettingsStore.OSD_HIDE_CHOICES[index]) },
                )
                SettingsRow(
                    title = "Режим кадра",
                    value = aspectModeLabel,
                    hint = "Что делать с картинкой, если пропорции не совпадают",
                )
                ChoiceGroup(
                    options = aspectOptions.map { it to (it == aspectModeLabel) },
                    columns = 4,
                    onPick = onAspect,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                SettingsRow(
                    title = "Режим эфира",
                    value = if (directStream) "Прямой поток" else "Свой буфер",
                    hint = "Свой буфер сглаживает обрывы, но отстаёт от эфира",
                )
                ChoiceGroup(
                    options = listOf(
                        "Прямой поток" to directStream,
                        "Свой буфер" to !directStream,
                    ),
                    columns = 2,
                    onPick = { index -> onDirectStream(index == 0) },
                )
            }
        }
    }
}

/**
 * Карточка настроек: иконка с подложкой, заголовок 17/600 и подпись 10.
 *
 * @param icon иконка шапки;
 * @param iconTint цвет иконки;
 * @param iconBackground подложка иконки;
 * @param title заголовок карточки;
 * @param subtitle подпись под заголовком;
 * @param modifier размеры карточки;
 * @param content строки настроек.
 */
@Composable
private fun SettingsCard(
    icon: ImageVector,
    iconTint: Color,
    iconBackground: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(TvRadius.panel)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TvColors.panel.copy(alpha = 0.82f))
            .border(1.dp, TvColors.line, shape)
            .padding(TvDimens.settingsCardPadding),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(TvDimens.settingsIcon)
                    .clip(RoundedCornerShape(TvRadius.item))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(text = title, style = TvType.panelTitle, color = TvColors.text)
                Text(text = subtitle, style = TvType.date, color = TvColors.muted)
            }
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/**
 * Строка настройки: название, значение, действие и подсказка.
 *
 * @param title название параметра;
 * @param value текущее значение, `null` — значения нет;
 * @param action подпись действия, `null` — действия нет;
 * @param onAction действие;
 * @param hint подсказка под строкой.
 */
@Composable
private fun SettingsRow(
    title: String,
    value: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    hint: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TvColors.line),
        )
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TvType.category,
                color = TvColors.text,
                modifier = Modifier.weight(1f),
            )
            if (value != null) {
                Text(
                    text = value,
                    style = TvType.rowProgramme,
                    color = TvColors.fieldLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
            if (action != null && onAction != null) {
                Spacer(Modifier.width(12.dp))
                ActionText(text = action, onClick = onAction)
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(3.dp))
            Text(text = hint, style = TvType.caption, color = Color(0xFF747C8A))
        }
    }
}

/**
 * Действие строки настройки: текст 10/600 акцентом.
 *
 * @param text подпись;
 * @param onClick действие.
 */
@Composable
private fun ActionText(text: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.choice)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (focused) TvColors.accent.copy(alpha = 0.16f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = TvType.rowProgramme, color = TvColors.accent)
    }
}

/**
 * Группа вариантов: кнопки min-height 40 с активной рамкой.
 *
 * @param options подписи и признак выбора;
 * @param columns число колонок;
 * @param onPick индекс выбранного варианта.
 */
@Composable
private fun ChoiceGroup(
    options: List<Pair<String, Boolean>>,
    columns: Int,
    onPick: (Int) -> Unit,
    selectedRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val rows = options.chunked(columns.coerceAtLeast(1))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { (label, selected) ->
                    val index = options.indexOfFirst { it.first == label && it.second == selected }
                    ChoiceButton(
                        label = label,
                        selected = selected,
                        modifier = Modifier.weight(1f),
                        // Выбранный вариант получает requester: после смены настройки
                        // экран остаётся на этой же строке, а не уезжает вверх.
                        focusRequester = if (selected) selectedRequester else null,
                        onClick = { onPick(index) },
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Кнопка варианта: 40 dp, радиус 10; активная — рамка `#a481f3` и заливка.
 *
 * @param label подпись;
 * @param selected выбран ли вариант;
 * @param onClick выбор;
 * @param modifier размеры.
 */
@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.choice)
    Box(
        modifier = modifier
            .heightIn(min = TvDimens.choiceMinHeight)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            )
            .clip(shape)
            // Фокус в настройках: акцентная заливка и толстая обводка — на тёмных панелях
            // прежняя подсветка 16% не читалась (замечание Александра 06.11.2026).
            .background(
                when {
                    focused -> TvColors.accent
                    selected -> Color(0x29946CEE)
                    else -> Color.Transparent
                },
            )
            .border(
                if (focused) 3.dp else 1.dp,
                when {
                    focused -> TvColors.focusOutline
                    selected -> Color(0xFFA481F3)
                    else -> TvColors.line
                },
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TvType.rowProgramme,
            color = when {
                focused -> Color(0xFF100A1E)
                selected -> TvColors.text
                else -> TvColors.fieldLabel
            },
            maxLines = 1,
        )
    }
}

/**
 * Строка переключателя: название, подпись и тумблер 39×22.
 *
 * @param title название;
 * @param hint подпись;
 * @param checked включён ли;
 * @param onToggle переключение.
 */
@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.choice)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onToggle(!checked) }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(top = 17.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = TvType.programme, color = TvColors.text)
            Spacer(Modifier.height(3.dp))
            Text(text = hint, style = TvType.caption, color = Color(0xFF777F8D))
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(width = 39.dp, height = 22.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (checked) Color(0xFF7C59D2) else Color(0xFF333946))
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    // Кружок переезжает вправо при включении: раньше менялся только цвет,
                    // и выключатель выглядел непереключённым (замечание Александра 09.11.2026).
                    // Без анимации: на слабых приставках лишние кадры не нужны.
                    .offset(x = if (checked) 17.dp else 0.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (checked) Color.White else Color(0xFF9299A6)),
            )
        }
    }
}

/**
 * Строка гида XMLTV: имя, адрес и «Удалить».
 *
 * @param guide гид;
 * @param onRemove удаление.
 */
@Composable
private fun GuideItem(guide: EpgGuide, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(TvRadius.small)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TvColors.fieldBg)
            .border(1.dp, TvColors.line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.List,
            contentDescription = null,
            tint = TvColors.eyebrow,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = guide.name,
                style = TvType.programme,
                color = TvColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = guide.url,
                style = TvType.caption,
                color = Color(0xFF747C8A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Удалить",
            style = TvType.badge,
            color = TvColors.guideDelete,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onRemove() }
                .focusable()
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/**
 * Диалог добавления гида XMLTV: карточка 520 dp, поле адреса и две кнопки.
 *
 * @param busy идёт проверка адреса;
 * @param message результат проверки;
 * @param onConfirm добавить гид по адресу;
 * @param onDismiss закрыть диалог.
 */
@Composable
private fun AddGuideDialog(
    busy: Boolean,
    message: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    // Фокус в диалог ставим сами: без этого пульт оставался на экране настроек
    // и ни поле ввода, ни кнопки не реагировали.
    val dialogFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(5) {
            withFrameNanos { }
            runCatching { dialogFocus.requestFocus() }
            delay(40)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.scrim)
                .clickable(onClick = onDismiss)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(TvRadius.dialog)
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .clip(shape)
                    .background(TvColors.dialogBg)
                    .border(1.dp, TvColors.lineStrong, shape)
                    .padding(22.dp),
            ) {
                Text(
                    text = "Новый гид программы передач",
                    style = TvType.clock,
                    color = TvColors.text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Адрес XMLTV: xml или xml.gz. Адрес проверяется перед сохранением.",
                    style = TvType.rowProgramme,
                    color = TvColors.fieldLabel,
                )
                Spacer(Modifier.height(14.dp))
                val fieldShape = RoundedCornerShape(TvRadius.small)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 43.dp)
                        .clip(fieldShape)
                        .background(TvColors.fieldBg)
                        .border(1.dp, TvColors.line, fieldShape)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        enabled = !busy,
                        textStyle = TvType.category.copy(color = TvColors.text),
                        cursorBrush = SolidColor(TvColors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(dialogFocus)
                            .focusable(),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (url.isEmpty()) {
                                    Text(
                                        text = "http://example.com/guide.xml",
                                        style = TvType.category,
                                        color = Color(0xFF6E7583),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(text = message, style = TvType.caption, color = TvColors.errorText)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End),
                ) {
                    DialogButton(text = "Закрыть", primary = false, onClick = onDismiss)
                    DialogButton(
                        text = if (busy) "Проверяем…" else "Проверить и добавить",
                        primary = true,
                        onClick = { if (!busy) onConfirm(url) },
                    )
                }
            }
        }
    }
}

/**
 * Кнопка диалога: главная — градиент, второстепенная — рамка.
 *
 * @param text подпись;
 * @param primary главная ли кнопка;
 * @param onClick действие.
 */
@Composable
private fun DialogButton(text: String, primary: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.choice)
    Box(
        modifier = Modifier
            .heightIn(min = 39.dp)
            .clip(shape)
            .then(
                if (primary) {
                    Modifier.background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(TvColors.buttonStart, TvColors.buttonEnd),
                        ),
                    )
                } else {
                    Modifier.background(Color.Transparent)
                },
            )
            .border(
                1.dp,
                if (primary) Color.Transparent else Color(0xFF484052),
                shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TvType.rowProgramme,
            color = if (primary) Color.White else Color(0xFFC8B7F1),
            maxLines = 1,
        )
    }
}

/** Порядок качества в настройках: от самого экономичного к лучшему. */
private val QUALITY_CHOICES = listOf(
    VideoQuality.AUTO,
    VideoQuality.SD_360,
    VideoQuality.SD_480,
    VideoQuality.HD,
    VideoQuality.FULL_HD,
)

/** Подпись качества для настроек. */
private fun VideoQuality.label(): String = when (this) {
    VideoQuality.AUTO -> "Авто"
    VideoQuality.SD_360 -> "360p"
    VideoQuality.SD_480 -> "480p"
    VideoQuality.HD -> "720p"
    VideoQuality.FULL_HD -> "1080p"
}

/** Подпись языка звука. */
private fun AudioLanguage.label(): String = when (this) {
    AudioLanguage.RUSSIAN -> "Русский"
    AudioLanguage.ENGLISH -> "Английский"
    AudioLanguage.ORIGINAL -> "Оригинал"
}

/** Подпись глубины в часах: «1 сут», «12 ч». */
private fun depthLabel(hours: Int): String =
    if (hours % 24 == 0) "${hours / 24} сут" else "$hours ч"

/** Подпись варианта окна программы передач. */
private fun windowLabel(hours: Int): String =
    if (hours % 24 == 0) "${hours / 24} сут" else "$hours ч"

/** Подпись общего смещения гида. */
private fun shiftLabel(hours: Int): String = when {
    hours == 0 -> "нет"
    hours > 0 -> "+$hours ч"
    else -> "$hours ч"
}
