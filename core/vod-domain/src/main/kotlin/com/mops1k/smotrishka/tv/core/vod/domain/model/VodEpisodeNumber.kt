package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Номер серии внутри сезона.
 *
 * Сезон на портале — отдельная карточка, поэтому нумерация начинается с единицы внутри неё,
 * а не сквозная по сериалу.
 *
 * @property value номер серии, начиная с 1.
 */
@JvmInline
value class VodEpisodeNumber(val value: Int)
