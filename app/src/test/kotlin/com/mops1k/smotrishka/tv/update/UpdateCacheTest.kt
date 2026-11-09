package com.mops1k.smotrishka.tv.update

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.update.SemverVersion
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * TDD-спецификация кэша скачанных APK: файл переиспользуется только для своей версии
 * и удаляется, когда обновление установлено.
 */
class UpdateCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val cache by lazy {
        // В тесте версией считаем содержимое файла: в приложении её читает InstalledApp
        // из манифеста APK, а здесь проверяется сама логика кэша.
        UpdateCache(tempFolder.newFolder("update")) { file -> file.takeIf { it.isFile }?.readText() }
    }

    @Test
    fun `скачанный файл своей версии используется повторно`() {
        val file = cache.file("0.1.0-rc.2").apply { writeText("0.1.0-rc.2") }

        assertThat(cache.cached("0.1.0-rc.2")).isEqualTo(file)
    }

    @Test
    fun `файл другой версии не подходит`() {
        cache.file("0.1.0-rc.2").writeText("0.1.0-rc.1")

        assertThat(cache.cached("0.1.0-rc.2")).isNull()
    }

    @Test
    fun `пустой файл не считается скачанным`() {
        cache.file("0.1.0-rc.2").writeText("")

        assertThat(cache.cached("0.1.0-rc.2")).isNull()
    }

    @Test
    fun `после установки кэш очищается`() {
        val first = cache.file("0.1.0-rc.2").apply { writeText("0.1.0-rc.2") }
        val second = cache.file("0.1.0-rc.1").apply { writeText("0.1.0-rc.1") }

        cache.clear()

        assertThat(first.exists()).isFalse()
        assertThat(second.exists()).isFalse()
    }

    @Test
    fun `очистка не падает на пустом кэше`() {
        cache.clear()

        assertThat(File(tempFolder.root, "update").listFiles().orEmpty()).isEmpty()
    }

    @Test
    fun `файл установленной версии удаляется при запуске`() {
        val installed = cache.file("0.1.0-rc.2").apply { writeText("0.1.0-rc.2") }

        cache.clearUpTo(SemverVersion.parse("0.1.0-rc.2")!!)

        assertThat(installed.exists()).isFalse()
    }

    @Test
    fun `файл более новой версии остаётся`() {
        val newer = cache.file("0.1.0-rc.3").apply { writeText("0.1.0-rc.3") }

        cache.clearUpTo(SemverVersion.parse("0.1.0-rc.2")!!)

        assertThat(newer.exists()).isTrue()
    }
}
