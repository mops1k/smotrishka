package com.mops1k.smotrishka.tv.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * TDD-спецификация сведений об установленной сборке: строка версии собирается из
 * `PackageManager`, а сравнение подписи на неподходящем файле не бросает исключений.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalledAppTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val installedApp = InstalledApp(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun `строка версии содержит имя версии и номер сборки`() {
        assertThat(installedApp.versionLabel).contains("сборка")
        assertThat(installedApp.versionLabel).startsWith("Версия")
    }

    @Test
    fun `версия всегда разбирается`() {
        val version = installedApp.version()

        assertThat(version.major).isAtLeast(0)
        assertThat(version.toString()).isNotEmpty()
    }

    @Test
    fun `неподходящий файл не считается совпадающей подписью`() {
        val broken = File(tempFolder.root, "broken.apk").apply { writeBytes(ByteArray(64)) }

        assertThat(installedApp.hasSameSignature(broken)).isFalse()
    }

    @Test
    fun `битый файл не считается APK приложения`() {
        val broken = File(tempFolder.root, "broken.apk").apply { writeBytes(ByteArray(64)) }

        assertThat(installedApp.isOwnApk(broken)).isFalse()
    }

    @Test
    fun `страница ошибки вместо APK не считается APK приложения`() {
        val page = File(tempFolder.root, "error.html").apply {
            writeText("<html><body>404 Not Found</body></html>")
        }

        assertThat(installedApp.isOwnApk(page)).isFalse()
    }
}
