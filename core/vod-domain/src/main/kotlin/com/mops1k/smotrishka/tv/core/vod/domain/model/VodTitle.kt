package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Карточка медиатеки: фильм, сезон сериала, передача или запись матча.
 *
 * Данные приходят прямо из списка категории — описание и год портал отдаёт вместе с постером,
 * поэтому карточка открывается без дополнительного запроса, а запрос деталей нужен только
 * ради серий и вариантов качества.
 *
 * @property id идентификатор на портале;
 * @property title название;
 * @property year год выпуска, если известен;
 * @property posterUrl постер большого размера;
 * @property previewPosterUrl уменьшенный постер для сетки;
 * @property description описание, если портал его отдал;
 * @property kind вид карточки — от него зависит, можно ли её запускать сразу;
 * @property durationSeconds длительность, если известна (0 у портала означает «неизвестно»);
 * @property isFhd признак Full HD;
 * @property is4k признак 4K;
 * @property isHdr признак HDR;
 * @property ageLimit возрастное ограничение, если указано.
 */
data class VodTitle(
    val id: VodTitleId,
    val title: String,
    val year: Int? = null,
    val posterUrl: String? = null,
    val previewPosterUrl: String? = null,
    val description: String? = null,
    val kind: VodTitleKind = VodTitleKind.STREAM,
    val durationSeconds: Long? = null,
    val isFhd: Boolean = false,
    val is4k: Boolean = false,
    val isHdr: Boolean = false,
    val ageLimit: Int? = null,
) {

    /** Ссылка для сетки: превью, а если его нет — большой постер. */
    val gridPosterUrl: String?
        get() = previewPosterUrl ?: posterUrl
}
