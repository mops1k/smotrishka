package com.mops1k.smotrishka.tv.core.vod.domain

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodStream
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Агрегат карточки: сам фильм или сезон, его поток и серии.
 *
 * Одиночный фильм несёт [stream]; сезон несёт [episodes] и обычно не имеет своего потока.
 * Метод [nextEpisode] — единственное место, где решается, какая серия следующая: и автопереход,
 * и кнопка «Следующая» обязаны спрашивать его, а не считать номер сами.
 *
 * @property title карточка из списка;
 * @property stream поток карточки, если портал его отдал;
 * @property episodes серии сезона в порядке портала.
 */
data class VodTitleDetails(
    val title: VodTitle,
    val stream: VodStream? = null,
    val episodes: List<VodEpisode> = emptyList(),
) {

    /** Карточка является сезоном сериала. */
    val isSerial: Boolean
        get() = episodes.isNotEmpty()

    /** Первая серия — с неё начинается просмотр сезона. */
    val firstEpisode: VodEpisode?
        get() = episodes.minByOrNull { it.number.value }

    /** Что запускать по кнопке «Смотреть» без выбора серии. */
    val defaultStreamUrl: String?
        get() = stream?.url ?: firstEpisode?.streamUrl

    /**
     * Варианты качества для карточки или конкретной серии.
     *
     * @param episodeId серия, `null` — сама карточка (фильм).
     * @return доступные варианты; пусто — портал вариантов не отдал.
     */
    fun qualities(episodeId: VodTitleId? = null): Map<VodQuality, String> =
        if (episodeId == null) stream?.qualities.orEmpty() else episode(episodeId)?.qualities.orEmpty()

    /**
     * Адрес записи для выбранного качества.
     *
     * @param episodeId серия, `null` — сама карточка (фильм);
     * @param quality выбранное качество, `null` — вариант по умолчанию («Авто»).
     * @return адрес записи либо `null`, если записи нет.
     */
    fun streamUrl(episodeId: VodTitleId? = null, quality: VodQuality? = null): String? {
        if (quality != null) qualities(episodeId)[quality]?.let { return it }
        return if (episodeId == null) {
            stream?.url ?: firstEpisode?.streamUrl
        } else {
            episode(episodeId)?.streamUrl
        }
    }

    /**
     * Находит серию по идентификатору.
     *
     * @param episodeId идентификатор серии.
     */
    fun episode(episodeId: VodTitleId): VodEpisode? =
        episodes.firstOrNull { it.id == episodeId }

    /**
     * Находит серию по номеру.
     *
     * @param number номер серии внутри сезона.
     */
    fun episode(number: VodEpisodeNumber): VodEpisode? =
        episodes.firstOrNull { it.number == number }

    /**
     * Следующая серия после указанной.
     *
     * @param episodeId идентификатор текущей серии;
     * @return следующая серия либо `null`, если серия последняя.
     */
    fun nextEpisode(episodeId: VodTitleId): VodEpisode? {
        val ordered = episodes.sortedBy { it.number.value }
        val index = ordered.indexOfFirst { it.id == episodeId }
        if (index < 0) return null
        return ordered.getOrNull(index + 1)
    }

    /**
     * Предыдущая серия перед указанной.
     *
     * @param episodeId идентификатор текущей серии;
     * @return предыдущая серия либо `null`, если серия первая.
     */
    fun previousEpisode(episodeId: VodTitleId): VodEpisode? {
        val ordered = episodes.sortedBy { it.number.value }
        val index = ordered.indexOfFirst { it.id == episodeId }
        if (index <= 0) return null
        return ordered[index - 1]
    }
}
