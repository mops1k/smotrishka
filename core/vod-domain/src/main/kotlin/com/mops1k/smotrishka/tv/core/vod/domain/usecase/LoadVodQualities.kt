package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodTitlePort
import com.mops1k.smotrishka.tv.core.vod.domain.port.connectedConfig

/**
 * Загружает доступные качества карточки или серии.
 *
 * Список качеств нужен и в карточке (меню у кнопки «Смотреть»), и в плеере (переключатель).
 * Запрашивается по требованию: в ответе каталога портал вариантов не присылает.
 *
 * @param titlePort портал карточек;
 * @param configPort подключение медиатеки.
 */
class LoadVodQualities(
    private val titlePort: VodTitlePort,
    private val configPort: VodConfigPort,
) {

    /**
     * Загружает варианты качества.
     *
     * @param titleId карточка или серия;
     * @return качества по ключам портала, при отсутствии подключения — отказ.
     */
    suspend operator fun invoke(titleId: VodTitleId): VodResult<Map<VodQuality, String>> =
        when (val config = configPort.connectedConfig()) {
            is VodResult.Success -> titlePort.loadQualities(config.value, titleId)
            is VodResult.Failure -> config
        }
}
