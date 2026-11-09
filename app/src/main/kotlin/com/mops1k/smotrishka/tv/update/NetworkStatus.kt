package com.mops1k.smotrishka.tv.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Состояние сети для проверки обновлений: есть ли интернет и не мобильный ли он.
 *
 * @param context контекст приложения.
 */
@Singleton
class NetworkStatus @Inject constructor(@ApplicationContext private val context: Context) {

    /** Есть ли сеть с проверенным доступом в интернет. */
    fun isOnline(): Boolean {
        val capabilities = capabilities() ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Немимитированная сеть: Wi-Fi, проводной Ethernet, VPN поверх них.
     *
     * @return `true`, если сеть не тарифицируется как мобильная (или её нет — тогда
     * проверять обновления тоже нечего).
     */
    fun isUnmetered(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = capabilities() ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !manager.isActiveNetworkMetered
    }

    private fun capabilities(): NetworkCapabilities? = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val network = manager.activeNetwork ?: return null
        manager.getNetworkCapabilities(network)
    }.getOrNull()
}
