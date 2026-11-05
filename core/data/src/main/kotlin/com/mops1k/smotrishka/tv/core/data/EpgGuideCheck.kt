package com.mops1k.smotrishka.tv.core.data

/**
 * Результат проверки гида перед сохранением.
 */
sealed interface EpgGuideCheck {

    /** Гид отвечает и отдаёт XMLTV — его можно сохранять. */
    data object Available : EpgGuideCheck

    /**
     * Гид сохранять нельзя.
     *
     * @param reason причина для пользователя.
     */
    data class Unavailable(val reason: String) : EpgGuideCheck
}
