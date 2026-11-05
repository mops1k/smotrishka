package com.mops1k.smotrishka.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Защита от внезапных падений.
 *
 * Непойманное исключение не должно закрывать приложение молча: текст ошибки сохраняется,
 * приложение перезапускает главный экран, а при следующем старте показывает, что случилось.
 * Кроме короткого текста для пользователя сохраняется подробный отчёт (стектрейс, устройство,
 * память): падение 04.11.2026 разбиралось только по crash-буферу logcat, потому что в файле
 * оставалась одна строка без стектрейса.
 *
 * Это страховка на случай программной ошибки, а не замена обработке ошибок в коде.
 */
object CrashGuard {

    private const val FILE_NAME = "last_crash.txt"

    private const val REPORT_PREFIX = "crash-"
    private const val REPORT_SUFFIX = ".txt"

    /** Сколько подробных отчётов хранить: старые удаляются. */
    private const val MAX_REPORTS = 3

    /**
     * Окружение, в котором произошло падение.
     *
     * @param device модель устройства;
     * @param sdk версия Android (SDK);
     * @param usedMemoryMb занятая приложением память, МБ;
     * @param maxMemoryMb предел памяти приложения, МБ.
     */
    data class Environment(
        val device: String,
        val sdk: Int,
        val usedMemoryMb: Long,
        val maxMemoryMb: Long,
    )

    /**
     * Ставит перехватчик непойманных исключений.
     *
     * @param application контекст приложения.
     */
    fun install(application: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                save(application, describe(error))
                val now = System.currentTimeMillis()
                saveReport(application, report(error, environment(), now), now)
            } catch (e: Exception) {
                // Сохранить текст не удалось — всё равно перезапускаем приложение.
            } finally {
                restart(application)
                previous?.uncaughtException(thread, error)
            }
        }
    }

    /**
     * Текст ошибки для пользователя.
     *
     * @param error непойманное исключение.
     */
    fun describe(error: Throwable): String {
        val message = error.message?.takeIf(String::isNotBlank) ?: error::class.java.simpleName
        return "Приложение перезапущено после ошибки: $message"
    }

    /**
     * Подробный отчёт о падении: стектрейс и состояние устройства.
     *
     * @param error непойманное исключение;
     * @param environment окружение на момент падения;
     * @param timeMillis момент падения;
     * @return текст отчёта.
     */
    fun report(error: Throwable, environment: Environment, timeMillis: Long): String = buildString {
        appendLine("Падение приложения")
        appendLine("время: ${Instant.ofEpochMilli(timeMillis)} ($timeMillis)")
        appendLine("устройство: ${environment.device}, Android (SDK ${environment.sdk})")
        appendLine("память: ${environment.usedMemoryMb} из ${environment.maxMemoryMb} МБ")
        appendLine("исключение:")
        val text = StringWriter()
        error.printStackTrace(PrintWriter(text))
        append(text.toString())
    }

    /**
     * Сохраняет подробный отчёт и вычищает старые.
     *
     * @param context контекст приложения;
     * @param text текст отчёта;
     * @param timeMillis момент падения.
     */
    fun saveReport(context: Context, text: String, timeMillis: Long) {
        runCatching {
            File(context.filesDir, "$REPORT_PREFIX$timeMillis$REPORT_SUFFIX").writeText(text)
            pruneReports(context, MAX_REPORTS)
        }
    }

    /**
     * Оставляет самые новые отчёты.
     *
     * @param context контекст приложения;
     * @param keep сколько файлов оставить.
     */
    fun pruneReports(context: Context, keep: Int) {
        val reports = context.filesDir.listFiles { file ->
            file.name.startsWith(REPORT_PREFIX) && file.name.endsWith(REPORT_SUFFIX)
        }?.sortedBy { file ->
            file.name.removePrefix(REPORT_PREFIX).removeSuffix(REPORT_SUFFIX).toLongOrNull() ?: 0L
        }.orEmpty()

        reports.dropLast(keep).forEach { it.delete() }
    }

    /**
     * Забирает сохранённый текст прошлого падения и удаляет его.
     *
     * @param context контекст приложения.
     */
    fun consumeLast(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null

        return try {
            file.readText().takeIf(String::isNotBlank)
        } catch (e: IOException) {
            null
        } finally {
            file.delete()
        }
    }

    private fun environment(): Environment {
        val runtime = Runtime.getRuntime()
        val perMb = MB
        return Environment(
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            sdk = Build.VERSION.SDK_INT,
            usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / perMb,
            maxMemoryMb = runtime.maxMemory() / perMb,
        )
    }

    private fun save(context: Context, text: String) {
        File(context.filesDir, FILE_NAME).writeText(text)
    }

    private fun restart(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        context.startActivity(intent)
        (context as? Activity)?.finish()
        Process.killProcess(Process.myPid())
    }

    private const val MB = 1024L * 1024
}
