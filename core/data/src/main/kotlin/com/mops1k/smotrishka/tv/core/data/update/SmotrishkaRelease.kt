package com.mops1k.smotrishka.tv.core.data.update

/**
 * Опубликованный релиз приложения.
 *
 * @param version версия из тега;
 * @param tag тег как он есть на GitHub (`v0.2.0`);
 * @param assets приложенные файлы.
 */
data class SmotrishkaRelease(
    val version: SemverVersion,
    val tag: String,
    val assets: List<ReleaseAsset>,
) {

    /**
     * Подходящий APK: отладочная сборка ставит себе `.debug.apk`, релизная — `.release.apk`.
     *
     * @param debugBuild установлена отладочная сборка.
     * @return ассет либо `null`, если в релизе нет файла нужного типа.
     */
    fun apk(debugBuild: Boolean): ReleaseAsset? {
        val suffix = if (debugBuild) DEBUG_SUFFIX else RELEASE_SUFFIX
        return assets.firstOrNull { it.name.endsWith(suffix) }
    }

    private companion object {
        const val DEBUG_SUFFIX = ".debug.apk"
        const val RELEASE_SUFFIX = ".release.apk"
    }
}
