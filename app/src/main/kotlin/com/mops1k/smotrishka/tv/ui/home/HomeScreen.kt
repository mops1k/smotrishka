package com.mops1k.smotrishka.tv.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.data.SearchHitKind
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.ui.common.LocalToastController
import com.mops1k.smotrishka.tv.ui.common.TextFormat
import com.mops1k.smotrishka.tv.ui.common.rememberDeviceProfile
import com.mops1k.smotrishka.tv.ui.home.components.ArchivePickerDialog
import com.mops1k.smotrishka.tv.ui.home.components.CategoryPane
import com.mops1k.smotrishka.tv.ui.home.components.ChannelCard
import com.mops1k.smotrishka.tv.ui.home.components.ChannelListPanel
import com.mops1k.smotrishka.tv.ui.search.SearchOverlay
import com.mops1k.smotrishka.tv.ui.shell.AppShell
import com.mops1k.smotrishka.tv.ui.shell.ShellFocusTargets
import com.mops1k.smotrishka.tv.ui.shell.rememberShellFocusTargets
import androidx.compose.ui.platform.LocalConfiguration
import com.mops1k.smotrishka.tv.ui.shell.ShellDestination
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Главный экран: каркас (сайдбар, шапка), раздел «Телеканалы» / «Избранное» / «Недавние»,
 * список каналов и карточка выбранного.
 *
 * Строка списка только выбирает канал: в плеер ведёт кнопка «Смотреть», в телепрограмму —
 * «Телепрограмма канала» (`docs/design/flows.md`).
 *
 * @param onChannelOpen открыть канал в плеере;
 * @param onOpenArchive открыть прошедшую передачу в архиве;
 * @param onOpenSettings открыть настройки;
 * @param viewModel экранная модель.
 */
