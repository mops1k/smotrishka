package com.mops1k.smotrishka.tv.ui.onboarding

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import kotlinx.coroutines.launch

/**
 * Экран подключения источника.
 *
 * Один экран на все способы ввода: тип выбирается сверху, поля меняются под него.
 * Ошибки показываются текстом прямо под формой — без диалогов, чтобы их было видно с дивана.
 *
 * @param onConnected вызывается после успешного подключения;
 * @param viewModel экранная модель.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    onConnected: () -> Unit,
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
    // Файл, выбранный системным проводником: адрес и имя для подписи на экране.
    var pickedFile by remember { mutableStateOf<Pair<String, String?>?>(null) }
    // Счётчик запросов фокуса: выбор способа подключения ставит фокус в первое поле ввода,
    // чтобы с пульта не искать его крестовиной.
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

    // Отправка формы одна и та же для кнопки «Подключить» и для клавиши «Готово» на системной
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

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(text = "Смотришка ТВ", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Подключите источник каналов: ссылку на плейлист, файл с устройства, логин Xtream или код iedem.tv.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceType.entries.forEach { type ->
                    FilterChip(
                        selected = sourceType == type,
                        onClick = {
                            sourceType = type
                            focusEpoch++
                        },
                        label = { Text(type.title, maxLines = 1) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                when (sourceType) {
                    SourceType.M3U_URL -> InputField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it },
                        label = "Адрес плейлиста (http://…/list.m3u)",
                        onDone = submitSource,
                        focusEpoch = focusEpoch,
                    )

                    SourceType.XTREAM -> Column {
                        InputField(
                            value = server,
                            onValueChange = { server = it },
                            label = "Сервер (http://example.com:8080)",
                            onDone = submitSource,
                            imeAction = ImeAction.Next,
                            focusEpoch = focusEpoch,
                        )
                        Spacer(Modifier.height(12.dp))
                        InputField(
                            value = username,
                            onValueChange = { username = it },
                            label = "Логин",
                            onDone = submitSource,
                            imeAction = ImeAction.Next,
                        )
                        Spacer(Modifier.height(12.dp))
                        InputField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Пароль",
                            onDone = submitSource,
                            password = true,
                        )
                    }

                    SourceType.IEDEM_CODE -> InputField(
                        value = code,
                        onValueChange = { code = it },
                        label = "Код из личного кабинета iedem.tv",
                        onDone = submitSource,
                        focusEpoch = focusEpoch,
                    )

                    SourceType.FILE -> Column {
                        Text(
                            text = pickedFile?.second?.let { "Выбран файл: $it" } ?: "Файл не выбран",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { pickPlaylist.launch(arrayOf("*/*")) }) {
                            Text("Выбрать файл")
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                if (state is OnboardingUiState.Error) {
                    Text(
                        text = (state as OnboardingUiState.Error).reason,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (state is OnboardingUiState.Loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp))
                        Spacer(Modifier.height(0.dp))
                        Text(
                            text = "  Загружаем каналы…",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    Button(onClick = submitSource) {
                        Text("Подключить")
                    }
                }
            }
        }
    }

    if (state is OnboardingUiState.Success) {
        onConnected()
    }
}

/**
 * Поле ввода, рассчитанное на пульт ДУ.
 *
 * Своего экранного набора приложение не содержит: показывается системная клавиатура устройства.
 * Она открывается по получению фокуса и по нажатию OK — на части приставок фокус сам её не
 * поднимает, и тогда ввод с пульта был бы невозможен. «Далее» переводит фокус на следующее
 * поле, «Готово» отправляет форму: ввод не требует возврата пультом к кнопке подключения.
 *
 * @param value текущий текст;
 * @param onValueChange правка текста;
 * @param label подпись поля;
 * @param onDone действие по «Готово» — отправка формы;
 * @param imeAction действие клавиши ввода;
 * @param focusEpoch счётчик запросов фокуса: каждое новое значение ставит фокус в это поле;
 * @param password скрывать введённые символы.
 */
@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onDone: () -> Unit,
    imeAction: ImeAction = ImeAction.Done,
    focusEpoch: Int = 0,
    password: Boolean = false,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // Фокус запрашивается только когда поле выбрано способом подключения, а не при старте
    // экрана: иначе клавиатура выезжала бы сама при каждом открытии онбординга.
    LaunchedEffect(focusEpoch) {
        if (focusEpoch > 0) {
            focusRequester.requestFocus()
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) {
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
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoView)
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    keyboard?.show()
                    // Поле должно остаться видимым, когда клавиатура займёт половину экрана.
                    scope.launch { bringIntoView.bringIntoView() }
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
    )
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
