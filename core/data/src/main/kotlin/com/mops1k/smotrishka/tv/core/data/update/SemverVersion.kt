package com.mops1k.smotrishka.tv.core.data.update

/**
 * Версия приложения в формате semver: `0.2.0`, `0.2.0-rc.1`.
 *
 * Тег релиза приходит с префиксом `v`, разбор его отбрасывает.
 *
 * @param major старшая часть;
 * @param minor средняя часть;
 * @param patch младшая часть;
 * @param prerelease суффикс пререлиза (`rc.1`), `null` у стабильной версии.
 */
data class SemverVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null,
) : Comparable<SemverVersion> {

    /**
     * Сравнивает версии: сначала числа, затем стабильная версия старше пререлиза
     * той же тройки чисел (правило semver).
     */
    override fun compareTo(other: SemverVersion): Int {
        val byNumber = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (byNumber != 0) return byNumber

        return when {
            prerelease == null && other.prerelease == null -> 0
            prerelease == null -> 1
            other.prerelease == null -> -1
            else -> prerelease.compareTo(other.prerelease)
        }
    }

    /** Строка без префикса `v`: так версия показывается пользователю. */
    override fun toString(): String = buildString {
        append(major).append('.').append(minor).append('.').append(patch)
        if (prerelease != null) append('-').append(prerelease)
    }

    companion object {

        private val PATTERN = Regex(
            """^[vV]?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""",
        )

        /**
         * Разбирает строку версии.
         *
         * @param raw тег релиза или `versionName` сборки.
         * @return версия либо `null`, если строка не похожа на semver.
         */
        fun parse(raw: String): SemverVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            return SemverVersion(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
                prerelease = match.groupValues[4].ifBlank { null },
            )
        }
    }
}
