package com.mops1k.smotrishka.tv

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.mops1k.smotrishka.tv.core.data.SourceRepository
import com.mops1k.smotrishka.tv.core.designsystem.theme.SmotrishkaTvTheme
import com.mops1k.smotrishka.tv.ui.AppNavHost
import com.mops1k.smotrishka.tv.ui.Route
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Единственная активность приложения: хост навигации Compose.
 *
 * Приложение живёт только в альбомной ориентации: интерфейс рассчитан на широкий экран
 * (рейл категорий, список каналов, превью справа). Стартовый экран зависит от того,
 * настроен ли источник: без источника показываем онбординг, с источником — сразу каналы.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sourceRepository: SourceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Альбомная ориентация на всех устройствах, включая телефоны: вертикальная
        // раскладка не предусмотрена дизайном.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Приложение не должно уходить в сон и затемнять экран: это телевизор, а на слабом
        // канале пользователь может долго ждать картинку, не трогая пульт.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            SmotrishkaTvTheme {
                val startDestination by produceState<String?>(initialValue = null) {
                    value = if (sourceRepository.active() != null) Route.HOME else Route.ONBOARDING
                }

                startDestination?.let { route -> AppNavHost(startDestination = route) }
            }
        }

        // Если прошлый запуск закончился ошибкой, говорим об этом прямо, а не молчим.
        CrashGuard.consumeLast(this)?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
