package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodResult
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodSearchPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.connectedConfig

/**
 * Ищет фильмы и сериалы в медиатеке.
 *
 * Поиск идёт на портале: локального индекса у медиатеки нет, поэтому без сети он не работает.
 *
 * @param searchPort поиск портала;
 * @param configPort хранилище подключения.
 */
class SearchVodTitles(
    private val searchPort: VodSearchPort,
    private val configPort: VodConfigPort,
) {

    /**
     * Ищет карточки по строке.
     *
     * @param query строка поиска; пустая строка не отправляется на портал;
     * @param offset смещение для догрузки результатов.
     */
    suspend operator fun invoke(query: String, offset: Int = 0): VodResult<List<VodTitle>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return VodResult.Success(emptyList())
        return when (val config = configPort.connectedConfig()) {
            is VodResult.Success -> searchPort.search(config.value, trimmed, offset)
            is VodResult.Failure -> config
        }
    }
}
