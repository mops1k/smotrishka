package com.mops1k.smotrishka.tv.core.vod.domain

import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure

/**
 * Результат операции медиатеки.
 *
 * Исключения через границу домена не ходят: адаптер сводит сеть, разбор и ошибки портала
 * к [Failure], а вызывающий код обязан обработать оба случая.
 *
 * @param T тип успешного значения.
 */
sealed interface VodResult<out T> {

    /**
     * Успешный результат.
     *
     * @property value значение операции.
     */
    data class Success<T>(val value: T) : VodResult<T>

    /**
     * Неудачный результат с причиной.
     *
     * @property failure причина для интерфейса.
     */
    data class Failure(val failure: VodFailure) : VodResult<Nothing>

    /** Значение при успехе, иначе `null`. */
    fun valueOrNull(): T? = (this as? Success)?.value

    /**
     * Преобразует успешное значение, сохраняя ошибку.
     *
     * @param transform преобразование значения.
     */
    fun <R> map(transform: (T) -> R): VodResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
}
