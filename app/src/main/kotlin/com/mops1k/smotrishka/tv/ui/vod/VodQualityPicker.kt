package com.mops1k.smotrishka.tv.ui.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.ui.player.osd.OsdCircleButton

/**
 * Выбор качества записи — один компонент на карточку и на плеер медиатеки.
 *
 * Кнопка показывает текущее качество («Авто», «1080p») и раскрывает список доступных вариантов.
 * Список строится из того, что прислал портал: чего нет в ответе, того нет и в меню.
 *
 * @param qualities доступные качества: ключ — вариант, значение — адрес записи;
 * @param selected выбранный вариант, `null` — «Авто»;
 * @param onSelect выбор качества;
 * @param modifier оформление места;
 * @param showLabel показывать подпись текущего качества рядом с кнопкой.
 */
@Composable
fun VodQualityPicker(
    qualities: Map<VodQuality, String>,
    selected: VodQuality?,
    onSelect: (VodQuality?) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    if (qualities.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val current = selected ?: VodQuality.AUTO

    Box(modifier = modifier) {
        Row(
            modifier = Modifier.padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showLabel) {
                Text(
                    text = current.label,
                    style = TvType.caption,
                    color = TvColors.text,
                )
            }
            OsdCircleButton(
                icon = Icons.Filled.ArrowDropDown,
                contentDescription = "Качество: ${current.label}",
                onClick = { expanded = true },
            )
        }

        VodQualityMenu(
            expanded = expanded,
            qualities = qualities,
            selected = selected,
            onDismiss = { expanded = false },
            onSelect = { quality ->
                expanded = false
                onSelect(quality)
            },
        )
    }
}