@Composable
fun HomeScreen(
    onChannelOpen: (ChannelId) -> Unit = {},
    onOpenArchive: (ChannelId, Instant) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dataSaver by viewModel.dataSaver.collectAsStateWithLifecycle()
    val bufferSeconds by viewModel.bufferSeconds.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val device = rememberDeviceProfile()
    // Живое превью: на приставке оно тоже нужно (PreviewPolicy), гасит его экономия трафика.
    val livePreview = PreviewPolicy.showLive(
        isTv = device.isTelevision,
        isLowRam = device.isLowRam,
        dataSaver = dataSaver,
    )
    // Перед открытием плеера превью отпускает декодер: открытие канала откладывается на
    // пару кадров, иначе HEVC-канал не может инициализировать кодек.
    var previewSuppressed by remember { mutableStateOf(false) }
    // При открытии телепрограммы фокус ставится на текущую передачу — и тогда левое меню
    // остаётся свёрнутым, потому что фокус не в нём.
    val programmeFocus = remember { FocusRequester() }
    val openScope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val toastController = LocalToastController.current
    val narrow = LocalConfiguration.current.screenWidthDp < 980
    // Двухколоночная карточка нужна только там, где копия не помещается по высоте:
    // на телефоне в альбомной ориентации (~360 dp), но не на планшете и не на ТВ.
    val compactCard = LocalConfiguration.current.screenHeightDp < 500

    // Возврат с настроек или из плеера должен показать свежие каналы и передачу.
    // Без force: если каталог не обновлялся, экран не пересобирается заново на каждый возврат
    // из плеера — раньше это давало повторные проходы по 3752 каналам.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    // Время на экране идёт, пока он открыт: иначе прогресс передачи «застывает».
    LaunchedEffect(Unit) {
        while (true) {
            delay(HOME_TICK_MS)
            viewModel.tick()
        }
    }

    var searchOpen by remember { mutableStateOf(false) }
    // Окно «Архив» для каналов без программы передач, но с архивом.
    var archivePickerOpen by remember { mutableStateOf(false) }

    val content = state as? HomeUiState.Content
    val programmeOpen = content?.programmeOpen == true

    // Приоритет закрытия (`docs/design/flows.md`): поиск → разворот программы → система.
    val lifecycleResumed by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleResumed.isAtLeast(Lifecycle.State.RESUMED)
    BackHandler(enabled = resumed && searchOpen) {
        searchOpen = false
        viewModel.closeSearch()
    }
    BackHandler(enabled = resumed && !searchOpen && programmeOpen) {
        viewModel.closeProgramme()
    }
    // «Назад» переключает уровни: из списка каналов — в категории, из категорий — обратно
    // в список. Иначе в категориях «назад» уходил из приложения.
    val showChannels = content?.showChannels == true
    BackHandler(enabled = resumed && !searchOpen && !programmeOpen && content != null) {
        if (showChannels) viewModel.showCategories() else viewModel.showChannelsList()
    }

    // Открыли телепрограмму — фокус на текущую передачу, левое меню при этом свёрнуто.
    LaunchedEffect(programmeOpen) {
        if (programmeOpen) {
            repeat(3) {
                withFrameNanos { }
                runCatching { programmeFocus.requestFocus() }
                delay(40)
            }
        }
    }

    // Возврат из плеера снова разрешает живое превью.
    LaunchedEffect(Unit) { previewSuppressed = false }

    // Боковое меню получает фокус только по явному «влево» с левого края контента.
    var sidebarFocused by remember { mutableStateOf(false) }
    val focusTargets = rememberShellFocusTargets()
    val activateMenu: () -> Unit = {
        sidebarFocused = true
    }
    // «Влево» из списка каналов открывает категории и ставит на них фокус:
    // дальше ещё «влево» ведёт в меню — это и есть край.
    val leftToCategories: () -> Unit = {
        viewModel.showCategories()
        openScope.launch {
            withFrameNanos { }
            runCatching { focusTargets.categories.requestFocus() }
        }
    }
    // Где сейчас фокус: в категориях или в списке каналов. По этим флагам экран решает,
    // куда ведёт «влево»: список → категории, категории → меню (указание 07.11.2026).
    var inCategories by remember { mutableStateOf(false) }
    var inList by remember { mutableStateOf(false) }
    val handleLeft: () -> Boolean = {
        when {
            sidebarFocused -> false
            inCategories -> {
                sidebarFocused = true
                true
            }
            inList -> {
                if (content?.section == HomeSection.TV) leftToCategories() else sidebarFocused = true
                true
            }
            else -> false
        }
    }

    CompositionLocalProvider(LocalPreviewSuppressed provides previewSuppressed) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // «Влево» решается здесь по тому, где фокус: список → категории,
                // категории → меню. Геометрический поиск фокуса не используется:
                // из-за него меню разворачивалось без фокуса внутри.
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> handleLeft()
                    Key.DirectionRight -> {
                        if (sidebarFocused) {
                            sidebarFocused = false
                            // Фокус возвращаем в контент сами: пункты меню перестают быть
                            // фокусируемыми, и без этого фокус терялся бы вовсе.
                            runCatching { focusTargets.list.requestFocus() }
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
    AppShell(
        sidebarFocused = sidebarFocused,
        menuFocusRequester = focusTargets.menu,
        destination = content?.section?.toDestination() ?: ShellDestination.TV,
        onSelect = { destination ->
            if (destination == ShellDestination.SETTINGS) {
                onOpenSettings()
            } else {
                viewModel.selectSection(destination.toSection())
            }
        },
        onSearch = { searchOpen = true },
        onSearchLeftEdge = activateMenu,
        searchFocusRequester = focusTargets.search,
    ) {
        when (val current = state) {
            HomeUiState.Loading -> CenteredMessage("Загружаем каналы…")
            HomeUiState.Empty -> CenteredMessage("Каналов нет. Проверьте источник в настройках.")
            is HomeUiState.Error -> CenteredMessage(current.reason)
            is HomeUiState.Content -> SectionContent(
                content = current,
                zone = zone,
                showLogos = !dataSaver,
                livePreview = livePreview,
                bufferSeconds = bufferSeconds,
                narrow = narrow,
                compactCard = compactCard,
                onSelectCategory = viewModel::selectCategory,
                onSelectChannel = viewModel::selectChannel,
                onToggleFavorite = viewModel::toggleFavorite,
                onWatch = { id ->
                    // OK подтверждает канал: живое превью переключается только по нему.
                    viewModel.confirmPreview(id)
                    // Смотрим канал из поиска — после возврата поиск должен быть сброшен.
                    viewModel.closeSearch()
                    // Живое превью отпускает аппаратный декодер до старта плеера: иначе
                    // HEVC-канал не может инициализировать кодек на приставке.
                    previewSuppressed = true
                    openScope.launch {
                        delay(PREVIEW_RELEASE_MS)
                        onChannelOpen(id)
                    }
                },
                onOpenProgramme = viewModel::openProgramme,
                onCollapseProgramme = viewModel::closeProgramme,
                programmeFocus = programmeFocus,
                focusTargets = focusTargets,
                onActivateMenu = activateMenu,
                onOpenArchivePicker = { archivePickerOpen = true },
                onLeftToCategories = leftToCategories,
                onFocusInCategories = { inCategories = it },
                onFocusInList = { inList = it },
                onChangeEpgShift = viewModel::adjustEpgShift,
                onScheduleClick = { row ->
                    val channel = current.previewChannel ?: return@SectionContent
                    when {
                        row.status == ProgrammeStatus.NOW -> onChannelOpen(channel.channel.id)
                        row.hasArchive -> onOpenArchive(channel.channel.id, row.programme.start)
                        row.status == ProgrammeStatus.PAST ->
                            toastController.show("Архив недоступен для этого канала")

                        else -> toastController.show(
                            TextFormat.startsAt(
                                title = row.programme.title,
                                start = row.programme.start,
                                zone = zone,
                            ),
                        )
                    }
                },
            )
        }
    }

    if (searchOpen) {
        SearchOverlay(
            query = content?.query.orEmpty(),
            onQueryChange = viewModel::setQuery,
            results = searchResults,
            onPick = { hit ->
                searchOpen = false
                viewModel.closeSearch()
                when (hit.kind) {
                    // Категория переключает раздел, канал и передача открывают плеер.
                    // selectCategory сам снимает режим категорий и показывает каналы.
                    SearchHitKind.CATEGORY -> viewModel.selectCategory(hit.refId)

                    else -> onChannelOpen(ChannelId(hit.refId))
                }
            },
            onClose = {
                searchOpen = false
                viewModel.closeSearch()
            },
            // «Искать»: окно закрывается, в списке остаются каналы по запросу.
            onSubmit = {
                searchOpen = false
                viewModel.applySearchFilter()
            },
        )
    }

    // Архив без программы: выбираем момент по времени назад по глубине архива.
    val archiveChannel = content?.previewChannel?.channel
    if (archivePickerOpen && archiveChannel != null) {
        ArchivePickerDialog(
            channelName = archiveChannel.name,
            zone = zone,
            hours = (archiveChannel.archive?.days ?: 0) * 24,
            onPick = { moment ->
                archivePickerOpen = false
                onOpenArchive(archiveChannel.id, moment)
            },
            onDismiss = { archivePickerOpen = false },
        )
    }
    }
    }
}

