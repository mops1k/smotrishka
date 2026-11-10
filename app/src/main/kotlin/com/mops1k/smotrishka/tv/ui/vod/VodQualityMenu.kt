package com.mops1k.smotrishka.tv.ui.vod

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality

/**
 * Меню доступных качеств записи — одно на карточку и на плеер.
 *
 * @param expanded раскрыто ли меню;
 * @param qualities доступные варианты: ключ — качество, значение — адрес записи;
 * @param selected выбранный вариант, `null` — «Авто»;
 * @param onDismiss закрыть без выбора;
 * @param onSelect выбор качества.
 */
@Composable
fun VodQualityMenu(
    expanded: Boolean,
    qualities: Map<VodQuality, String>,
    selected: VodQuality?,
    onDismiss: () -> Unit,
    onSelect: (VodQuality?) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val options = buildList {
            // «Авто» первым: это выбор по умолчанию, а не лучшее качество.
            add(null)
            qualities.keys.filter { it != VodQuality.AUTO }
                .sortedByDescending { it.key.toIntOrNull() ?: 0 }
                .forEach { add(it) }
        }
        options.forEach { quality ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = quality?.label ?: VodQuality.AUTO.label,
                        style = TvType.menuItem,
                        color = if (quality == selected) TvColors.accent else TvColors.text,
                    )
                },
                onClick = { onSelect(quality) },
            )
        }
    }
}
