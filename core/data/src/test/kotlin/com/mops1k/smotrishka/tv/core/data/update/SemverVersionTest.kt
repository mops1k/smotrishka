package com.mops1k.smotrishka.tv.core.data.update

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация разбора и сравнения версий.
 */
class SemverVersionTest {

    @Test
    fun `тег с префиксом v разбирается`() {
        assertThat(SemverVersion.parse("v0.2.0")).isEqualTo(SemverVersion(0, 2, 0))
    }

    @Test
    fun `пререлиз остаётся в суффиксе`() {
        assertThat(SemverVersion.parse("0.2.0-rc.1")?.prerelease).isEqualTo("rc.1")
    }

    @Test
    fun `нечисловой тег не разбирается`() {
        assertThat(SemverVersion.parse("nightly")).isNull()
    }

    @Test
    fun `неполная версия не разбирается`() {
        assertThat(SemverVersion.parse("0.2")).isNull()
    }

    @Test
    fun `сравнение идёт по числам`() {
        assertThat(SemverVersion.parse("0.2.0")).isGreaterThan(SemverVersion.parse("0.1.9"))
        assertThat(SemverVersion.parse("0.2.10")).isGreaterThan(SemverVersion.parse("0.2.9"))
    }

    @Test
    fun `стабильная версия старше своего пререлиза`() {
        assertThat(SemverVersion.parse("0.2.0")).isGreaterThan(SemverVersion.parse("0.2.0-rc.1"))
    }

    @Test
    fun `пререлизы сравниваются суффиксом`() {
        assertThat(SemverVersion.parse("0.2.0-rc.2")).isGreaterThan(SemverVersion.parse("0.2.0-rc.1"))
    }

    @Test
    fun `одинаковые версии равны`() {
        assertThat(SemverVersion.parse("v1.2.3")).isEquivalentAccordingToCompareTo(
            SemverVersion.parse("1.2.3"),
        )
    }

    @Test
    fun `строка версии печатается без префикса v`() {
        assertThat(SemverVersion.parse("v1.2.3-rc.1").toString()).isEqualTo("1.2.3-rc.1")
    }
}
