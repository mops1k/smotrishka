package com.mops1k.smotrishka.tv.core.data.update

/**
 * Итог запроса списка релизов в GitHub.
 */
sealed interface ReleasesResult {

    /** Список релизов: только разобранные версии, без черновиков. */
    data class Success(val releases: List<SmotrishkaRelease>) : ReleasesResult

    /** Запрос не удался: нет сети, ошибка API или неразборчивый ответ. */
    data class Failure(val reason: String) : ReleasesResult
}
