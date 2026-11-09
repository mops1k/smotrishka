package com.mops1k.smotrishka.tv.ui.about

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mops1k.smotrishka.tv.R
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.common.LocalToastController
import com.mops1k.smotrishka.tv.ui.shell.AppShell
import com.mops1k.smotrishka.tv.ui.shell.ShellDestination
import com.mops1k.smotrishka.tv.update.UpdateState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * «О приложении»: название, назначение, версия сборки и блок обновления.
 *
 * Обновление проверяется при открытии экрана; тут же переключатели пререлизов
 * и «только Wi-Fi», скачивание с процентом и установка через системный установщик.
 *
 * @param onBack возврат на предыдущий экран;
 * @param onOpenSettings переход в настройки;
 * @param viewModel экранная модель.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val toast = LocalToastController.current

    // Скачанный APK в сценарии переустановки сохраняется туда, куда укажет пользователь.
    val saveApk = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive"),
    ) { uri -> if (uri != null) viewModel.saveApk(uri) }

    // Файл настроек: экспорт и импорт через системный проводник.
    val saveSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) viewModel.exportSettings(uri) }

    val openSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.readSettingsFile(uri) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        toast.show(text)
        viewModel.consumeMessage()
    }

    BackHandler(onBack = onBack)

    // Проверка при открытии окна: пользователь зашёл сюда, значит хочет знать версию.
    LaunchedEffect(Unit) { viewModel.checkNow() }

    // Возврат из системы (отмена установки, экран разрешений) должен показать свежее
    // состояние, а не застывшее «Установщик открыт».
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.checkNow() }

    AppShell(
        destination = ShellDestination.ABOUT,
        onSelect = { destination ->
            when (destination) {
                ShellDestination.ABOUT -> Unit
                ShellDestination.SETTINGS -> onOpenSettings()
                else -> onBack()
            }
        },
        onSearch = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.widthIn(max = TvDimens.settingsMaxWidth)) {
                AboutCard(
                    icon = Icons.Filled.Info,
                    brandMark = true,
                    title = stringResource(R.string.app_name),
                    subtitle = "IPTV для телевизора и телефона",
                ) {
                    Text(
                        text = stringResource(R.string.about_description),
                        style = TvType.category,
                        color = TvColors.muted,
                    )
                    Spacer(Modifier.height(13.dp))
                    Text(
                        text = state.versionLabel,
                        style = TvType.rowProgramme,
                        color = TvColors.fieldLabel,
                    )
                }

                Spacer(Modifier.height(17.dp))

                UpdateCard(
                    state = state,
                    onCheck = viewModel::checkNow,
                    onCheckAgain = viewModel::checkAgain,
                    onDownload = { viewModel.download() },
                    onForceDownload = { viewModel.download(force = true) },
                    onDismiss = viewModel::dismiss,
                    onPrereleases = viewModel::setPrereleases,
                    onWifiOnly = viewModel::setWifiOnly,
                    onOpenPermission = viewModel::openInstallPermission,
                    onSaveApk = { name -> saveApk.launch(name) },
                )

                Spacer(Modifier.height(17.dp))

                AboutCard(
                    icon = Icons.Filled.Settings,
                    title = "Резервная копия",
                    subtitle = "Настройки, каналы, избранное и архив — в один файл",
                ) {
                    Text(
                        text = "Файл резервной копии содержит источник в открытом виде: ссылку, " +
                            "код или логин и пароль. Его не стоит публиковать.",
                        style = TvType.caption,
                        color = TvColors.fieldLabel,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 13.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End),
                    ) {
                        AboutButton(
                            text = "Сохранить копию",
                            primary = false,
                            onClick = { saveSettings.launch("smotrishka-settings.json") },
                        )
                        AboutButton(
                            text = "Восстановить из файла",
                            primary = true,
                            onClick = { openSettings.launch(arrayOf("*/*")) },
                        )
                    }
                }
            }
        }
    }

    if (pendingImport != null) {
        ImportConfirmDialog(
            onConfirm = viewModel::confirmImport,
            onCancel = viewModel::cancelImport,
        )
    }
}

/**
 * Карточка окна: иконка с подложкой, заголовок, подпись и содержимое.
 *
 * @param icon иконка карточки;
 * @param title заголовок;
 * @param subtitle подпись под заголовком;
 * @param content строки карточки.
 */
