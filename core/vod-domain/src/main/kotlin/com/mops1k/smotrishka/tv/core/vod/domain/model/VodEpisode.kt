package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Серия сезона.
 *
 * Портал отдаёт серии внутри карточки сезона, у каждой свой `fid` и своя ссылка на поток,
 * поэтому серию можно запустить, не запрашивая её карточку ещё раз.
 *
 * @property number номер серии внутри сезона;
 * @property id идентификатор серии на портале;
 * @property title название серии из ответа портала («Серия 3»);
 * @property streamUrl адрес потока серии, если портал его отдал.
 */
data class VodEpisode(
    val number: VodEpisodeNumber,
    val id: VodTitleId,
    val title: String,
    val streamUrl: String? = null,
    val qualities: Map<VodQuality, String> = emptyMap(),
)
