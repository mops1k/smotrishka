package com.mops1k.smotrishka.tv.ui.common

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Единственный тост приложения: одновременно показывается один (2.4 с).
 *
 * Тост переживает переход между экранами: онбординг показывает «Источник подключён», и это
 * сообщение должно быть видно уже на «Телеканалах», а не исчезнуть вместе с экраном.
 */
@Stable
class ToastController {

    /** Текущее сообщение, `null` — тоста нет. */
    var message: String? by mutableStateOf(null)
        private set

    /**
     * Показывает сообщение, заменяя предыдущее.
     *
     * @param text текст подтверждения.
     */
    fun show(text: String) {
        message = text
    }

    /** Снимает сообщение по истечении времени показа. */
    fun clear() {
        message = null
    }
}

/** Доступ к тосту из любого экрана. */
val LocalToastController = staticCompositionLocalOf<ToastController> {
    error("ToastController не предоставлен в композиции")
}
