package com.mops1k.smotrishka.tv.ui.player.osd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Нижняя панель плеера: время, полоса перемотки и кнопки на общей подложке.
 *
 * Общая для эфира и медиатеки: в эфире слева время передачи, в медиатеке — текущее время
 * и длительность по краям полосы. Подложка нужна, чтобы время читалось и на светлом кадре.
 *
 * @param positionLabel время слева (в эфире — диапазон передачи);
 * @param fraction текущая доля проигранного, `null` — полоса без бегунка;
 * @param onSeek выбор новой доли;
 * @param onPreview предпросмотр при перетаскивании;
 * @param onSeekBy шаг перемотки пультом;
 * @param modifier размеры панели;
 * @param durationLabel время справа (длительность записи);
 * @param hint подсказка о перемотке;
 * @param buttons ряд кнопок под полосой.
 */
@Composable
fun PlayerOsdPanel(
    positionLabel: String,
    fraction: Float?,
    onSeek: (Float) -> Unit,
    onPreview: (Float?) -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
    durationLabel: String? = null,
    hint: String? = null,
    buttons: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = positionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.92f),
            )
            Spacer(Modifier.width(14.dp))
            if (fraction != null) {
                PlayerSeekBar(
                    fraction = fraction,
                    onSeek = onSeek,
                    onSeekBy = onSeekBy,
                    onPreview = { onPreview(it) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                PlayerLiveBar(modifier = Modifier.weight(1f))
            }
            if (durationLabel != null) {
                Spacer(Modifier.width(14.dp))
                Text(
                    text = durationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.92f),
                )
            }
        }

        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }

        buttons()
    }
}
