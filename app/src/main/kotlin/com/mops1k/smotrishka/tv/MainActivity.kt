package com.mops1k.smotrishka.tv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.mops1k.smotrishka.tv.core.data.SourceRepository
import com.mops1k.smotrishka.tv.core.data.watchnext.WatchNextLink
import com.mops1k.smotrishka.tv.core.designsystem.theme.SmotrishkaTvTheme
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.ui.AppNavHost
import com.mops1k.smotrishka.tv.ui.Route
import com.mops1k.smotrishka.tv.ui.common.LocalToastController
import com.mops1k.smotrishka.tv.ui.common.rememberDeviceProfile
import com.mops1k.smotrishka.tv.core.designsystem.component.LocalReduceMotion
import com.mops1k.smotrishka.tv.ui.common.ToastController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
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

    /**
     * Канал из диплинка ленты «Сейчас идёт».
     *
     * Хранится до тех пор, пока навигация не откроет плеер: экран запускается асинхронно,
     * и обработать ссылку раньше нельзя. Лента может прислать ссылку и на уже открытое
     * приложение (singleTask), поэтому это поток, а не одноразовое значение из Intent'а.
     */
    private val pendingChannel = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingChannel.value = WatchNextLink.channelId(intent?.dataString)

        // Альбомная ориентация на всех устройствах, включая телефоны: вертикальная
        // раскладка не предусмотрена дизайном.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Приложение не должно уходить в сон и затемнять экран: это телевизор, а на слабом
        // канале пользователь может долго ждать картинку, не трогая пульт.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Системные панели скрыты для всего приложения, а не только в плеере: интерфейс
        // рассчитан на полный экран, статус-бар и панель навигации перекрывают контент.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            SmotrishkaTvTheme {
                // Один тост на всё приложение: он переживает переход между экранами.
                val toastController = remember { ToastController() }
                // Слабые приставки: анимации выключаем, интерфейс реагирует сразу.
                val device = rememberDeviceProfile()
                val reduceMotion = device.isLowRam
                CompositionLocalProvider(
                    LocalToastController provides toastController,
                    LocalReduceMotion provides reduceMotion,
                ) {
                    val startDestination by produceState<String?>(initialValue = null) {
                        value = if (sourceRepository.active() != null) Route.HOME else Route.ONBOARDING
                    }
                    val pendingChannelId by pendingChannel.collectAsState()

                    startDestination?.let { route ->
                        AppNavHost(
                            startDestination = route,
                            pendingChannelId = pendingChannelId?.let(::ChannelId),
                            onPendingChannelConsumed = { pendingChannel.value = null },
                        )
                    }
                }
            }
        }

        // Если прошлый запуск закончился ошибкой, говорим об этом прямо, а не молчим.
        CrashGuard.consumeLast(this)?.let { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Приложение уже открыто, а пользователь нажал карточку ленты: `singleTask` не создаёт
     * новую активность, и диплинк приходит сюда.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        WatchNextLink.channelId(intent.dataString)?.let { channelId ->
            pendingChannel.value = channelId
        }
    }
}
