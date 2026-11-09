package com.mops1k.smotrishka.tv.ui.onboarding

import android.content.Intent
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mops1k.smotrishka.tv.core.designsystem.component.TvButtonGradient
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import kotlinx.coroutines.launch

/**
 * Экран подключения источника: один экран на все способы ввода.
 *
 * Показывается целиком без каркаса при первом запуске; при входе из настроек поля подставлены
 * значениями активного источника и внизу появляется «Отмена» (решение Д12: маршрут, а не
 * модальный слой).
 *
 * @param onConnected вызывается после успешного подключения;
 * @param onCancel возврат в настройки, `null` — отмены нет (первый запуск);
 * @param viewModel экранная модель.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onConnected: () -> Unit,
    onCancel: (() -> Unit)? = null,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val initial by viewModel.initial.collectAsStateWithLifecycle()

    var sourceType by remember { mutableStateOf(SourceType.M3U_URL) }
    var m3uUrl by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    // Файл, выбранный системным проводником: адрес и имя для подписи на экране.
    var pickedFile by remember { mutableStateOf<Pair<String, String?>?>(null) }
    // Счётчик запросов фокуса: смена способа ставит фокус в первое поле ввода.
    var focusEpoch by remember { mutableStateOf(0) }
    val context = LocalContext.current

    // Файл становится полноценным источником: берём persistable-разрешение, чтобы каталог
    // можно было перечитать позже без повторного выбора файла.
    val pickPlaylist = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            pickedFile = uri.toString() to displayNameOf(context, uri.toString())
        }
    }

    // Файл настроек: после переустановки источник, гиды и избранное возвращаются разом.
    val pickSettings = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restore(uri)
    }

    LaunchedEffect(initial) {
        when (val source = initial) {
            is PlaylistSource.M3uUrl -> {
                sourceType = SourceType.M3U_URL
                m3uUrl = source.url
            }

            is PlaylistSource.Xtream -> {
                sourceType = SourceType.XTREAM
                server = source.server
                username = source.username
                password = source.password
            }

            is PlaylistSource.IedemCode -> {
                sourceType = SourceType.IEDEM_CODE
                code = source.code
            }

            is PlaylistSource.File -> {
                sourceType = SourceType.FILE
                pickedFile = source.uri to source.name
            }

            null -> Unit
        }
    }

    // Отправка формы одна и та же для кнопки «Подключить» и клавиши «Готово» на системной
    // клавиатуре: ввод с пульта не должен требовать возврата к кнопке.
    val submitSource: () -> Unit = {
        viewModel.submit(
            when (sourceType) {
                SourceType.M3U_URL -> PlaylistSource.M3uUrl(url = m3uUrl.trim())
                SourceType.XTREAM -> PlaylistSource.Xtream(
                    server = server.trim(),
                    username = username.trim(),
                    password = password,
                )

                SourceType.IEDEM_CODE -> PlaylistSource.IedemCode(code = code.trim())
                SourceType.FILE -> PlaylistSource.File(
                    uri = pickedFile?.first.orEmpty(),
                    name = pickedFile?.second,
                )
            },
        )
    }

    if (state is OnboardingUiState.Success) {
        LaunchedEffect(Unit) { onConnected() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.bg)
            .drawBehind {
                // Два пятна фона: фиолетовое слева сверху и зелёное справа снизу.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(TvColors.accentStrong.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.10f),
                        radius = size.maxDimension * 0.42f,
                    ),
                    radius = size.maxDimension * 0.42f,
                    center = Offset(size.width * 0.12f, size.height * 0.10f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(TvColors.green.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.95f),
                        radius = size.maxDimension * 0.36f,
                    ),
                    radius = size.maxDimension * 0.36f,
                    center = Offset(size.width * 0.95f, size.height * 0.95f),
                )
            }
            // Esc закрывает экран только при входе из настроек (первый запуск закрывать некуда).
            .onPreviewKeyEvent { event ->
                if (
                    onCancel != null &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.Escape
                ) {
                    onCancel()
                    true
                } else {
                    false
                }
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val wide = maxWidth >= 900.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val shape = RoundedCornerShape(24.dp)
                Row(
                    modifier = Modifier
                        .widthIn(max = 1040.dp)
                        .fillMaxWidth()
                        .shadow(
                            elevation = 32.dp,
                            shape = shape,
                            ambientColor = Color.Black,
                            spotColor = Color.Black,
                        )
                        .clip(shape)
                        .background(TvColors.panel.copy(alpha = 0.88f))
                        .border(1.dp, TvColors.line, shape)
                        .padding(32.dp),
                    horizontalArrangement = Arrangement.spacedBy(34.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BrandHeader()
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = "Подключите источник каналов",
                            style = TvType.onboardingTitle,
                            color = TvColors.text,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Ссылка на плейлист, файл с устройства, логин Xtream " +
                                "или код iedem.tv.",
                            style = TvType.onboardingSubtitle,
                            color = TvColors.muted,
                            modifier = Modifier.widthIn(max = 460.dp),
                        )
                        Spacer(Modifier.height(22.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SourceType.entries.forEach { type ->
                                SourceChip(
                                    title = type.title,
                                    active = sourceType == type,
                                    onClick = {
                                        if (sourceType != type) {
                                            sourceType = type
                                            viewModel.reset()
                                            focusEpoch++
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))

                        Column(
                            modifier = Modifier
                                .widthIn(max = 700.dp)
                                .fillMaxWidth()
                                .alpha(if (state is OnboardingUiState.Loading) 0.45f else 1f),
                        ) {
                            Fields(
                                sourceType = sourceType,
                                loading = state is OnboardingUiState.Loading,
                                focusEpoch = focusEpoch,
                                m3uUrl = m3uUrl,
                                onM3uUrl = { m3uUrl = it },
                                server = server,
                                onServer = { server = it },
                                username = username,
                                onUsername = { username = it },
                                password = password,
                                onPassword = { password = it },
                                passwordVisible = passwordVisible,
                                onTogglePassword = { passwordVisible = !passwordVisible },
                                code = code,
                                onCode = { code = it },
                                pickedFile = pickedFile,
                                onPickFile = { pickPlaylist.launch(arrayOf("*/*")) },
                                onSubmit = submitSource,
                            )
                        }

                        val error = (state as? OnboardingUiState.Error)?.reason
                        if (error != null) {
                            Spacer(Modifier.height(14.dp))
                            ErrorPlaque(text = error)
                        }

                        Spacer(Modifier.height(16.dp))
                        if (state is OnboardingUiState.Loading) {
                            LoadingButton()
                        } else {
                            SubmitButton(onClick = submitSource)
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (state is OnboardingUiState.Loading) {
                                "Проверяем источник и загружаем каталог"
                            } else {
                                "OK — клавиатура · Далее — следующее поле · Готово — подключить"
                            },
                            style = TvType.caption,
                            color = TvColors.muted,
                        )

                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(
                            text = "Восстановить настройки из файла",
                            enabled = state !is OnboardingUiState.Loading,
                            onClick = { pickSettings.launch(arrayOf("*/*")) },
                        )

                        if (onCancel != null) {
                            Spacer(Modifier.height(14.dp))
                            CancelButton(onClick = onCancel)
                        }
                    }

                    if (wide) {
                        HowItWorks()
                    }
                }
            }
        }
    }
}

