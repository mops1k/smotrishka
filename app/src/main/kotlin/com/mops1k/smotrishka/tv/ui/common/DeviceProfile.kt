package com.mops1k.smotrishka.tv.ui.common

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Признаки устройства, от которых зависит поведение интерфейса.
 *
 * @param isTelevision Android TV или ТВ-приставка (`FEATURE_LEANBACK` либо мало памяти);
 * @param isLowRam малопамятное устройство.
 */
data class DeviceProfile(
    val isTelevision: Boolean,
    val isLowRam: Boolean,
)

/**
 * Читает признаки устройства один раз за композицию.
 *
 * Проверка вынесена из composable-кода экрана: решение «показывать ли превью» принимает
 * чистая [com.mops1k.smotrishka.tv.ui.home.PreviewPolicy], а здесь только факты о железе.
 */
@Composable
fun rememberDeviceProfile(): DeviceProfile {
    val context = LocalContext.current
    return remember(context) { readDeviceProfile(context) }
}

/**
 * Факты о железе: Leanback и признак малой памяти.
 *
 * @param context контекст приложения.
 */
fun readDeviceProfile(context: Context): DeviceProfile {
    val hasLeanback = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val isLowRam = runCatching {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.isLowRamDevice
    }.getOrDefault(false)
    return DeviceProfile(
        isTelevision = hasLeanback || isLowRam,
        isLowRam = isLowRam,
    )
}
