package com.mops1k.smotrishka.tv.ui.shell

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Признак «есть доступное обновление»: маркер на пункте «О приложении» в сайдбаре.
 *
 * Значение кладёт навигация: она читает состояние проверки обновлений и знает,
 * скрыл ли пользователь эту версию.
 */
val LocalUpdateBadge = staticCompositionLocalOf { false }
