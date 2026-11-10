package com.mops1k.smotrishka.tv.ui.player.osd

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/**
 * Фокус меню плеера: один и тот же порядок в эфире и в медиатеке.
 *
 * Правило одно: меню открывается — фокус на «Пауза», меню закрывается — фокус на поверхности
 * видео, чтобы пульт снова мог открыть меню. Заявка на фокус повторяется несколько раз: меню
 * появляется в композиции с анимацией, и первый запрос может прийти раньше кнопки.
 *
 * @property play адрес кнопки «Пауза» — цель фокуса при открытии меню;
 * @property surface адрес поверхности видео.
 */
@Stable
class PlayerOsdFocus internal constructor() {

    /** Куда встаёт фокус при открытии меню. */
    val play: FocusRequester = FocusRequester()

    /** Куда встаёт фокус, когда меню скрыто. */
    val surface: FocusRequester = FocusRequester()
}

/**
 * Запоминает фокус меню и ведёт его по состоянию видимости.
 *
 * @param osdVisible открыто ли меню;
 * @param retries сколько раз повторить заявку на фокус (меню появляется с анимацией).
 */
@Composable
fun rememberPlayerOsdFocus(osdVisible: Boolean, retries: Int = 8): PlayerOsdFocus {
    val focus = remember { PlayerOsdFocus() }

    LaunchedEffect(osdVisible) {
        if (!osdVisible) {
            runCatching { focus.surface.requestFocus() }
            return@LaunchedEffect
        }
        repeat(retries) {
            withFrameNanos { }
            runCatching { focus.play.requestFocus() }
            delay(60)
        }
    }
    return focus
}
