package com.mops1k.smotrishka.tv.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Установка скачанного APK через системный установщик (`PackageInstaller`).
 *
 * Все сбои (нет разрешения, битый файл, отказ системы) возвращаются [InstallResult],
 * исключений наружу нет: обновление не должно ронять приложение.
 *
 * @param context контекст приложения.
 */
@Singleton
class ApkInstaller @Inject constructor(@ApplicationContext private val context: Context) {

    /** Разрешена ли установка приложений из этого приложения. */
    fun canInstall(): Boolean = runCatching {
        context.packageManager.canRequestPackageInstalls()
    }.getOrDefault(false)

    /** Открывает системный экран «Установка неизвестных приложений» для этого приложения. */
    fun openInstallPermission() {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { error ->
            Log.e(TAG, "open unknown sources settings failed", error)
        }
    }

    /**
     * Запускает установку APK.
     *
     * @param apk скачанный файл;
     * @return итог запуска установщика.
     */
    suspend fun install(apk: File): InstallResult = withContext(Dispatchers.IO) {
        if (!canInstall()) return@withContext InstallResult.NeedsPermission

        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL,
            ).apply { setAppPackageName(context.packageName) }

            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite(APK_NAME, 0, apk.length()).use { output ->
                    apk.inputStream().use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                session.commit(statusIntent().intentSender)
            }
            InstallResult.Started
        }.getOrElse { error ->
            Log.e(TAG, "install failed", error)
            InstallResult.Failed(error.message ?: "Не удалось запустить установщик")
        }
    }

    private fun statusIntent(): PendingIntent {
        val intent = Intent(context, InstallStatusReceiver::class.java)
            .setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private companion object {
        const val TAG = "SmotrishkaUpdate"
        const val APK_NAME = "smotrishka.apk"
    }
}
