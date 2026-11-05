package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import com.mops1k.smotrishka.tv.core.model.SourceKind

/**
 * Хранит активный источник и его секрет.
 *
 * В базе лежит только описание источника (тип, адрес, логин), а код/пароль —
 * в зашифрованном хранилище под ключом этого источника.
 *
 * @param catalogStore каталог и список источников;
 * @param secretStore зашифрованные секреты.
 */
class SourceRepository(
    private val catalogStore: CatalogStore,
    private val secretStore: SecretStore,
) {

    /**
     * Сохраняет источник и его секрет (если есть).
     *
     * @param source настроенный источник;
     * @param displayName имя для экрана настроек.
     */
    suspend fun save(source: PlaylistSource, displayName: String) {
        val entity = when (source) {
            is PlaylistSource.M3uUrl -> {
                secretStore.remove(SECRET_KEY)
                SourceEntity(
                    id = SOURCE_ID,
                    kind = SourceKind.M3U_URL.name,
                    displayName = displayName,
                    url = source.url,
                    server = null,
                    username = null,
                    createdAt = clock(),
                )
            }

            is PlaylistSource.IedemCode -> {
                secretStore.put(SECRET_KEY, source.code)
                SourceEntity(
                    id = SOURCE_ID,
                    kind = SourceKind.IEDEM_CODE.name,
                    displayName = displayName,
                    url = null,
                    server = null,
                    username = null,
                    createdAt = clock(),
                )
            }

            is PlaylistSource.Xtream -> {
                secretStore.put(SECRET_KEY, source.password)
                SourceEntity(
                    id = SOURCE_ID,
                    kind = SourceKind.XTREAM.name,
                    displayName = displayName,
                    url = null,
                    server = source.server,
                    username = source.username,
                    createdAt = clock(),
                )
            }

            is PlaylistSource.File -> {
                secretStore.remove(SECRET_KEY)
                SourceEntity(
                    id = SOURCE_ID,
                    kind = SourceKind.FILE.name,
                    displayName = displayName,
                    url = source.uri,
                    server = null,
                    username = null,
                    createdAt = clock(),
                )
            }
        }

        catalogStore.saveSource(entity)
    }

    /** Активный источник либо `null`, если он не настроен или секрет повреждён. */
    suspend fun active(): PlaylistSource? {
        val entity = catalogStore.sources().firstOrNull { it.id == SOURCE_ID } ?: return null

        return when (entity.kind) {
            SourceKind.M3U_URL.name -> entity.url?.let { PlaylistSource.M3uUrl(url = it, name = entity.displayName) }

            SourceKind.IEDEM_CODE.name -> secretStore.get(SECRET_KEY)?.let { PlaylistSource.IedemCode(code = it) }

            SourceKind.XTREAM.name -> {
                val server = entity.server ?: return null
                val username = entity.username ?: return null
                val password = secretStore.get(SECRET_KEY) ?: return null
                PlaylistSource.Xtream(server = server, username = username, password = password)
            }

            SourceKind.FILE.name -> entity.url?.let { PlaylistSource.File(uri = it, name = entity.displayName) }

            else -> null
        }
    }

    private fun clock(): Long = System.currentTimeMillis()

    private companion object {
        const val SOURCE_ID = "active"
        const val SECRET_KEY = "source:active:secret"
    }
}
