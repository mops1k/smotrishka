package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Идентификатор категории медиатеки — `fid` портала.
 *
 * Провайдер нумерует категории сам (1 — фильмы, 2 — сериалы и т.д.), поэтому идентификатор
 * не придумывается приложением, а переносится как есть.
 *
 * @property value числовой идентификатор категории.
 */
@JvmInline
value class VodCategoryId(val value: Int)