/** Пауза перед входом в плеер: живое превью успевает отдать аппаратный декодер. */
private const val PREVIEW_RELEASE_MS = 250L

/**
 * Содержимое раздела: плашка категорий (только «Телеканалы») и сетка «список + карточка».
 *
 * @param content состояние раздела;
 * @param zone часовой пояс;
 * @param showLogos грузить ли логотипы;
 * @param livePreview показывать ли живое превью (только телефоны и планшеты);
 * @param bufferSeconds сколько секунд держать в буфере превью;
 * @param onSelectCategory выбор категории;
 * @param onSelectChannel выбор канала;
 * @param onToggleFavorite переключение избранного;
 * @param onWatch открыть канал в плеере;
 * @param onOpenProgramme развернуть телепрограмму;
 * @param onCollapseProgramme свернуть телепрограмму;
 * @param onScheduleClick клик по передаче в развороте.
 */
@Composable
private fun SectionContent(
    content: HomeUiState.Content,
    zone: ZoneId,
    showLogos: Boolean,
    livePreview: Boolean,
    bufferSeconds: Int,
    narrow: Boolean,
    compactCard: Boolean,
    onSelectCategory: (String?) -> Unit,
    onSelectChannel: (ChannelId) -> Unit,
    onToggleFavorite: (ChannelId) -> Unit,
    onWatch: (ChannelId) -> Unit,
    onOpenProgramme: () -> Unit,
    onCollapseProgramme: () -> Unit,
    onScheduleClick: (ProgrammeRowUi) -> Unit,
    programmeFocus: FocusRequester? = null,
    // Адреса фокуса: список, категории, меню — направления «влево» задаются явно.
    focusTargets: ShellFocusTargets = rememberShellFocusTargets(),
    onActivateMenu: () -> Unit = {},
    // Открыть окно выбора момента архива (когда программы передач нет).
    onOpenArchivePicker: () -> Unit = {},
    // «Влево» из списка каналов: открыть категории.
    onLeftToCategories: () -> Unit = {},
    // Фокус внутри категорий / списка: экран по ним решает, куда ведёт «влево».
    onFocusInCategories: (Boolean) -> Unit = {},
    onFocusInList: (Boolean) -> Unit = {},
    // Сдвиг гида текущего канала карточки: значение и изменение на ±1 час.
    onChangeEpgShift: ((Int) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(25.dp),
        ) {
            // Открытая телепрограмма занимает весь экран: список каналов и категории
            // в этот момент скрыты (указание Александра 05.11.2026).
            if (content.programmeOpen) {
                // Список каналов и категории не показываем: место отдано передачам.
            } else if (content.section == HomeSection.TV && !content.showChannels) {
                CategoryPane(
                    categories = content.categories,
                    selectedCategoryId = content.selectedCategoryId,
                    onSelect = onSelectCategory,
                    modifier = Modifier.weight(1.15f),
                    onActivateMenu = onActivateMenu,
                    rowFocusRequester = focusTargets.categories,
                    onFocusInCategories = onFocusInCategories,
                )
            } else {
            ChannelListPanel(
                // Пока действует поиск, в заголовке — запрос, а не название категории:
                // так видно, что список отфильтрован («Симпсоны», 2 канала).
                title = when {
                    content.query.isNotBlank() -> content.query
                    content.section == HomeSection.TV ->
                        content.categories.firstOrNull { it.id == content.selectedCategoryId }?.name
                            ?: "Все каналы"
                    else -> null
                },
                channels = content.channels,
                selectedChannelId = content.selectedChannelId,
                // Играющий канал: синий фон закреплён за ним и не следует за фокусом.
                playingChannelId = content.previewVideoChannelId,
                epgPending = content.epgPending,
                now = content.now,
                zone = zone,
                showLogos = showLogos,
                archiveVerified = content.archiveVerified,
                unstable = content.unstable,
                emptyTitle = emptyTitleFor(content.section, content.query),
                emptySubtitle = emptySubtitleFor(content.section, content.query),
                // Наведение фокуса только выбирает канал для превью. Плеер открывает
                // исключительно OK: раньше общий колбэк открывал плеер уже на фокусе,
                // из-за чего в список каналов нельзя было попасть.
                onSelect = onSelectChannel,
                onOpen = onWatch,
                onShowProgramme = onOpenProgramme,
                narrow = narrow,
                modifier = Modifier.weight(1.15f),
                // Категории видны — «влево» из списка идёт в них; категорий нет —
                // список открывает их сам, а в разделах без категорий — меню.
                leftTarget = if (content.section == HomeSection.TV && !content.showChannels) {
                    focusTargets.categories
                } else {
                    null
                },
                rowFocusRequester = focusTargets.list,
                onActivateMenu = when {
                    content.section != HomeSection.TV -> onActivateMenu
                    !content.showChannels -> null
                    else -> onLeftToCategories
                },
                onFocusInList = onFocusInList,
                // Что за список показан: по этому ключу сбрасываются прокрутка и фокус,
                // иначе они переезжали из «Телеканалов» в «Избранное» и поиск.
                listKey = "${content.section}|${content.selectedCategoryId.orEmpty()}|${content.query}",
            )
            }

            // Карточка: «влево» с её кнопок и сердечка уводит фокус в список каналов.
            Box(
                modifier = Modifier.weight(if (content.programmeOpen) 1f else 0.85f),
            ) {
            ChannelCard(
                channel = content.previewChannel,
                // Видео держим на канале, подтверждённом OK: листание списка его не меняет.
                previewChannel = content.previewVideoChannel ?: content.previewChannel,
                categoryName = content.categories
                    .firstOrNull { it.id == content.previewChannel?.channel?.categoryId }
                    ?.name,
                favorite = content.previewChannel?.channel?.id?.value in content.favorites,
                now = content.now,
                zone = zone,
                showLogo = showLogos,
                livePreview = livePreview,
                bufferSeconds = bufferSeconds,
                programmeOpen = content.programmeOpen,
                programmeFocus = programmeFocus,
                epgPending = content.epgPending,
                compactCard = compactCard,
                schedule = content.schedule,
                onToggleFavorite = {
                    content.previewChannel?.let { onToggleFavorite(it.channel.id) }
                },
                onWatch = { content.previewChannel?.let { onWatch(it.channel.id) } },
                onOpenProgramme = onOpenProgramme,
                onCollapseProgramme = onCollapseProgramme,
                onScheduleClick = onScheduleClick,
                narrow = narrow,
                leftTarget = focusTargets.list,
                upTarget = focusTargets.search,
                epgShiftHours = content.epgShiftHours,
                onChangeEpgShift = onChangeEpgShift,
                // Передач нет, но архив есть — вместо «Программы» кнопка «Архив».
                onOpenArchivePicker = if (
                    content.schedule.isEmpty() && content.previewChannel?.channel?.archive != null
                ) {
                    onOpenArchivePicker
                } else {
                    null
                },
            )
            }
        }
    }
}

