package com.mops1k.smotrishka.tv.update

import com.mops1k.smotrishka.tv.core.data.update.ReleaseAsset
import com.mops1k.smotrishka.tv.core.data.update.SmotrishkaRelease
import java.io.File

/**
 * Состояние обновления приложения — одно на весь процесс.
 */
sealed interface UpdateState {

    /** Проверка ещё не запускалась или её результат не важен. */
    data object Idle : UpdateState

    /** Идёт запрос к GitHub. */
    data object Checking : UpdateState

    /**
     * Установлена самая свежая версия.
     *
     * @param checkedAtMillis когда проверяли.
     */
    data class UpToDate(val checkedAtMillis: Long?) : UpdateState

    /**
     * Найдена версия новее установленной.
     *
     * @param release релиз;
     * @param apk подходящий APK;
     * @param dismissed про эту версию попросили больше не сообщать;
     * @param cachedApk уже скачанный ранее файл этой версии, если он есть.
     */
    data class Available(
        val release: SmotrishkaRelease,
        val apk: ReleaseAsset,
        val dismissed: Boolean = false,
        val cachedApk: File? = null,
    ) : UpdateState

    /**
     * APK скачивается.
     *
     * @param percent сколько процентов уже получено.
     */
    data class Downloading(val percent: Int) : UpdateState

    /**
     * Скачанный APK подписан другим ключом: установить поверх нельзя.
     *
     * @param version версия файла;
     * @param apkFile скачанный файл в кэше приложения.
     */
    data class NeedsReinstall(val version: String, val apkFile: File) : UpdateState

    /** Системный установщик запущен, ждём решения пользователя. */
    data object Installing : UpdateState

    /** Установка запрещена в настройках «неизвестные источники». */
    data object NeedsPermission : UpdateState

    /**
     * Проверка или установка не удалась.
     *
     * @param reason причина для пользователя.
     */
    data class Failed(val reason: String) : UpdateState
}