/**
 * Поля ввода выбранного способа подключения.
 *
 * @param sourceType выбранный способ;
 * @param loading идёт загрузка: поля не принимают ввод;
 * @param focusEpoch счётчик запросов фокуса;
 * @param m3uUrl адрес плейлиста;
 * @param onM3uUrl правка адреса;
 * @param server сервер Xtream;
 * @param onServer правка сервера;
 * @param username логин Xtream;
 * @param onUsername правка логина;
 * @param password пароль Xtream;
 * @param onPassword правка пароля;
 * @param passwordVisible показан ли пароль;
 * @param onTogglePassword переключатель показа пароля;
 * @param code код iedem.tv;
 * @param onCode правка кода;
 * @param pickedFile выбранный файл;
 * @param onPickFile открыть проводник;
 * @param onSubmit отправка формы по «Готово».
 */
@Composable
private fun Fields(
    sourceType: SourceType,
    loading: Boolean,
    focusEpoch: Int,
    m3uUrl: String,
    onM3uUrl: (String) -> Unit,
    server: String,
    onServer: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePassword: () -> Unit,
    code: String,
    onCode: (String) -> Unit,
    pickedFile: Pair<String, String?>?,
    onPickFile: () -> Unit,
    onSubmit: () -> Unit,
) {
    when (sourceType) {
        SourceType.M3U_URL -> OnboardingField(
            label = "Адрес плейлиста",
            placeholder = "http://example.com/list.m3u",
            value = m3uUrl,
            onValueChange = onM3uUrl,
            enabled = !loading,
            imeAction = ImeAction.Done,
            onDone = onSubmit,
            focusEpoch = focusEpoch,
        )

        SourceType.XTREAM -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OnboardingField(
                label = "Сервер",
                placeholder = "http://example.com:8080",
                value = server,
                onValueChange = onServer,
                enabled = !loading,
                imeAction = ImeAction.Next,
                onDone = onSubmit,
                focusEpoch = focusEpoch,
            )
            OnboardingField(
                label = "Логин",
                placeholder = "логин провайдера",
                value = username,
                onValueChange = onUsername,
                enabled = !loading,
                imeAction = ImeAction.Next,
                onDone = onSubmit,
            )
            OnboardingField(
                label = "Пароль",
                placeholder = "пароль провайдера",
                value = password,
                onValueChange = onPassword,
                enabled = !loading,
                imeAction = ImeAction.Done,
                onDone = onSubmit,
                password = true,
                passwordVisible = passwordVisible,
                onTogglePassword = onTogglePassword,
            )
        }

        SourceType.IEDEM_CODE -> OnboardingField(
            label = "Код из личного кабинета",
            placeholder = "например, 4821 7304",
            value = code,
            onValueChange = onCode,
            enabled = !loading,
            imeAction = ImeAction.Done,
            onDone = onSubmit,
            focusEpoch = focusEpoch,
        )

        SourceType.FILE -> FilePlaque(
            pickedFile = pickedFile,
            enabled = !loading,
            onPickFile = onPickFile,
        )
    }
}

