package com.mops1k.smotrishka.tv.core.model

/**
 * Параметры архива канала из плейлиста.
 *
 * @param mode режим построения архивного URL;
 * @param source шаблон `catchup-source`, если провайдер его задал;
 * @param days глубина архива в днях (`catchup-days`), 0 — провайдер не указал.
 */
data class ArchiveInfo(
    val mode: CatchupMode,
    val source: String? = null,
    val days: Int = 0,
)
