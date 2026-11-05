package com.mops1k.smotrishka.tv.core.data

import com.google.gson.annotations.SerializedName

/*
 * DTO ответов Xtream API. Все поля nullable: серверы разных версий отдают
 * разные наборы полей, а отсутствующее поле не должно ронять разбор.
 */

internal data class XtreamAuthDto(
    @SerializedName("user_info") val userInfo: XtreamUserInfoDto? = null,
    @SerializedName("server_info") val serverInfo: XtreamServerInfoDto? = null,
)

internal data class XtreamUserInfoDto(
    @SerializedName("username") val username: String? = null,
    @SerializedName("auth") val auth: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("exp_date") val expDate: String? = null,
    @SerializedName("is_trial") val isTrial: String? = null,
)

internal data class XtreamServerInfoDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("port") val port: String? = null,
)

internal data class XtreamCategoryDto(
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
)

internal data class XtreamStreamDto(
    @SerializedName("stream_id") val streamId: Long? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("tv_archive") val tvArchive: Int? = null,
    @SerializedName("tv_archive_duration") val tvArchiveDuration: Int? = null,
)
