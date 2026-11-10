package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import kotlinx.coroutines.flow.Flow

/**
 * Наблюдает за состоянием подключения медиатеки.
 *
 * Пункт «Медиатека» в меню показывается только при [VodPortalStatus.Connected]: проверка
 * порталом уже прошла. Сохранённые, но отвергнутые данные пункт не показывают.
 *
 * @param configPort хранилище подключения.
 */
class ObserveVodConnection(private val configPort: VodConfigPort) {

    /** Поток состояния подключения. */
    operator fun invoke(): Flow<VodPortalStatus> = configPort.status
}
