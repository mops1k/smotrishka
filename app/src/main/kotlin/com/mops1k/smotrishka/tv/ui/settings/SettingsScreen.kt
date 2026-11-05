package com.mops1k.smotrishka.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mops1k.smotrishka.tv.core.data.AudioLanguage
import com.mops1k.smotrishka.tv.core.data.PlayerSettingsStore
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import com.mops1k.smotrishka.tv.core.model.EpgGuide

/**
 * Настройки в виде простых строк «параметр — значение».
 *
 * Строки без карточек: так экран читается с дивана и с пульта. Смена источника открывает
 * онбординг с подставленными значениями, чтобы править, а не вводить заново.
 *
 * @param onChangeSource переход к смене источника;
 * @param onBack возврат на главный экран (пульт и системная кнопка «назад»);
 * @param viewModel экранная модель.
 */
@Composable
fun SettingsScreen(
    onChangeSource: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bufferSeconds by viewModel.bufferSeconds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val updating by viewModel.updating.collectAsStateWithLifecycle()
    val fileSource by viewModel.fileSource.collectAsStateWithLifecycle()
    val quality by viewModel.videoQuality.collectAsStateWithLifecycle()
    val retryAttempts by viewModel.retryAttempts.collectAsStateWithLifecycle()
    val epgWindowHours by viewModel.epgWindowHours.collectAsStateWithLifecycle()
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val audioLanguage by viewModel.audioLanguage.collectAsStateWithLifecycle()
    val archiveDepthHours by viewModel.archiveDepthHours.collectAsStateWithLifecycle()
    val guides by viewModel.guides.collectAsStateWithLifecycle()
    val guideBusy by viewModel.guideBusy.collectAsStateWithLifecycle()
    val guideMessage by viewModel.guideMessage.collectAsStateWithLifecycle()
    var guideDialog by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(text = "Настройки", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            // Импорт плейлиста файлом: системный проводник, затем разбор содержимого.
            val pickPlaylist = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    // Persistable-разрешение: файл остаётся источником и перечитывается
                    // при «Обновить» уже после перезапуска приложения.
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
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
                            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                        }
                    }.getOrNull()
                    viewModel.importPlaylist(text, name, uri.toString())
                }
            }

            SettingsRow(
                title = "Плейлист из файла",
                value = "M3U или M3U8 с устройства или из облака",
                action = "Выбрать файл",
                onAction = { pickPlaylist.launch(arrayOf("*/*")) },
            )

            SettingsRow(
                title = "Каталог по коду провайдера",
                value = "Вернуть каталог из сохранённого источника",
                action = "Вернуть",
                onAction = viewModel::returnToProviderSource,
            )

            SettingsRow(
                title = "Источник",
                value = fileSource?.let { "Сейчас: плейлист из файла «$it»" } ?: when (val current = state) {
                    SettingsUiState.Loading -> "…"
                    SettingsUiState.Empty -> "не настроен"
                    is SettingsUiState.Content -> "${current.sourceLabel} · ${current.sourceDetail}"
                    is SettingsUiState.Error -> current.reason
                },
                action = "Сменить",
                onAction = onChangeSource,
            )

            // Пользователю нужно пере-скачать плейлист, когда провайдер поменял каналы
            // или когда каталог разобран старой версией приложения.
            SettingsRow(
                title = "Плейлист",
                value = when {
                    updating -> "идёт обновление…"
                    status != null -> status.orEmpty()
                    else -> "не загружался"
                },
                hint = "Скачать каналы и программу передач заново",
                action = if (updating) "Обновляется…" else "Загрузить заново",
                onAction = viewModel::updateData,
            )

            SettingsRow(
                title = "Буферизация вперёд",
                value = "$bufferSeconds с",
                hint = "Больше — спокойнее при обрывах сети",
            )

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                PlayerSettingsStore.BUFFER_CHOICES.forEach { seconds ->
                    val selected = seconds == bufferSeconds
                    Text(
                        text = "$seconds с",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .clickable { viewModel.setBufferSeconds(seconds) }
                            .focusable()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            SettingsRow(
                title = "Качество видео",
                value = quality.label(),
                hint = "Потолок действует только у каналов с несколькими вариантами качества; у остальных поток идёт в своём разрешении",
            )
            ChoiceRow(
                options = QUALITY_CHOICES.map { it.label() to (quality == it) },
                onPick = { index -> viewModel.setVideoQuality(QUALITY_CHOICES[index]) },
            )

            SettingsRow(
                title = "Повторы при обрыве",
                value = "$retryAttempts",
                hint = "Сколько раз пробовать заново, прежде чем показать ошибку",
            )
            ChoiceRow(
                options = PlayerSettingsStore.RETRY_CHOICES.map { "$it" to (it == retryAttempts) },
                onPick = { index -> viewModel.setRetryAttempts(PlayerSettingsStore.RETRY_CHOICES[index]) },
            )

            SettingsRow(
                title = "Окно программы передач",
                value = "${depthLabel(epgWindowHours)} назад",
                hint = "Меньше окно — быстрее обновление; глубину задаёт архив",
            )
            ChoiceRow(
                options = PlayerSettingsStore.EPG_WINDOW_CHOICES.map { hours ->
                    val label = if (hours % 24 == 0) "${hours / 24} сут" else "$hours ч"
                    label to (hours == epgWindowHours)
                },
                onPick = { index -> viewModel.setEpgWindowHours(PlayerSettingsStore.EPG_WINDOW_CHOICES[index]) },
            )

            // Глубина архива идёт сразу за окном: настройки зависят друг от друга —
            // окно задаёт, сколько грузить, глубина — сколько хранить.
            SettingsRow(
                title = "Глубина архива",
                value = "${depthLabel(archiveDepthHours)} назад",
                hint = "Сколько назад доступны прошедшие передачи; провайдер может отдавать меньше",
            )
            ChoiceRow(
                options = PlayerSettingsStore.ARCHIVE_DEPTH_CHOICES.map { hours ->
                    "${hours / 24} сут" to (hours == archiveDepthHours)
                },
                onPick = { index ->
                    viewModel.setArchiveDepthHours(PlayerSettingsStore.ARCHIVE_DEPTH_CHOICES[index])
                },
            )

            SettingsRow(
                title = "Источники программы передач",
                value = if (guides.isEmpty()) "нет" else "${guides.size}",
                hint = "Адрес проверяется перед сохранением; недоступный не добавляется",
                action = "Добавить",
                onAction = { guideDialog = true },
            )
            guides.forEach { guide ->
                GuideRow(guide = guide, onRemove = { viewModel.removeGuide(guide.id) })
            }
            guideMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp),
                )
            }

            SettingsRow(
                title = "Язык звука",
                value = when (audioLanguage) {
                    AudioLanguage.RUSSIAN -> "Русский"
                    AudioLanguage.ENGLISH -> "Английский"
                    AudioLanguage.ORIGINAL -> "Оригинал"
                },
                hint = "Какую дорожку выбирать, если у канала их несколько",
            )
            ChoiceRow(
                options = listOf(
                    "Русский" to (audioLanguage == AudioLanguage.RUSSIAN),
                    "Английский" to (audioLanguage == AudioLanguage.ENGLISH),
                    "Оригинал" to (audioLanguage == AudioLanguage.ORIGINAL),
                ),
                onPick = { index ->
                    viewModel.setAudioLanguage(
                        when (index) {
                            1 -> AudioLanguage.ENGLISH
                            2 -> AudioLanguage.ORIGINAL
                            else -> AudioLanguage.RUSSIAN
                        },
                    )
                },
            )

            SettingsRow(
                title = "Экономия трафика",
                value = if (dataSaver) "включена" else "выключена",
                hint = "Превью не запускается само, логотипы не загружаются",
                action = if (dataSaver) "Выключить" else "Включить",
                onAction = { viewModel.setDataSaver(!dataSaver) },
            )

            if (state is SettingsUiState.Loading) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "← Назад",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onBack() }
                    .focusable()
                    .padding(vertical = 8.dp),
            )
        }

        if (guideDialog) {
            AddGuideDialog(
                busy = guideBusy,
                message = guideMessage,
                onConfirm = { url -> viewModel.addGuide(url) },
                onDismiss = { guideDialog = false },
            )
        }
    }
}

