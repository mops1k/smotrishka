package com.mops1k.smotrishka.tv.update

import android.content.Context
import com.mops1k.smotrishka.tv.core.data.update.SemverVersion
import java.io.File

/**
 * Кэш скачанных APK обновлений.
 *
 * Если версия уже скачана, повторная загрузка не нужна: пользователь мог случайно
 * закрыть системный установщик, и ему достаточно нажать «Установить» ещё раз.
 *
 * @param dir каталог кэша;
 * @param versionOf версия APK-файла, `null` если файл не является APK этого приложения.
 */
class UpdateCache(
    private val dir: File,
    private val versionOf: (File) -> String?,
) {

    /**
     * Файл, в который скачивается APK указанной версии.
     *
     * @param version версия релиза.
     */
    fun file(version: String): File {
        dir.mkdirs()
        return File(dir, "smotrishka-$version.apk")
    }

    /**
     * Ранее скачанный APK нужной версии.
     *
     * @param version ожидаемая версия;
     * @return файл либо `null`, если его нет, он пуст или относится к другой версии.
     */
    fun cached(version: String): File? = file(version).takeIf { apk ->
        apk.isFile && apk.length() > 0 && versionOf(apk) == version
    }

    /** Удаляет все скачанные APK: после установки они больше не нужны. */
    fun clear() {
        dir.listFiles()?.forEach { file -> file.delete() }
    }

    /**
     * Удаляет APK версий не новее установленной.
     *
     * @param current версия установленной сборки.
     */
    fun clearUpTo(current: SemverVersion) {
        dir.listFiles()?.forEach { apk ->
            val version = versionOf(apk)?.let(SemverVersion::parse) ?: return@forEach
            if (version <= current) apk.delete()
        }
    }

    companion object {

        /**
         * Каталог кэша обновлений приложения.
         *
         * @param context контекст приложения.
         */
        fun directory(context: Context): File = File(context.cacheDir, "update")
    }
}

/**
 * Удаляет скачанные APK после успешной установки, чтобы кэш не заполнял диск.
 *
 * @param context контекст приложения.
 */
fun clearUpdateCache(context: Context) {
    runCatching { UpdateCache(UpdateCache.directory(context)) { null }.clear() }
}
