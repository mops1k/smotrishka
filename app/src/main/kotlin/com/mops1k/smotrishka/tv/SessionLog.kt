package com.mops1k.smotrishka.tv

import android.content.Context
import java.io.File

/**
 * Короткий журнал долгого сеанса.
 *
 * Пятичасовой просмотр нельзя воспроизвести по заказу, поэтому состояние плеера и памяти
 * пишется раз в минуту в маленький файл. После обычного сеанса видно, растёт ли память,
 * не отстаёт ли буфер и сколько сегментов держит локальный сервер.
 *
 * Файл ограничен [MAX_LINES]: старые строки уходят, журнал не растёт бесконечно.
 */
object SessionLog {

    /** Сколько строк хранить. */
    const val MAX_LINES = 300

    private const val FILE_NAME = "session.log"

    /**
     * Дописывает строку в журнал.
     *
     * @param context контекст приложения;
     * @param line текст строки.
     */
    fun append(context: Context, line: String) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            val lines = file.takeIf(File::exists)?.readLines().orEmpty() + line
            file.writeText(lines.takeLast(MAX_LINES).joinToString("\n") + "\n")
        }
    }

    /**
     * Читает журнал.
     *
     * @param context контекст приложения;
     * @return строки журнала в порядке записи; пустой список, если журнала нет.
     */
    fun read(context: Context): List<String> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return runCatching { file.readLines().filter(String::isNotBlank) }.getOrDefault(emptyList())
    }
}
