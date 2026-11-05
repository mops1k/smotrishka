package com.mops1k.smotrishka.tv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TDD-спецификация защиты от падений: текст ошибки понятен пользователю и переживает перезапуск.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashGuardTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `текст ошибки содержит сообщение исключения`() {
        val text = CrashGuard.describe(IllegalStateException("поток оборвался"))

        assertThat(text).contains("поток оборвался")
        assertThat(text).contains("перезапущено")
    }

    @Test
    fun `исключение без сообщения даёт понятный текст`() {
        val text = CrashGuard.describe(IllegalArgumentException())

        assertThat(text).contains("IllegalArgumentException")
    }

    @Test
    fun `сохранённый текст забирается один раз`() {
        val file = java.io.File(context.filesDir, "last_crash.txt").apply { writeText("ошибка") }

        assertThat(file.exists()).isTrue()
        assertThat(CrashGuard.consumeLast(context)).isEqualTo("ошибка")
        assertThat(CrashGuard.consumeLast(context)).isNull()
    }

    @Test
    fun `отчёт о падении содержит стектрейс, устройство и память`() {
        val error = java.net.SocketTimeoutException("timeout").apply {
            stackTrace = arrayOf(StackTraceElement("LiveBufferLoader", "fetchText", "LiveBufferLoader.kt", 135))
        }
        val environment = CrashGuard.Environment(
            device = "realme RMX5090",
            sdk = 36,
            usedMemoryMb = 741,
            maxMemoryMb = 512,
        )

        val text = CrashGuard.report(error, environment, timeMillis = 1_791_000_000_000L)

        assertThat(text).contains("SocketTimeoutException")
        assertThat(text).contains("timeout")
        assertThat(text).contains("LiveBufferLoader.kt:135")
        assertThat(text).contains("realme RMX5090")
        assertThat(text).contains("741")
        assertThat(text).contains("1_791".replace("_", ""))
    }

    @Test
    fun `отчёт сохраняется в файл и не мешает короткому сообщению`() {
        CrashGuard.saveReport(context, "подробный отчёт", timeMillis = 1_000L)

        val report = java.io.File(context.filesDir, "crash-1000.txt")
        assertThat(report.readText()).isEqualTo("подробный отчёт")

        java.io.File(context.filesDir, "last_crash.txt").writeText("короткое сообщение")
        assertThat(CrashGuard.consumeLast(context)).isEqualTo("короткое сообщение")
        assertThat(report.exists()).isTrue()
    }

    @Test
    fun `старые отчёты удаляются, остаются три последних`() {
        (1..5).forEach { index ->
            CrashGuard.saveReport(context, "отчёт $index", timeMillis = index * 1_000L)
        }

        val reports = context.filesDir.list().orEmpty().filter { it.startsWith("crash-") }.sorted()

        assertThat(reports).containsExactly("crash-3000.txt", "crash-4000.txt", "crash-5000.txt")
    }
}
