package com.mops1k.smotrishka.tv.ui.about

import com.mops1k.smotrishka.tv.update.UpdateState

/**
 * Состояние окна «О приложении».
 *
 * @param versionLabel строка версии: «Версия 0.1.0 (сборка 1)»;
 * @param update состояние проверки обновлений;
 * @param includePrereleases предлагать пререлизы;
 * @param wifiOnly проверять и скачивать автоматически только по Wi-Fi;
 * @param metered текущая сеть мобильная;
 * @param canInstall установка из этого приложения разрешена системой.
 */
data class AboutUiState(
    val versionLabel: String,
    val update: UpdateState = UpdateState.Idle,
    val includePrereleases: Boolean = false,
    val wifiOnly: Boolean = true,
    val metered: Boolean = false,
    val canInstall: Boolean = true,
)
