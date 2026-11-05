package com.mops1k.smotrishka.tv.ui.player

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Общий HTTP-клиент для потоков.
 *
 * Один клиент и один пул соединений на всё приложение: при каждом открытии канала не создаются
 * новые пулы и не копятся «висящие» соединения — на приставках с малым ОЗУ это заметно.
 */
object PlayerHttp {

    /** Клиент для загрузки HLS-сегментов. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = MAX_IDLE_CONNECTIONS,
                    keepAliveDuration = KEEP_ALIVE_SECONDS,
                    timeUnit = TimeUnit.SECONDS,
                ),
            )
            .build()
    }

    private const val MAX_IDLE_CONNECTIONS = 4
    private const val KEEP_ALIVE_SECONDS = 30L
}
