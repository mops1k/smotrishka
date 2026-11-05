package com.mops1k.smotrishka.tv.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import kotlin.math.absoluteValue

/** Палитра плиток-заглушек: цвет выбирается по названию канала и не меняется между запусками. */
private val LogoPalette = listOf(
    Color(0xFF6C4BC7),
    Color(0xFF2F6FB0),
    Color(0xFFB05A2F),
    Color(0xFF2F8F6F),
    Color(0xFF8F2F6B),
    Color(0xFF4B5BB0),
    Color(0xFF9A7B2F),
    Color(0xFF3F7A8F),
)

/**
 * Логотип канала: реальная картинка через Coil, плитка с буквами — заглушка.
 *
 * Решение Д5: в приложении логотипы есть (в плейлисте у канала `tvg-logo`), поэтому макетная
 * плитка остаётся только резервом — пока картинка грузится или если её нет.
 *
 * @param name название канала для букв и доступности;
 * @param url адрес логотипа, `null` — логотипа нет;
 * @param size сторона плитки;
 * @param shape скругление;
 * @param textStyle начертание букв;
 * @param showImage грузить ли картинку (выключено при экономии трафика).
 * @param modifier размеры и положение.
 */
@Composable
fun ChannelLogo(
    name: String,
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = TvDimens.logoSmall,
    shape: RoundedCornerShape = RoundedCornerShape(TvRadius.small),
    textStyle: TextStyle = LogoSmallText,
    showImage: Boolean = true,
) {
    var failed by remember(url) { mutableStateOf(false) }
    val background = logoColorOf(name)
    val showPicture = showImage && !url.isNullOrBlank() && !failed

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name),
            style = textStyle,
            color = textColorOn(background),
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (showPicture) {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                onState = { state -> failed = state is AsyncImagePainter.State.Error },
            )
        }
    }
}

/** Начертание букв в маленьком логотипе: 11/800, `letter-spacing -.5`. */
val LogoSmallText = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)

/** Начертание букв в крупном логотипе: 27/800. */
val LogoLargeText = TextStyle(fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)

/** Начертание букв в среднем логотипе разворота: 20/800. */
val LogoMediumText = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)

/**
 * Цвет плитки по названию канала: одинаковый при каждом показе, без хранения в базе.
 *
 * @param name название канала.
 */
fun logoColorOf(name: String): Color =
    LogoPalette[name.hashCode().absoluteValue % LogoPalette.size]

/**
 * Цвет букв на плите: тёмный на светлой, белый на тёмной — как в макете.
 *
 * @param background цвет плитки.
 */
fun textColorOn(background: Color): Color =
    if (background.luminance() > 0.55f) TvColors.logoText else Color.White

/**
 * Буквы для заглушки: первая буква названия, а у названий из нескольких слов — первые две.
 *
 * @param name название канала.
 */
fun initialsOf(name: String): String {
    val words = name.trim().split(' ', '-', '/').filter(String::isNotBlank)
    return when {
        words.isEmpty() -> "ТВ"
        words.size == 1 -> words.first().take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
