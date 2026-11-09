package com.mops1k.smotrishka.tv.core.data.update

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mops1k.smotrishka.tv.core.data.HttpTextClient
import com.mops1k.smotrishka.tv.core.data.HttpTextResult

/**
 * Список релизов приложения из GitHub API.
 *
 * Любая неприятность (нет сети, лимит запросов, неразборчивый JSON) возвращается
 * значением [ReleasesResult.Failure], а не исключением.
 *
 * @param http клиент текстовых ответов;
 * @param repository репозиторий вида `owner/name`;
 * @param baseUrl адрес API: подменяется в тестах.
 */
class GithubReleasesClient(
    private val http: HttpTextClient,
    private val repository: String = DEFAULT_REPOSITORY,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    private val gson = Gson()

    /**
     * Запрашивает релизы.
     *
     * @param includePrereleases учитывать пререлизы (`rc`);
     * @return разобранные релизы, свежие первыми, либо причина отказа.
     */
    suspend fun fetch(includePrereleases: Boolean): ReleasesResult {
        val url = "$baseUrl/repos/$repository/releases?per_page=$PAGE_SIZE"
        val response = http.fetch(url, headers = mapOf(ACCEPT_HEADER to ACCEPT_VALUE))

        val text = when (response) {
            is HttpTextResult.Success -> response.text

            is HttpTextResult.HttpError -> return ReleasesResult.Failure(
                "GitHub ответил кодом ${response.code}",
            )

            is HttpTextResult.NetworkError -> return ReleasesResult.Failure(
                "Нет связи с GitHub: ${response.reason}",
            )

            HttpTextResult.EmptyBody -> return ReleasesResult.Failure("GitHub вернул пустой ответ")
        }

        val releases = runCatching { parse(text, includePrereleases) }.getOrNull()
            ?: return ReleasesResult.Failure("Не удалось разобрать ответ GitHub")

        return ReleasesResult.Success(releases)
    }

    private fun parse(text: String, includePrereleases: Boolean): List<SmotrishkaRelease> {
        val json = gson.fromJson(text, Array<ReleaseJson>::class.java).orEmpty()
        return json
            .filter { release -> !release.draft && (includePrereleases || !release.prerelease) }
            .mapNotNull { release -> release.toRelease() }
            .sortedByDescending { release -> release.version }
    }

    /**
     * Релиз из ответа API.
     *
     * @return релиз либо `null`, если тег не похож на версию.
     */
    private fun ReleaseJson.toRelease(): SmotrishkaRelease? {
        val version = SemverVersion.parse(tagName) ?: return null
        val files = assets.orEmpty().mapNotNull { asset ->
            val name = asset.name ?: return@mapNotNull null
            val url = asset.downloadUrl ?: return@mapNotNull null
            ReleaseAsset(name = name, downloadUrl = url, sizeBytes = asset.size ?: 0L)
        }
        return SmotrishkaRelease(version = version, tag = tagName, assets = files)
    }

    private data class ReleaseJson(
        @SerializedName("tag_name") val tagName: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<AssetJson>? = null,
    )

    private data class AssetJson(
        val name: String? = null,
        @SerializedName("browser_download_url") val downloadUrl: String? = null,
        val size: Long? = null,
    )

    private companion object {
        const val DEFAULT_REPOSITORY = "mops1k/smotrishka"
        const val DEFAULT_BASE_URL = "https://api.github.com"
        const val PAGE_SIZE = 20
        const val ACCEPT_HEADER = "Accept"
        const val ACCEPT_VALUE = "application/vnd.github+json"
    }
}