@Composable
private fun AboutCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    brandMark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(TvRadius.panel)
    Column(
        modifier = Modifier
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
                    .background(TvColors.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                if (brandMark) {
                    Image(
                        painter = painterResource(R.drawable.brand_mark),
                        contentDescription = null,
                        modifier = Modifier
                            .size(TvDimens.settingsIcon)
                            .clip(RoundedCornerShape(TvRadius.item)),
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TvColors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
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
 * Блок обновления: состояние, действия и настройки проверки.
 *
 * @param state состояние окна;
 * @param onCheck проверить обновления;
 * @param onCheckAgain повторная проверка со сбросом скрытия;
 * @param onDownload скачать и установить;
 * @param onForceDownload скачать по мобильной сети;
 * @param onDismiss «не спрашивать об этой версии»;
 * @param onPrereleases переключить пререлизы;
 * @param onWifiOnly переключить «только Wi-Fi»;
 * @param onOpenPermission открыть разрешение установки;
 * @param onSaveApk сохранить APK в файл.
 */
@Composable
private fun UpdateCard(
    state: AboutUiState,
    onCheck: () -> Unit,
    onCheckAgain: () -> Unit,
    onDownload: () -> Unit,
    onForceDownload: () -> Unit,
    onDismiss: () -> Unit,
    onPrereleases: (Boolean) -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onOpenPermission: () -> Unit,
    onSaveApk: (String) -> Unit,
) {
    val update = state.update
    val sizeLabel = (update as? UpdateState.Available)?.apk?.sizeBytes?.let(::sizeLabel)

    AboutCard(
        icon = Icons.Filled.Refresh,
        title = "Обновление",
        subtitle = "Новые версии приложения на GitHub",
    ) {
        Text(
            text = statusText(state),
            style = TvType.category,
            color = statusColor(update),
        )

        if (update is UpdateState.NeedsReinstall) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Установленная сборка подписана другим ключом: поверх неё нельзя. " +
                    "Сохраните APK, удалите приложение и поставьте файл заново — " +
                    "источник и избранное вернутся из сохранённых настроек.",
                style = TvType.caption,
                color = TvColors.errorText,
            )
        }

        if (update is UpdateState.Available && update.cachedApk != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Файл уже скачан: повторно загружать не нужно.",
                style = TvType.caption,
                color = TvColors.fieldLabel,
            )
        }

        if (update is UpdateState.Available && state.metered && state.wifiOnly) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Сейчас мобильная сеть: скачивание $sizeLabel пойдёт по трафику.",
                style = TvType.caption,
                color = TvColors.fieldLabel,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End),
        ) {
            when (update) {
                UpdateState.Checking -> AboutButton(
                    text = "Проверяем…",
                    primary = true,
                    onClick = {},
                )

                is UpdateState.UpToDate -> AboutButton(
                    text = "Проверить снова",
                    primary = false,
                    onClick = onCheck,
                )

                is UpdateState.Available -> {
                    if (update.dismissed) {
                        AboutButton(text = "Показать снова", primary = false, onClick = onCheckAgain)
                    } else {
                        AboutButton(
                            text = "Не спрашивать об этой версии",
                            primary = false,
                            onClick = onDismiss,
                        )
                    }
                    if (state.metered && state.wifiOnly && update.cachedApk == null) {
                        AboutButton(text = "Скачать всё равно", primary = false, onClick = onForceDownload)
                    }
                    AboutButton(
                        text = if (update.cachedApk != null) "Установить" else "Скачать и установить",
                        primary = true,
                        onClick = onDownload,
                    )
                }

                is UpdateState.Downloading -> Text(
                    text = "Загрузка…",
                    style = TvType.caption,
                    color = TvColors.fieldLabel,
                )

                is UpdateState.NeedsReinstall -> AboutButton(
                    text = "Сохранить APK в файл",
                    primary = true,
                    onClick = { onSaveApk("smotrishka-${update.version}.apk") },
                )

                UpdateState.Installing -> Text(
                    text = "Ждём подтверждения в системном установщике",
                    style = TvType.caption,
                    color = TvColors.fieldLabel,
                )

                UpdateState.NeedsPermission -> AboutButton(
                    text = "Разрешить установку",
                    primary = true,
                    onClick = onOpenPermission,
                )

                is UpdateState.Failed -> AboutButton(
                    text = "Повторить",
                    primary = true,
                    onClick = onCheck,
                )

                UpdateState.Idle -> AboutButton(
                    text = "Проверить обновления",
                    primary = true,
                    onClick = onCheck,
                )
            }
        }

        AboutToggle(
            title = "Получать пререлизы",
            hint = "Предлагать и тестовые версии-кандидаты (rc)",
            checked = state.includePrereleases,
            onToggle = onPrereleases,
        )
        AboutToggle(
            title = "Только по Wi-Fi",
            hint = "Проверять и скачивать автоматически только в Wi-Fi или проводной сети",
            checked = state.wifiOnly,
            onToggle = onWifiOnly,
        )
    }
}

