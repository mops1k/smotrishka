package com.mops1k.smotrishka.tv.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

/**
 * Адреса фокуса для явных направлений «влево» между меню и контентом.
 *
 * Compose ищет соседа по геометрии, и при движении влево из карточки или списка фокус мог
 * уходить сразу в боковое меню. Поэтому направления задаются явно: карточка → список →
 * категории → меню. Переход в меню с настоящего края (категории, поиск) выполняется
 * обработчиком клавиши, а не геометрией.
 */
class ShellFocusTargets {
    /** Первый пункт бокового меню (текущий раздел). */
    val menu = FocusRequester()

    /** Строки списка каналов: сюда уходит фокус из карточки. */
    val list = FocusRequester()

    /** Строки категорий: сюда уходит фокус из списка каналов. */
    val categories = FocusRequester()

    /** Кнопка поиска в шапке: на неё ведёт «вверх» с сердечка карточки. */
    val search = FocusRequester()
}

/** Создаёт [ShellFocusTargets] в пределах композиции экрана. */
@Composable
fun rememberShellFocusTargets(): ShellFocusTargets = remember { ShellFocusTargets() }
