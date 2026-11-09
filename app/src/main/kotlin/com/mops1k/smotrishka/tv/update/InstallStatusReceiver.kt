package com.mops1k.smotrishka.tv.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import android.widget.Toast

/**
 * Результат установки от системного установщика.
 *
 * Когда система требует подтверждения пользователя, она присылает
 * [PackageInstaller.STATUS_PENDING_USER_ACTION] и окно установки в `EXTRA_INTENT`:
 * запустить его обязано само приложение, иначе диалог не появится вообще.
 * Пользователь видит диалог установки сам, поэтому здесь только журнал и короткое
 * сообщение о неудаче: молчание после «Отмены» выглядело бы как зависание.
 */
class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "update installed")
                // Скачанный APK больше не нужен: иначе кэш будет расти с каждой версией.
                clearUpdateCache(context)
                return
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = confirmIntent(intent)
                if (confirm == null) {
                    Log.e(TAG, "pending user action без окна установки")
                    toast(context, "Не удалось открыть установщик")
                    return
                }
                startInstaller(context, confirm)
                return
            }
        }

        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        Log.e(TAG, "update not installed: status=$status, message=$message")
        toast(context, "Обновление не установлено")
    }

    /** Окно подтверждения установки из широковещательного интента. */
    private fun confirmIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private fun startInstaller(context: Context, intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { error ->
            Log.e(TAG, "installer activity start failed", error)
            toast(context, "Не удалось открыть установщик")
        }
    }

    private fun toast(context: Context, text: String) {
        runCatching { Toast.makeText(context, text, Toast.LENGTH_LONG).show() }
    }

    private companion object {
        const val TAG = "SmotrishkaUpdate"
    }
}