/** Текст состояния в блоке обновления. */
private fun statusText(state: AboutUiState): String = when (val update = state.update) {
    UpdateState.Idle -> "Проверьте, нет ли новой версии."

    UpdateState.Checking -> "Запрашиваем GitHub…"

    is UpdateState.UpToDate -> buildString {
        append("Установлена последняя версия.")
        update.checkedAtMillis?.let { append(" Проверено ").append(timeLabel(it)).append('.') }
    }

    is UpdateState.Available -> if (update.dismissed) {
        "Версия ${update.release.version} скрыта: вы выбрали «Не спрашивать об этой версии»."
    } else {
        "Доступна версия ${update.release.version} (${sizeLabel(update.apk.sizeBytes)})."
    }

    is UpdateState.Downloading -> "Загружаем APK: ${update.percent}%"

    is UpdateState.NeedsReinstall -> "Версию ${update.version} нельзя установить поверх."

    UpdateState.Installing -> "Установщик открыт: подтвердите установку."

    UpdateState.NeedsPermission -> "Установка из этого приложения запрещена системой."

    is UpdateState.Failed -> update.reason
}

/** Цвет текста состояния: ошибки выделяются, обычное состояние — нет. */
private fun statusColor(update: UpdateState): Color = when (update) {
    is UpdateState.Failed, is UpdateState.NeedsReinstall, UpdateState.NeedsPermission -> TvColors.errorText
    else -> TvColors.fieldLabel
}

/** Размер файла словами: «≈ 9 МБ». */
private fun sizeLabel(bytes: Long): String =
    if (bytes <= 0L) "размер неизвестен" else "≈ ${bytes / 1_048_576} МБ"

/** Время проверки: «14:20». */
private fun timeLabel(millis: Long): String = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(millis))

/**
 * Кнопка окна: главная — градиент, второстепенная — рамка.
 *
 * @param text подпись;
 * @param primary главная ли кнопка;
 * @param onClick действие;
 * @param focusRequester адрес фокуса, если на кнопку надо встать сразу.
 */
@Composable
private fun AboutButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.choice)
    Box(
        modifier = Modifier
            .heightIn(min = 39.dp)
            .clip(shape)
            // Фокус как в остальных кнопках проекта: акцентная заливка и обводка 3 dp —
            // на тёмной панели одной рамки tvFocusRing не хватало.
            .background(
                when {
                    focused -> Brush.linearGradient(listOf(TvColors.accent, TvColors.accent))
                    primary -> Brush.linearGradient(listOf(TvColors.buttonStart, TvColors.buttonEnd))
                    else -> Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                },
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = when {
                    focused -> TvColors.focusOutline
                    primary -> Color.Transparent
                    else -> Color(0xFF484052)
                },
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TvType.rowProgramme,
            color = when {
                focused -> Color(0xFF100A1E)
                primary -> Color.White
                else -> Color(0xFFC8B7F1)
            },
            maxLines = 1,
        )
    }
}

/**
 * Переключатель с подписью и подсказкой.
 *
 * @param title название;
 * @param hint пояснение;
 * @param checked включён ли;
 * @param onToggle переключение.
 */
@Composable
private fun AboutToggle(
    title: String,
    hint: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.choice)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            // Фокус строки: акцентная подсветка и обводка 3 dp — как у кнопок-вариантов
            // в настройках, иначе на пульте не видно, где стоит фокус.
            .background(if (focused) TvColors.accent.copy(alpha = 0.18f) else Color.Transparent)
            .then(
                if (focused) Modifier.border(3.dp, TvColors.focusOutline, shape) else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable { onToggle(!checked) }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(top = 17.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TvType.programme,
                color = if (focused) TvColors.focusOutline else TvColors.text,
            )
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (checked) Color.White else Color(0xFF9299A6)),
            )
        }
    }
}

/**
 * Подтверждение замены настроек: импорт переписывает источник, гиды и избранное.
 *
 * @param onConfirm применить файл;
 * @param onCancel оставить текущие настройки.
 */
@Composable
private fun ImportConfirmDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val confirmFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(5) {
            withFrameNanos { }
            runCatching { confirmFocus.requestFocus() }
            delay(40)
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.scrim)
                .clickable(onClick = onCancel)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(TvRadius.dialog)
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clickable(enabled = false) {}
                    .clip(shape)
                    .background(TvColors.dialogBg)
                    .border(1.dp, TvColors.lineStrong, shape)
                    .padding(22.dp),
            ) {
                Text(
                    text = "Заменить настройки?",
                    style = TvType.clock,
                    color = TvColors.text,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Текущие настройки, источник, гиды и избранное будут заменены " +
                        "содержимым файла. Каталог обновится после импорта.",
                    style = TvType.rowProgramme,
                    color = TvColors.fieldLabel,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End),
                ) {
                    AboutButton(text = "Отмена", primary = false, onClick = onCancel)
                    AboutButton(
                        text = "Заменить",
                        primary = true,
                        onClick = onConfirm,
                        focusRequester = confirmFocus,
                    )
                }
            }
        }
    }
}
