package com.mops1k.smotrishka.tv.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import kotlinx.coroutines.delay

/** Сколько живёт тост: 2.4 с (`docs/design/flows.md`, «Таймеры»). */
const val TOAST_DURATION_MS = 2_400L

/**
 * Тост — единый индикатор подтверждения действий: снизу по центру, 2.4 с, один за раз.
 *
 * @param message текст подтверждения, `null` — тост скрыт;
 * @param onDismiss вызывается по истечении 2.4 с: экран снимает сообщение;
 * @param modifier положение тоста на экране.
 */
@Composable
fun TvToast(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    LaunchedEffect(message) {
        delay(TOAST_DURATION_MS)
        onDismiss()
    }

    val shape = RoundedCornerShape(TvRadius.item)
    Row(
        modifier = modifier
            .shadow(elevation = 15.dp, shape = shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(TvColors.toastBg)
            .border(1.dp, TvColors.lineStrong.copy(alpha = 0.1f), shape)
            .padding(start = 10.dp, end = 17.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(TvDimens.toastIcon)
                .clip(CircleShape)
                .background(TvColors.toastIconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = TvColors.toastIcon,
                modifier = Modifier.size(17.dp),
            )
        }
        Text(text = message, style = TvType.programme, color = TvColors.text)
    }
}
