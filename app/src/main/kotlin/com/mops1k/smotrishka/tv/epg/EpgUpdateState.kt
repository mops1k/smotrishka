package com.mops1k.smotrishka.tv.epg

/**
 * Состояние фоновой загрузки программы передач.
 *
 * Показывается на главном экране: каналы уже доступны, а гид догружается отдельно.
 */
sealed interface EpgUpdateState {

    /** Догрузка ещё не запускалась. */
    data object Idle : EpgUpdateState

    /** Гид загружается и разбирается. */
    data object Running : EpgUpdateState

    /**
     * Гид загружен.
     *
     * @param programmes сколько передач записано.
     */
    data class Done(val programmes: Int) : EpgUpdateState

    /**
     * Гид загрузить не удалось.
     *
     * @param reason причина для пользователя.
     */
    data class Failed(val reason: String) : EpgUpdateState
}
