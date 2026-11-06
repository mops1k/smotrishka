package com.mops1k.smotrishka.tv.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mops1k.smotrishka.tv.core.designsystem.component.TvToast
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.ui.common.LocalToastController
import com.mops1k.smotrishka.tv.ui.home.HomeScreen
import com.mops1k.smotrishka.tv.ui.onboarding.OnboardingScreen
import com.mops1k.smotrishka.tv.ui.player.PlayerScreen
import com.mops1k.smotrishka.tv.ui.player.PlayerViewModel
import com.mops1k.smotrishka.tv.ui.programme.ProgrammeScreen
import com.mops1k.smotrishka.tv.ui.programme.ProgrammeViewModel
import com.mops1k.smotrishka.tv.ui.settings.SettingsScreen

/** Маршруты приложения. */
object Route {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"

    /** Плеер канала: `player/<id канала>`, с архивом — `?start=<секунды эпохи>`. */
    const val PLAYER = "player/{channelId}?start={start}"

    /** Программа канала: `programme/<id канала>`. */
    const val PROGRAMME = "programme/{channelId}"

    /**
     * Собирает маршрут плеера для канала.
     *
     * Идентификатор кодируется: у каналов без `tvg-id` он содержит URL, а слэши и двоеточия
     * в маршруте недопустимы — без кодирования приложение падало при открытии такого канала.
     *
     * @param channelId идентификатор канала.
     */
    fun player(channelId: ChannelId): String = "player/${Uri.encode(channelId.value)}"

    /**
     * Собирает маршрут плеера с архивной передачей.
     *
     * @param channelId идентификатор канала;
     * @param startEpochSeconds начало передачи в секундах эпохи.
     */
    fun playerArchive(channelId: ChannelId, startEpochSeconds: Long): String =
        "player/${Uri.encode(channelId.value)}?start=$startEpochSeconds"

    /**
     * Собирает маршрут программы канала.
     *
     * @param channelId идентификатор канала.
     */
    fun programme(channelId: ChannelId): String = "programme/${Uri.encode(channelId.value)}"
}

/**
 * Навигация приложения.
 *
 * @param startDestination стартовый маршрут: онбординг или сразу главный экран;
 * @param pendingChannelId канал из диплинка ленты «Сейчас идёт», который надо открыть в плеере;
 * @param onPendingChannelConsumed диплинк обработан — повторять его не нужно;
 * @param navController контроллер навигации.
 */
@Composable
fun AppNavHost(
    startDestination: String,
    pendingChannelId: ChannelId? = null,
    onPendingChannelConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable(Route.ONBOARDING) {
                val toast = LocalToastController.current
                // «Отмена» и Esc есть только при входе из настроек: на первом запуске
                // возвращаться некуда (решение Д12).
                val canCancel = navController.previousBackStackEntry != null
                OnboardingScreen(
                    onConnected = {
                        // Тост переживает переход: его видно уже на «Телеканалах».
                        toast.show("Источник подключён, каналы загружены")
                        navController.navigate(Route.HOME) {
                            popUpTo(Route.HOME) { inclusive = true }
                            popUpTo(Route.ONBOARDING) { inclusive = true }
                        }
                    },
                    onCancel = if (canCancel) {
                        { navController.popBackStack() }
                    } else {
                        null
                    },
                )
            }

            composable(Route.HOME) {
                HomeScreen(
                    onChannelOpen = { channelId -> navController.navigate(Route.player(channelId)) },
                    onOpenArchive = { channelId, start ->
                        navController.navigate(Route.playerArchive(channelId, start.epochSecond))
                    },
                    onOpenSettings = { navController.navigate(Route.SETTINGS) },
                )
            }

        composable(
            route = Route.PLAYER,
            arguments = listOf(
                navArgument(PlayerViewModel.ARG_CHANNEL_ID) { type = NavType.StringType },
                navArgument(PlayerViewModel.ARG_START) {
                    type = NavType.LongType
                    defaultValue = PlayerViewModel.NO_START
                },
            ),
        ) {
            PlayerScreen(
                onBack = { navController.popBackStack() },
                // Переключение канала заменяет текущий экран плеера, иначе «назад» отматывает
                // все просмотренные каналы по одному вместо возврата в список.
                onGoLive = { id ->
                    navController.navigate(Route.player(id)) {
                        popUpTo(navController.currentDestination?.id ?: 0) { inclusive = true }
                    }
                },
                onOpenRecent = { id ->
                    navController.navigate(Route.player(ChannelId(id))) {
                        popUpTo(navController.currentDestination?.id ?: 0) { inclusive = true }
                    }
                },
                onOpenProgramme = { channelId -> navController.navigate(Route.programme(channelId)) },
                onOpenArchive = { channelId, start ->
                    navController.navigate(Route.playerArchive(channelId, start.epochSecond))
                },
            )
        }

        composable(
            route = Route.PROGRAMME,
            arguments = listOf(
                navArgument(ProgrammeViewModel.ARG_CHANNEL_ID) { type = NavType.StringType },
            ),
        ) {
            ProgrammeScreen(
                onPlay = { channelId, start ->
                    val target = if (start != null) {
                        Route.playerArchive(channelId, start.epochSecond)
                    } else {
                        Route.player(channelId)
                    }
                    navController.navigate(target)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Route.SETTINGS) {
            SettingsScreen(
                onChangeSource = { navController.navigate(Route.ONBOARDING) },
                onBack = { navController.popBackStack() },
            )
        }
    }

    // Единственный тост приложения: снизу по центру, поверх любого экрана.
    val toast = LocalToastController.current
    TvToast(
        message = toast.message,
        onDismiss = toast::clear,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = TvDimens.toastBottom),
    )

    // Диплинк из карточки ленты: канал открывается сразу в плеере. Без настроенного
    // источника открывать нечего — пользователь попадает в онбординг.
    LaunchedEffect(pendingChannelId, startDestination) {
        val channelId = pendingChannelId ?: return@LaunchedEffect
        if (startDestination == Route.HOME) {
            navController.navigate(Route.player(channelId)) { launchSingleTop = true }
        }
        onPendingChannelConsumed()
    }
    }
}
