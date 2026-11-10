package com.mops1k.smotrishka.tv.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Разделы приложения в сайдбаре: они же состояния главного экрана.
 *
 * «Настройки» остаются отдельным маршрутом, остальные три — состояния `HomeScreen`
 * (решение Д2). «Медиатека» — отдельный маршрут и показывается только после успешной
 * проверки портала (решение Александра 10.11.2026).
 *
 * @param title заголовок раздела в шапке контента;
 * @param icon иконка пункта меню.
 */
enum class ShellDestination(val title: String, val icon: ImageVector) {

    /** Телеканалы: плашка категорий и шапка списка. */
    TV("Телеканалы", Icons.Filled.Tv),

    /** Медиатека: фильмы и сериалы портала, отдельный маршрут. */
    MEDIATEKA("Медиатека", Icons.Filled.VideoLibrary),

    /** Избранное. */
    FAVORITES("Избранное", Icons.Filled.Star),

    /** Недавние. */
    RECENT("Недавние", Icons.Filled.Schedule),

    /** Настройки: отдельный маршрут. */
    SETTINGS("Настройки", Icons.Filled.Settings),

    /** О приложении: назначение, версия и обновление. */
    ABOUT("О приложении", Icons.Filled.Info),
}
