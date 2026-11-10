package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodTitlePort
import com.mops1k.smotrishka.tv.core.vod.domain.port.connectedConfig

/**
 * Загружает карточку медиатеки: поток фильма или серии сезона.
 *
 * @param titlePort карточки портала;
 * @param configPort хранилище подключения.
 */
class LoadVodTitleDetails(
    private val titlePort: VodTitlePort,
    private val configPort: VodConfigPort,
) {

    /**
     * Загружает детали карточки.
     *
     * @param titleId идентификатор карточки.
     */
    suspend operator fun invoke(titleId: VodTitleId): VodResult<VodTitleDetails> =
        when (val config = configPort.connectedConfig()) {
            is VodResult.Success -> titlePort.loadDetails(config.value, titleId)
            is VodResult.Failure -> config
        }
}
