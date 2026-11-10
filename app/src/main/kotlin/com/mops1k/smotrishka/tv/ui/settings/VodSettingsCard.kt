package com.mops1k.smotrishka.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mops1k.smotrishka.tv.core.designsystem.component.TvGlassPanel
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType

/**
 * Карточка «Медиатека» в настройках: подключение портала провайдера.
 *
 * Пара «адрес + ключ» проверяется порталом до сохранения, поэтому подключение либо работает
 * сразу, либо показывает причину отказа и ничего не сохраняет.
 *
 * @param viewModel экранная модель карточки.
 */
@Composable
fun VodSettingsCard(viewModel: VodPortalSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsCard(
        icon = Icons.Filled.VideoLibrary,
        iconTint = Color(0xFFBDA5FC),
        iconBackground = Color(0x29956FEE),
        title = "Медиатека",
        subtitle = "Фильмы и сериалы портала провайдера",
    ) {
        SettingsRow(
            title = "Портал медиатеки",
            value = state.detail,
            hint = "Ссылка из личного кабинета провайдера",
            action = if (state.connected) "Изменить" else "Подключить",
            onAction = viewModel::openPortalDialog,
        )
        val message = state.message
        if (message != null && !state.dialogOpen) {
            SettingsRow(title = message, value = null)
        }
        if (state.connected) {
            SettingsRow(
                title = "Отключить медиатеку",
                hint = "Раздел пропадёт из меню, избранное и история просмотра сбросятся",
                action = "Отключить",
                onAction = viewModel::disconnect,
            )
        }
        if (state.checking) {
            SettingsRow(title = "Проверяем портал…", value = null)
        }
    }

    if (state.dialogOpen) {
        VodPortalDialog(
            checking = state.checking,
            message = state.message,
            onDismiss = viewModel::dismissPortalDialog,
            onSubmit = viewModel::connect,
        )
    }
}

/**
 * Диалог ввода портала: одна строка подключения из личного кабинета.
 *
 * @param checking идёт проверка порталом;
 * @param message результат прошлой проверки;
 * @param onDismiss закрыть без сохранения;
 * @param onSubmit отдать строку подключения на разбор и проверку.
 */
@Composable
internal fun VodPortalDialog(
    checking: Boolean,
    message: String?,
    onDismiss: () -> Unit,
    onSubmit: (link: String) -> Unit,
) {
    var link by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        TvGlassPanel(width = 520.dp) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Портал медиатеки", style = TvType.panelTitle, color = TvColors.text)
                Text(
                    text = "Вставьте ссылку из личного кабинета провайдера. Она выдаётся одной " +
                        "строкой вида portal::[key:…]http://…/api/v1/",
                    style = TvType.caption,
                    color = TvColors.muted,
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Ссылка на медиатеку") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (message != null) {
                    Text(text = message, style = TvType.caption, color = TvColors.muted)
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onSubmit(link.trim()) },
                        enabled = !checking && link.isNotBlank(),
                    ) {
                        Text(if (checking) "Проверяем…" else "Подключить")
                    }
                }
            }
        }
    }
}
