package com.mops1k.smotrishka.tv.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mops1k.smotrishka.tv.core.designsystem.component.TvToast
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.ui.about.AboutScreen
import com.mops1k.smotrishka.tv.ui.about.UpdatePromptDialog
import com.mops1k.smotrishka.tv.ui.about.UpdateViewModel
import com.mops1k.smotrishka.tv.ui.common.LocalToastController
import com.mops1k.smotrishka.tv.ui.common.WithAppFontScale
import com.mops1k.smotrishka.tv.ui.home.HomeScreen
import com.mops1k.smotrishka.tv.ui.onboarding.OnboardingScreen
import com.mops1k.smotrishka.tv.ui.player.PlayerScreen
import com.mops1k.smotrishka.tv.ui.player.PlayerViewModel
import com.mops1k.smotrishka.tv.ui.programme.ProgrammeScreen
import com.mops1k.smotrishka.tv.ui.programme.ProgrammeViewModel
import com.mops1k.smotrishka.tv.ui.settings.SettingsScreen
import com.mops1k.smotrishka.tv.ui.shell.LocalUpdateBadge
import com.mops1k.smotrishka.tv.ui.vod.VodCatalogScreen
import com.mops1k.smotrishka.tv.ui.vod.VodFavoritesScreen
import com.mops1k.smotrishka.tv.ui.vod.VodPlayerScreen
import com.mops1k.smotrishka.tv.ui.vod.VodPlayerViewModel
import com.mops1k.smotrishka.tv.ui.vod.VodTitleScreen
import com.mops1k.smotrishka.tv.ui.vod.VodTitleViewModel
import com.mops1k.smotrishka.tv.update.UpdateState

/** Маршруты приложения. */
object Route {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"

    /** Медиатека: отдельный раздел, виден только у проверенного портала. */
    const val MEDIATEKA = "mediateka"

    /** Карточка медиатеки: `vod/title/<id карточки>`. */
    const val VOD_DETAIL = "vod/title/{titleId}"

    /** Избранное медиатеки. */
    const val VOD_FAVORITES = "vod/favorites"

    /** VOD-плеер медиатеки: `vod/play/<id карточки>?episode=<id серии>&quality=<качество>`. */
    const val VOD_PLAYER = "vod/play/{titleId}?episode={episode}&quality={quality}"

    /**
     * Собирает маршрут карточки медиатеки.
     *
     * @param titleId идентификатор карточки.
     */
    fun vodTitle(titleId: Int): String = "vod/title/$titleId"

    /**
     * Собирает маршрут VOD-плеера.
     *
     * @param titleId идентификатор карточки;
     * @param episodeId выбранная серия, `null` — продолжение с сохранённого места;
     * @param quality выбранное качество, `null` — «Авто».
     */
    fun vodPlayer(titleId: Int, episodeId: Int? = null, quality: String? = null): String {
        val query = buildList {
            episodeId?.let { add("episode=$it") }
            quality?.let { add("quality=$it") }
        }
        return "vod/play/$titleId" + if (query.isEmpty()) "" else "?" + query.joinToString("&")
    }

    /** «О приложении»: версия и обновление. */
    const val ABOUT = "about"

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
 * Здесь же живёт обновление приложения: диалог «Обновить сейчас / Позже / Не спрашивать»
 * при запуске, тосты и маркер на пункте «О приложении» в сайдбаре.
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
    // Единственный тост приложения: снизу по центру, поверх любого экрана.
    val toast = LocalToastController.current

    val updateViewModel: UpdateViewModel = hiltViewModel()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    // Ключ запроса раздела: меню «Медиатеки» передаёт его главному экрану.
    val SECTION_REQUEST_KEY = "home-section-request"

    val available = updateState as? UpdateState.Available
    val updateBadge = available != null && !available.dismissed

    // Вопрос об обновлении не перебивает онбординг и просмотр: только на экранах-разделах.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val promptAllowed = currentRoute != Route.ONBOARDING &&
        currentRoute?.startsWith("player") != true
    var promptShown by rememberSaveable { mutableStateOf(false) }
    var promptVisible by remember { mutableStateOf(false) }

    LaunchedEffect(available, promptAllowed) {
        if (promptAllowed && available != null && !available.dismissed && !promptShown) {
            promptShown = true
            promptVisible = true
        }
    }

    // Сообщения обновления: «Доступно обновление 0.2.0», «Обновление скачивается только по Wi-Fi».
    LaunchedEffect(updateViewModel) {
        updateViewModel.messages.collect { message -> toast.show(message) }
    }

