package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Идентификатор фильма, сезона или серии — `fid` портала.
 *
 * Один и тот же тип используется для карточки и для серии: у серии свой `fid`, и портал
 * открывает её тем же запросом, что и фильм.
 *
 * @property value числовой идентификатор.
 */
@JvmInline
value class VodTitleId(val value: Int)
