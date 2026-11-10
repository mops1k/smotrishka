package com.mops1k.smotrishka.tv.core.vod.data.backup

import com.mops1k.smotrishka.tv.core.data.backup.SettingsBackup
import com.mops1k.smotrishka.tv.core.vod.domain.VodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.VodPortalStatus
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodFavorite
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodPortalConfig
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleKind
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodConfigPort
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import com.mops1k.smotrishka.tv.core.vod.domain.usecase.ConnectVodPortal
import kotlinx.coroutines.flow.first

/**
 * Секция медиатеки в файле настроек: адрес, ключ и избранное.
 *
 * При восстановлении портал проверяется тем же use case, что и при ручном вводе: пункт
 * «Медиатека» появляется только по успешной проверке. Избранное возвращается снапшотом, чтобы
 * быть видимым и без сети.
 *
 * @param configPort подключение медиатеки;
 * @param favoritesPort избранное медиатеки;
 * @param connectVodPortal проверка и сохранение портала.
 */
class VodBackupAdapter(
    private val configPort: VodConfigPort,
    private val favoritesPort: VodFavoritesPort,
    private val connectVodPortal: ConnectVodPortal,
) : com.mops1k.smotrishka.tv.core.data.backup.VodBackupPort {

    override suspend fun read(): SettingsBackup.Vod? {
        val config = when (val status = configPort.status.first()) {
            is VodPortalStatus.Connected -> status.config
            is VodPortalStatus.Failed -> status.config
            VodPortalStatus.NotConfigured -> null
        } ?: return null

        return SettingsBackup.Vod(
            url = config.url.value,
            key = config.key.value,
            favorites = favoritesPort.favorites.first().items.map { it.toBackup() },
        )
    }

    override suspend fun restore(vod: SettingsBackup.Vod) {
        val config = VodPortalConfig.from(vod.url.orEmpty(), vod.key.orEmpty()) ?: return
        connectVodPortal(config)
        favoritesPort.replace(VodFavorites(vod.favorites.map { it.toDomain() }))
    }
}

/** Доменная избранная карточка в плоский вид файла настроек. */
internal fun VodFavorite.toBackup(): SettingsBackup.Favorite = SettingsBackup.Favorite(
    titleId = title.id.value,
    title = title.title,
    year = title.year,
    posterUrl = title.posterUrl,
    previewPosterUrl = title.previewPosterUrl,
    description = title.description,
    kind = title.kind.name,
    durationSeconds = title.durationSeconds,
    isFhd = title.isFhd,
    is4k = title.is4k,
    isHdr = title.isHdr,
    ageLimit = title.ageLimit,
    addedAtMillis = addedAtMillis,
)

/** Плоская карточка из файла в доменную; неизвестный вид считается одиночным потоком. */
internal fun SettingsBackup.Favorite.toDomain(): VodFavorite = VodFavorite(
    title = VodTitle(
        id = VodTitleId(titleId),
        title = title,
        year = year,
        posterUrl = posterUrl,
        previewPosterUrl = previewPosterUrl,
        description = description,
        kind = runCatching { VodTitleKind.valueOf(kind) }.getOrDefault(VodTitleKind.STREAM),
        durationSeconds = durationSeconds,
        isFhd = isFhd,
        is4k = is4k,
        isHdr = isHdr,
        ageLimit = ageLimit,
    ),
    addedAtMillis = addedAtMillis,
)