/** Брендовая шапка карточки: марка 38 dp, название и подпись «телевидение». */
@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(TvRadius.mark))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF845EE5), TvColors.accent)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "С", style = TvType.panelTitle, color = Color.White)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(text = "Смотришка", style = TvType.watchButton, color = TvColors.text)
            Text(text = "ТЕЛЕВИДЕНИЕ", style = TvType.eyebrow, color = TvColors.muted)
        }
    }
}

/**
 * Чип способа подключения: активный — светлая плашка с тёмным текстом.
 *
 * @param title подпись чипа;
 * @param active выбран ли способ;
 * @param onClick выбор способа.
 */
@Composable
private fun SourceChip(title: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.small)
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (active) TvColors.categoryActiveBg else TvColors.panel)
            .border(1.dp, if (active) Color.Transparent else TvColors.line, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = TvType.category,
            color = if (active) TvColors.categoryActiveText else TvColors.chipIdle,
            maxLines = 1,
        )
    }
}

/**
 * Поле ввода с подписью: подпись 10/600, бокс 46 dp, радиус 12, фон `#0d1118`.
 *
 * @param label подпись поля;
 * @param placeholder подсказка внутри поля;
 * @param value текущий текст;
 * @param onValueChange правка текста;
 * @param enabled принимает ли поле ввод;
 * @param imeAction действие клавиши ввода;
 * @param onDone отправка формы по «Готово»;
 * @param focusEpoch счётчик запросов фокуса: новое значение ставит фокус в поле;
 * @param password скрывать ли ввод;
 * @param passwordVisible показан ли пароль;
 * @param onTogglePassword переключатель показа пароля.
 */
@Composable
private fun OnboardingField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit,
    focusEpoch: Int = 0,
    password: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: () -> Unit = {},
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }

    // Фокус ставится только при смене способа подключения, а не при старте экрана: иначе
    // клавиатура выезжала бы сама при каждом открытии онбординга.
    LaunchedEffect(focusEpoch) {
        if (focusEpoch > 0 && enabled) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = TvType.fieldLabel, color = TvColors.fieldLabel)
        Spacer(Modifier.height(6.dp))
        val shape = RoundedCornerShape(TvRadius.control)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(shape)
                .background(TvColors.fieldBg)
                .border(
                    width = 1.dp,
                    color = if (focused) TvColors.accent.copy(alpha = 0.6f) else TvColors.line,
                    shape = shape,
                )
                .then(
                    if (focused) {
                        Modifier.drawBehind {
                            drawRoundRect(
                                color = TvColors.accent.copy(alpha = 0.16f),
                                topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                                size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
                                cornerRadius = CornerRadius(
                                    TvRadius.control.toPx() + 3.dp.toPx(),
                                    TvRadius.control.toPx() + 3.dp.toPx(),
                                ),
                                style = Stroke(width = 3.dp.toPx()),
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = TvType.field.copy(color = TvColors.text),
                cursorBrush = SolidColor(TvColors.accent),
                visualTransformation = if (password && !passwordVisible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = {
                        keyboard?.hide()
                        onDone()
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .bringIntoViewRequester(bringIntoView)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focus ->
                        focused = focus.isFocused
                        if (focus.isFocused) {
                            keyboard?.show()
                            scope.launch { runCatching { bringIntoView.bringIntoView() } }
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.isOkKey()) {
                            keyboard?.show()
                            true
                        } else {
                            false
                        }
                    },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TvType.field,
                                color = TvColors.placeholder,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (password) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (passwordVisible) "СКРЫТЬ" else "ПОКАЗАТЬ",
                    style = TvType.badge,
                    color = TvColors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onTogglePassword() }
                        .focusable()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Плашка выбора файла плейлиста: пунктирная рамка, имя файла и кнопка справа.
 *
 * @param pickedFile выбранный файл (uri и имя), `null` — файл не выбран;
 * @param enabled активна ли плашка;
 * @param onPickFile открыть системный проводник.
 */
@Composable
private fun FilePlaque(
    pickedFile: Pair<String, String?>?,
    enabled: Boolean,
    onPickFile: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .clip(shape)
            .background(TvColors.fieldBg.copy(alpha = 0.5f))
            .drawBehind {
                drawRoundRect(
                    color = TvColors.lineStrong,
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    ),
                )
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pickedFile?.second?.let { "Выбран файл: $it" } ?: "Файл не выбран",
                style = TvType.category,
                color = TvColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "M3U или M3U8 с устройства или из облака",
                style = TvType.caption,
                color = TvColors.muted,
            )
        }
        Spacer(Modifier.width(12.dp))
        SecondaryButton(text = "Выбрать файл", enabled = enabled, onClick = onPickFile)
    }
}

/**
 * Второстепенная кнопка: рамка `var(--line)`, текст 10/600.
 *
 * @param text подпись;
 * @param enabled активна ли кнопка;
 * @param onClick действие.
 */
@Composable
private fun SecondaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.choice)
    Box(
        modifier = Modifier
            .heightIn(min = 39.dp)
            .clip(shape)
            .background(TvColors.panel)
            .border(1.dp, if (focused) TvColors.accent.copy(alpha = 0.34f) else TvColors.line, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled) { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = TvType.programme, color = TvColors.text)
    }
}