/**
 * Ряд вариантов настройки: выбранный выделен цветом и жирным.
 *
 * @param options подписи вариантов и признак выбора;
 * @param onPick индекс выбранного варианта.
 */
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

@Composable
private fun ChoiceRow(options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        options.forEachIndexed { index, (label, selected) ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clickable { onPick(index) }
                    .focusable()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * Строка настроек: название слева, значение и действие справа.
 *
 * @param title название параметра;
 * @param value текущее значение;
 * @param hint пояснение под строкой, если нужно;
 * @param action подпись действия, `null` — действия нет;
 * @param onAction действие по нажатию.
 */
@Composable
private fun SettingsRow(
    title: String,
    value: String,
    hint: String? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 360.dp),
            )
            if (action != null) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onAction() }
                        .focusable()
                        .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                )
            }
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Строка добавленного гида: имя, адрес и удаление.
 *
 * @param guide сохранённый гид;
 * @param onRemove удаление гида из фонового разбора.
 */
@Composable
private fun GuideRow(guide: EpgGuide, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = guide.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = guide.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Удалить",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { onRemove() }
                .focusable()
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
        )
    }
}

/**
 * Окно добавления гида: адрес проверяется до сохранения.
 *
 * @param busy идёт проверка адреса;
 * @param message ответ на прошлую попытку;
 * @param onConfirm проверить и сохранить;
 * @param onDismiss закрыть окно.
 */
@Composable
private fun AddGuideDialog(
    busy: Boolean,
    message: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }

    // Успешное добавление закрывает окно само: список гидов уже обновился.
    LaunchedEffect(message) {
        if (message?.startsWith("Гид добавлен") == true) onDismiss()
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Новый гид программы передач") },
        text = {
            Column {
                Text(
                    text = "Адрес XMLTV: xml или xml.gz",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator()
                }
                if (message != null && !busy) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(url) }, enabled = !busy) {
                Text("Проверить и добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Закрыть")
            }
        },
    )
}

/**
 * Подпись настройки глубины: часы и сутки, если срок кратен суткам.
 *
 * @param hours значение в часах.
 */
private fun depthLabel(hours: Int): String =
    if (hours % 24 == 0) "$hours ч (${hours / 24} сут)" else "$hours ч"
