package com.mops1k.smotrishka.tv.core.vod.data.portal

import com.google.gson.JsonObject
import com.mops1k.smotrishka.tv.core.vod.domain.VodCatalogPage
import com.mops1k.smotrishka.tv.core.vod.domain.VodTitleDetails
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisode
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodStream
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleKind

/**
 * Разбор ответов портала в доменные модели.
 *
 * Разбор намеренно терпимый: API приватное и недокументированное, поэтому отсутствующее поле
 * даёт значение по умолчанию, а не исключение. Элемент без `fid` или без названия
 * пропускается — показать его всё равно нечем.
 */

/** Элементы каталога или поиска, кроме служебного маркера страницы. */
internal fun JsonObject.toTitles(): List<VodTitle> =
    items()
        .filterNot { it.itemType() == TYPE_NEXT }
        .mapNotNull { it.toTitle() }

/** Признак продолжения списка: маркер `next` с полем `request.offset`. */
internal fun JsonObject.toNextOffset(): Int? =
    items()
        .firstOrNull { it.itemType() == TYPE_NEXT }
        ?.getAsJsonObject(FIELD_REQUEST)
        ?.get(FIELD_OFFSET)
        ?.takeIf { !it.isJsonNull }
        ?.asInt

/** Категории из корневого ответа. */
internal fun JsonObject.toCategories(): List<VodCategory> =
    items()
        .filter { it.itemType() == TYPE_CATEGORY }
        .mapNotNull { item ->
            // В корневом ответе идентификатор категории лежит внутри request, а не рядом с title.
            val id = item.getAsJsonObject(FIELD_REQUEST)
                ?.get(FIELD_FID)
                ?.takeIf { !it.isJsonNull }
                ?.asInt
                ?: item.get(FIELD_FID)?.takeIf { !it.isJsonNull }?.asInt
                ?: return@mapNotNull null
            val title = item.get(FIELD_TITLE)?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            VodCategory(id = VodCategoryId(id), title = title, total = null)
        }

/** Страница каталога вместе с курсором и общим числом карточек. */
internal fun JsonObject.toCatalogPage(): VodCatalogPage = VodCatalogPage(
    titles = toTitles(),
    nextOffset = toNextOffset(),
    total = get(FIELD_COUNT)?.takeIf { !it.isJsonNull }?.asInt,
)

/**
 * Детали карточки: поток фильма либо серии сезона.
 *
 * Портал отдаёт либо один элемент (вариант качества того же фильма), либо список серий
 * с названиями «Серия N». Сезоны вкладываются отдельными карточками, поэтому других
 * вложенных уровней не бывает.
 *
 * @param fallback карточка из списка — источник названия, постера и года, если портал их не повторил;
 * @param listTitleId идентификатор, по которому карточку запросили.
 */
internal fun JsonObject.toTitleDetails(
    fallback: VodTitle,
    listTitleId: VodTitleId,
): VodTitleDetails {
    val elements = items()
    val episodeLike = elements.firstOrNull()?.get(FIELD_TITLE)?.takeIf { !it.isJsonNull }?.asString
        ?.startsWith(EPISODE_PREFIX, ignoreCase = true) == true

    val episodes = if (episodeLike || elements.size > 1) {
        elements.mapIndexedNotNull { index, item ->
            item.toEpisode(fallbackId = listTitleId, fallbackNumber = index + 1)
        }
    } else {
        emptyList()
    }

    // Одиночный фильм отдаёт поток полями самого ответа (`url`, `variants`, `duration`),
    // а сезон — сериями внутри `items`. Поэтому сначала серии, потом поток ответа.
    val stream = if (episodes.isEmpty()) {
        elements.firstOrNull()?.toStream() ?: toStream()
    } else {
        null
    }

    return VodTitleDetails(
        title = fallback.copy(kind = if (episodes.isNotEmpty()) VodTitleKind.MULTISTREAM else fallback.kind),
        stream = stream,
        episodes = episodes,
    )
}