/**
 * Кнопка «Подключить»: градиент, 48 dp, радиус 13, текст 13/700.
 *
 * @param onClick отправка формы.
 */
@Composable
private fun SubmitButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.item)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = Color(0xFF784EDE).copy(alpha = 0.22f),
                spotColor = Color(0xFF784EDE).copy(alpha = 0.22f),
            )
            .clip(shape)
            .background(TvButtonGradient)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape, color = Color.White)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Подключить", style = TvType.watchButton, color = Color.White)
    }
}

/** Состояние загрузки вместо кнопки: спиннер 17 и текст «Загружаем каналы…». */
@Composable
private fun LoadingButton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(17.dp),
            strokeWidth = 2.dp,
            color = TvColors.accent,
        )
        Text(text = "Загружаем каналы…", style = TvType.category, color = TvColors.text)
    }
}

/**
 * Кнопка «Отмена» при входе из настроек: 44 dp, радиус 12, фон `var(--panel)`.
 *
 * @param onClick возврат в настройки.
 */
@Composable
private fun CancelButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.control)
    Box(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(TvColors.panel)
            .border(1.dp, TvColors.line, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable()
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Отмена", style = TvType.programmeButton, color = TvColors.text)
    }
}

/**
 * Плашка ошибки под формой: круг с «!» и текст причины.
 *
 * @param text причина ошибки из состояния экрана.
 */
@Composable
private fun ErrorPlaque(text: String) {
    val shape = RoundedCornerShape(TvRadius.control)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(TvColors.errorBg)
            .border(1.dp, TvColors.errorLine, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(TvColors.favorite),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "!", style = TvType.badge, color = Color.White)
        }
        Text(text = text, style = TvType.programme, color = TvColors.errorText)
    }
}

/** Правая колонка «Как это работает» (решение Д11): три шага. */
@Composable
private fun HowItWorks() {
    Column(
        modifier = Modifier
            .width(256.dp)
            .drawBehind {
                drawLine(
                    color = TvColors.line,
                    start = Offset.Zero,
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(start = 26.dp),
    ) {
        Text(text = "КАК ЭТО РАБОТАЕТ", style = TvType.eyebrow, color = TvColors.eyebrow)
        Spacer(Modifier.height(14.dp))
        Step(number = "01", text = "Выберите способ подключения — поля появятся под ним")
        Step(number = "02", text = "Введите данные: с пульта откроется системная клавиатура")
        Step(number = "03", text = "Каталог загрузится сам, каналы появятся в списке")
    }
}

/**
 * Шаг правой колонки: номер акцентом и текст.
 *
 * @param number номер шага;
 * @param text пояснение.
 */
@Composable
private fun Step(number: String, text: String) {
    Row(
        modifier = Modifier.padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(text = number, style = TvType.fieldLabel, color = TvColors.accent)
        Text(text = text, style = TvType.programme, color = Color(0xFF98A0AE))
    }
}

/** OK пульта ДУ: центр крестовины или Enter. */
private fun KeyEvent.isOkKey(): Boolean =
    key == Key.DirectionCenter || key == Key.Enter

/** Способы подключения на экране онбординга. */
private enum class SourceType(val title: String) {
    M3U_URL("Ссылка M3U"),
    XTREAM("Xtream"),
    IEDEM_CODE("Код iedem.tv"),
    FILE("Плейлист из файла"),
}

/** Имя выбранного файла для подписи; `null`, если провайдер его не отдал. */
private fun displayNameOf(context: android.content.Context, uri: String): String? =
    runCatching {
        context.contentResolver.query(android.net.Uri.parse(uri), null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()
