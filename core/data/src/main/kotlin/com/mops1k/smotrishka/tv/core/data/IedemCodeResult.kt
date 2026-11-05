package com.mops1k.smotrishka.tv.core.data

/**
 * Результат проверки кода провайдера.
 */
sealed interface IedemCodeResult {

    /** Код принят, в нём есть все нужные символы. */
    data class Success(val code: String) : IedemCodeResult

    /** Код введён неверно. */
    data class Invalid(val reason: String) : IedemCodeResult
}
