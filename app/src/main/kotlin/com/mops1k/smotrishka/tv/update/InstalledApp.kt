package com.mops1k.smotrishka.tv.update

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.mops1k.smotrishka.tv.core.data.update.SemverVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Сведения об установленной сборке приложения: версия, тип и подпись.
 *
 * @param context контекст приложения.
 */
@Singleton
class InstalledApp @Inject constructor(@ApplicationContext private val context: Context) {

    /** `versionName` установленной сборки либо пустая строка, если система его не отдала. */
    val versionName: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

    /** `versionCode` установленной сборки. */
    val versionCode: Long
        get() = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }.getOrDefault(0L)

    /** Отладочная ли сборка: от этого зависит, какой APK предлагать. */
    val isDebugBuild: Boolean
        get() = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    /** Строка для окна About: «Версия 0.1.0 (сборка 1)». */
    val versionLabel: String
        get() = "Версия $versionName (сборка $versionCode)"

    /** Версия в виде semver; если `versionName` не разбирается, считаем её `0.0.0`. */
    fun version(): SemverVersion =
        SemverVersion.parse(versionName) ?: SemverVersion(0, 0, 0)

    /**
     * Похож ли скачанный файл на APK этого приложения.
     *
     * Проверка ловит битую загрузку (например, страницу ошибки вместо файла): предлагать
     * переустановку в этом случае бессмысленно.
     *
     * @param apk скачанный файл.
     */
    fun isOwnApk(apk: File): Boolean {
        val archive = packageInfo(apk) ?: return false
        return archive.packageName == context.packageName
    }

    /**
     * Версия APK-файла, если он принадлежит этому приложению.
     *
     * @param apk файл APK;
     * @return `versionName` из архива либо `null`, если файл не APK или чужой пакет.
     */
    fun apkVersion(apk: File): String? {
        val archive = packageInfo(apk) ?: return null
        return archive.versionName.takeIf { archive.packageName == context.packageName }
    }

    /**
     * Совпадает ли подпись скачанного APK с подписью установленного приложения.
     *
     * @param apk скачанный файл;
     * @return `true`, если установка поверх возможна.
     */
    fun hasSameSignature(apk: File): Boolean {
        val archive = packageInfo(apk) ?: return false
        val installed = packageInfo(null) ?: return false

        @Suppress("DEPRECATION")
        val archiveSignatures = archive.signatures.orEmpty().map { it.toByteArray().sha256() }.toSet()

        @Suppress("DEPRECATION")
        val installedSignatures = installed.signatures.orEmpty().map { it.toByteArray().sha256() }.toSet()

        return archiveSignatures.isNotEmpty() && archiveSignatures == installedSignatures
    }

    private fun packageInfo(apk: File?): PackageInfo? = runCatching {
        @Suppress("DEPRECATION")
        val flags = PackageManager.GET_SIGNATURES
        if (apk == null) {
            context.packageManager.getPackageInfo(context.packageName, flags)
        } else {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        }
    }.getOrNull()

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { byte ->
            "%02x".format(byte)
        }
}