/** Поля одного элемента списка в доменную карточку. */
internal fun JsonObject.toTitle(): VodTitle? {
    val id = get(FIELD_FID)?.takeIf { !it.isJsonNull }?.asInt ?: return null
    val title = get(FIELD_TITLE)?.takeIf { !it.isJsonNull }?.asString ?: return null
    return VodTitle(
        id = VodTitleId(id),
        title = title,
        year = get(FIELD_YEAR)?.takeIf { !it.isJsonNull }?.asInt?.takeIf { it > 0 },
        posterUrl = get(FIELD_IMAGE)?.takeIf { !it.isJsonNull }?.asString,
        previewPosterUrl = get(FIELD_IMAGE_SMALL)?.takeIf { !it.isJsonNull }?.asString,
        description = get(FIELD_DESCRIPTION)?.takeIf { !it.isJsonNull }?.asString,
        kind = if (itemType() == TYPE_MULTISTREAM) VodTitleKind.MULTISTREAM else VodTitleKind.STREAM,
        durationSeconds = get(FIELD_DURATION)?.takeIf { !it.isJsonNull }?.asLong?.takeIf { it > 0L },
        isFhd = flag(FIELD_FHD),
        is4k = flag(FIELD_4K),
        isHdr = flag(FIELD_HDR),
        ageLimit = get(FIELD_AGE_LIMIT)?.takeIf { !it.isJsonNull }?.asInt?.takeIf { it > 0 },
    )
}

/** Поток из элемента: адрес, варианты качества и длительность. */
internal fun JsonObject.toStream(): VodStream? {
    val url = get(FIELD_URL)?.takeIf { !it.isJsonNull }?.asString ?: return null
    return VodStream(
        url = url,
        qualities = get(FIELD_VARIANTS)
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.toQualityMap()
            .orEmpty(),
        durationSeconds = get(FIELD_DURATION)?.takeIf { !it.isJsonNull }?.asLong?.takeIf { it > 0L },
    )
}

/** Серия сезона из элемента `multistream`. */
private fun JsonObject.toEpisode(fallbackId: VodTitleId, fallbackNumber: Int): VodEpisode? {
    val id = get(FIELD_FID)?.takeIf { !it.isJsonNull }?.asInt ?: return null
    val title = get(FIELD_TITLE)?.takeIf { !it.isJsonNull }?.asString ?: return null
    val number = EPISODE_NUMBER.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: fallbackNumber
    return VodEpisode(
        number = VodEpisodeNumber(number),
        id = VodTitleId(id),
        title = title,
        streamUrl = get(FIELD_URL)?.takeIf { !it.isJsonNull }?.asString,
    )
}

/** Варианты качества: незнакомые ключи отбрасываются. */
internal fun JsonObject.toQualityMap(): Map<VodQuality, String> =
    entrySet().mapNotNull { (key, value) ->
        val quality = VodQuality.fromKey(key) ?: return@mapNotNull null
        if (value.isJsonNull) return@mapNotNull null
        quality to value.asString
    }.toMap()

/** Список `items` ответа, если он есть. */
private fun JsonObject.items(): List<JsonObject> =
    get(FIELD_ITEMS)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { it.takeIf { element -> element.isJsonObject }?.asJsonObject }
        .orEmpty()

/** Поле `type` элемента. */
private fun JsonObject.itemType(): String? = get(FIELD_TYPE)?.takeIf { !it.isJsonNull }?.asString

/** Булев флаг портала; отсутствие — `false`. */
private fun JsonObject.flag(name: String): Boolean =
    get(name)?.takeIf { !it.isJsonNull }?.asBoolean ?: false

/** Вложенный объект по имени. */
private fun JsonObject.getAsJsonObject(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

private const val FIELD_ITEMS = "items"
private const val FIELD_TYPE = "type"
private const val FIELD_FID = "fid"
private const val FIELD_TITLE = "title"
private const val FIELD_YEAR = "year"
private const val FIELD_IMAGE = "img"
private const val FIELD_IMAGE_SMALL = "imglr"
private const val FIELD_DESCRIPTION = "description"
private const val FIELD_DURATION = "duration"
private const val FIELD_URL = "url"
private const val FIELD_VARIANTS = "variants"
private const val FIELD_FHD = "fhd"
private const val FIELD_4K = "4k"
private const val FIELD_HDR = "hdr"
private const val FIELD_AGE_LIMIT = "agelimit"
private const val FIELD_COUNT = "count"
private const val FIELD_REQUEST = "request"
private const val FIELD_OFFSET = "offset"

private const val TYPE_CATEGORY = "category"
private const val TYPE_MULTISTREAM = "multistream"
private const val TYPE_NEXT = "next"
private const val EPISODE_PREFIX = "Серия"

private val EPISODE_NUMBER = Regex("(\\d+)")