/** Заголовок пустого состояния: зависит от раздела и от того, включён ли поиск. */
private fun emptyTitleFor(section: HomeSection, query: String): String = when {
    query.isNotBlank() -> "Каналы не найдены"
    section == HomeSection.FAVORITES -> "Пока нет избранных каналов"
    section == HomeSection.RECENT -> "Вы ещё не смотрели каналы"
    else -> "Каналы не найдены"
}

/** Подпись пустого состояния. */
private fun emptySubtitleFor(section: HomeSection, query: String): String = when {
    query.isNotBlank() -> "Попробуйте изменить запрос"
    section == HomeSection.FAVORITES -> "Отметьте канал сердцем в карточке"
    section == HomeSection.RECENT -> "Включите любой канал — он появится здесь"
    else -> "Попробуйте выбрать другую категорию"
}

/** Раздел каркаса, соответствующий состоянию главного экрана. */
private fun HomeSection.toDestination(): ShellDestination = when (this) {
    HomeSection.TV -> ShellDestination.TV
    HomeSection.FAVORITES -> ShellDestination.FAVORITES
    HomeSection.RECENT -> ShellDestination.RECENT
}

/** Состояние главного экрана, соответствующее пункту меню. */
private fun ShellDestination.toSection(): HomeSection = when (this) {
    ShellDestination.FAVORITES -> HomeSection.FAVORITES
    ShellDestination.RECENT -> HomeSection.RECENT
    else -> HomeSection.TV
}

/** Сообщение по центру экрана: загрузка, пустой каталог, ошибка. */
@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(
            text = text,
            style = com.mops1k.smotrishka.tv.core.designsystem.theme.TvType.panelTitle,
            color = TvColors.muted,
        )
    }
}

/** Как часто обновляем время на главном экране. */
private const val HOME_TICK_MS = 20_000L
