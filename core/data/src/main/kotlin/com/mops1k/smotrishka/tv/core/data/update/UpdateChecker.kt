package com.mops1k.smotrishka.tv.core.data.update

/**
 * Сравнивает установленную версию с опубликованными релизами.
 *
 * @param client источник релизов.
 */
class UpdateChecker(private val client: GithubReleasesClient) {

    /**
     * Проверяет, есть ли версия новее установленной.
     *
     * @param current версия установленной сборки;
     * @param includePrereleases учитывать пререлизы;
     * @param debugBuild установлена отладочная сборка (ей нужен `.debug.apk`).
     */
    suspend fun check(
        current: SemverVersion,
        includePrereleases: Boolean,
        debugBuild: Boolean,
    ): UpdateCheckResult = when (val result = client.fetch(includePrereleases)) {
        is ReleasesResult.Failure -> UpdateCheckResult.Failure(result.reason)

        is ReleasesResult.Success -> {
            val newer = result.releases.firstOrNull { release -> release.version > current }
                ?: return UpdateCheckResult.UpToDate(current)

            val apk = newer.apk(debugBuild)
                ?: return UpdateCheckResult.Failure(
                    "В релизе ${newer.version} нет APK для этой сборки",
                )

            UpdateCheckResult.Available(release = newer, apk = apk)
        }
    }
}