    CompositionLocalProvider(LocalUpdateBadge provides updateBadge) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Размер шрифта из настроек применяется ко всем экранам сразу; плеер внутри себя
            // оставляет меню обычного размера (решение Александра 09.11.2026).
            WithAppFontScale {
            NavHost(navController = navController, startDestination = startDestination) {
                composable(Route.ONBOARDING) {
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

                composable(Route.HOME) { entry ->
                    HomeScreen(
                        onChannelOpen = { channelId -> navController.navigate(Route.player(channelId)) },
                        onOpenArchive = { channelId, start ->
                            navController.navigate(Route.playerArchive(channelId, start.epochSecond))
                        },
                        onOpenSettings = { navController.navigate(Route.SETTINGS) },
                        onOpenAbout = { navController.navigate(Route.ABOUT) { launchSingleTop = true } },
                        onOpenMediateka = {
                            navController.navigate(Route.MEDIATEKA) { launchSingleTop = true }
                        },
                        onOpenVodFavorites = {
                            navController.navigate(Route.VOD_FAVORITES) { launchSingleTop = true }
                        },
                        requestedSection = entry.savedStateHandle.get<String>(SECTION_REQUEST_KEY),
                        onSectionRequestHandled = {
                            entry.savedStateHandle.remove<String>(SECTION_REQUEST_KEY)
                        },
                    )
                }

                composable(Route.VOD_FAVORITES) {
                    VodFavoritesScreen(
                        onBack = { navController.popBackStack() },
                        onOpenTitle = { titleId -> navController.navigate(Route.vodTitle(titleId.value)) },
                        onWatch = { titleId -> navController.navigate(Route.vodPlayer(titleId.value)) },
                        onOpenSettings = {
                            navController.navigate(Route.SETTINGS) { launchSingleTop = true }
                        },
                    )
                }

                composable(Route.MEDIATEKA) {
                    VodCatalogScreen(
                        onBack = { navController.backToHome() },
                        // Пункты меню на этом экране открывают раздел главного, а не просто
                        // закрывают «Медиатеку»: раньше «Избранное» приводило на «Телеканалы».
                        onOpenSection = { destination ->
                            runCatching {
                                navController.getBackStackEntry(Route.HOME).savedStateHandle[
                                    SECTION_REQUEST_KEY,
                                ] = destination.name
                            }
                            navController.backToHome()
                        },
                        onOpenTitle = { titleId -> navController.navigate(Route.vodTitle(titleId.value)) },
                        onWatch = { title -> navController.navigate(Route.vodPlayer(title.id.value)) },
                        onOpenSettings = {
                            navController.navigate(Route.SETTINGS) { launchSingleTop = true }
                        },
                    )
                }

                composable(
                    route = Route.VOD_DETAIL,
                    arguments = listOf(
                        navArgument(VodTitleViewModel.ARG_TITLE_ID) { type = NavType.StringType },
                    ),
                ) {
                    VodTitleScreen(
                        onBack = { navController.popBackStack() },
                        onWatch = { titleId, episodeId, quality ->
                            navController.navigate(
                                Route.vodPlayer(titleId.value, episodeId?.value, quality?.key),
                            )
                        },
                        onOpenSettings = {
                            navController.navigate(Route.SETTINGS) { launchSingleTop = true }
                        },
                    )
                }

                composable(
                    route = Route.VOD_PLAYER,
                    arguments = listOf(
                        navArgument(VodPlayerViewModel.ARG_TITLE_ID) { type = NavType.StringType },
                        navArgument(VodPlayerViewModel.ARG_EPISODE_ID) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(VodPlayerViewModel.ARG_QUALITY) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) {
                    VodPlayerScreen(onBack = { navController.popBackStack() })
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
                        // «Назад» из плеера всегда ведёт к списку каналов: повторное нажатие
                        // не должно выкидывать приложение в пустой экран.
                        onBack = { navController.backToHome() },
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
                            // Архив открывается вместо текущего плеера, а не поверх него:
                            // иначе «назад» отматывает цепочку каналов по одному, а должен
                            // сразу возвращать к списку каналов с превью.
                            navController.navigate(Route.playerArchive(channelId, start.epochSecond)) {
                                popUpTo(navController.currentDestination?.id ?: 0) { inclusive = true }
                            }
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
                            // Плеер открывается вместо экрана программы: «назад» из плеера
                            // возвращает к списку каналов с превью, а не на программу.
                            navController.navigate(target) {
                                popUpTo(Route.HOME)
                            }
                        },
                        // «Назад» с экрана программы — всегда к списку каналов (замечание
                        // Александра 09.11.2026).
                        onBack = { navController.backToHome() },
                    )
                }

                composable(Route.SETTINGS) {
                    SettingsScreen(
                        onChangeSource = { navController.navigate(Route.ONBOARDING) },
                        onOpenAbout = { navController.navigate(Route.ABOUT) { launchSingleTop = true } },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Route.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onOpenSettings = {
                            navController.navigate(Route.SETTINGS) { launchSingleTop = true }
                        },
                    )
                }
            }

            TvToast(
                message = toast.message,
                onDismiss = toast::clear,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TvDimens.toastBottom),
            )

            if (promptVisible && available != null && !available.dismissed) {
                UpdatePromptDialog(
                    version = available.release.version.toString(),
                    sizeBytes = available.apk.sizeBytes,
                    onUpdateNow = {
                        promptVisible = false
                        updateViewModel.downloadNow()
                    },
                    onLater = { promptVisible = false },
                    onDismissVersion = {
                        promptVisible = false
                        updateViewModel.dismissCurrent()
                    },
                )
            }

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
    }
}

/**
 * Возвращает на главный экран, не оставляя пустой стек.
 *
 * Раньше «назад» из плеера делал обычный `popBackStack()`: при повторном нажатии он выбивал
 * и главный экран, и приложение показывало пустое серое окно (замечание Александра
 * 09.11.2026). Здесь вторая попытка не разрушает стек: если главного экрана уже нет,
 * он восстанавливается.
 */
private fun NavHostController.backToHome() {
    android.util.Log.d(
        "SmotrishkaNav",
        "назад: текущий=${currentDestination?.route}",
    )
    if (popBackStack(Route.HOME, inclusive = false)) return
    if (currentDestination?.route == Route.HOME) return
    // Стек пуст или начальный экран другой — собираем главный экран заново.
    navigate(Route.HOME) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
