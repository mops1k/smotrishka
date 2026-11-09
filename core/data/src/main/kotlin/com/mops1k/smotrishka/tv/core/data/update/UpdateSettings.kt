package com.mops1k.smotrishka.tv.core.data.update

/**
 * Настройки проверки обновлений.
 *
 * @param includePrereleases предлагать пререлизы (`rc`) — по умолчанию выключено;
 * @param wifiOnly проверять и скачивать автоматически только по Wi-Fi/проводной сети;
 * @param lastCheckedAtMillis когда последний раз проверяли, `null` — ещё не проверяли;
 * @param dismissedVersion версия, про которую попросили больше не сообщать;
 * @param notifiedVersion версия, про которую уже показали тост при запуске.
 */
data class UpdateSettings(
    val includePrereleases: Boolean = false,
    val wifiOnly: Boolean = true,
    val lastCheckedAtMillis: Long? = null,
    val dismissedVersion: String? = null,
    val notifiedVersion: String? = null,
)
