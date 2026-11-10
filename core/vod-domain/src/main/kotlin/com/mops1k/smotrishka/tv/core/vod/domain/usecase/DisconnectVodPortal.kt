package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort

/**
 * Отключает медиатеку: забывает портал, избранное и историю просмотра.
 *
 * После этого пункт «Медиатека» пропадает из меню, а каналы продолжают работать как раньше.
 *
 * @param configPort хранилище подключения;
 * @param favoritesPort избранное медиатеки;
 * @param progressPort история просмотра.
 */
class DisconnectVodPortal(
    private val configPort: VodConfigPort,
    private val favoritesPort: VodFavoritesPort,
    private val progressPort: VodPlaybackProgressPort,
) {

    /** Выполняет отключение. */
    suspend operator fun invoke() {
        configPort.clear()
        favoritesPort.clear()
        progressPort.clear()
    }
}
