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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.model.ChannelId
import com.mops1k.smotrishka.tv.ui.common.LocalToastController
import com.mops1k.smotrishka.tv.ui.common.TextFormat
import com.mops1k.smotrishka.tv.ui.common.rememberDeviceProfile
import com.mops1k.smotrishka.tv.ui.home.components.CategoryRow
import com.mops1k.smotrishka.tv.ui.home.components.ChannelCard
import com.mops1k.smotrishka.tv.ui.home.components.ChannelListPanel
import com.mops1k.smotrishka.tv.ui.search.SearchOverlay
import com.mops1k.smotrishka.tv.ui.shell.AppShell
import androidx.compose.ui.platform.LocalConfiguration
import com.mops1k.smotrishka.tv.ui.shell.ShellDestination
import kotlinx.coroutines.delay
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
    val device = rememberDeviceProfile()
    // Живое превью — только на телефонах и планшетах, не на ТВ (PreviewPolicy).
    val livePreview = PreviewPolicy.showLive(
        isTv = device.isTelevision,
        isLowRam = device.isLowRam,
        dataSaver = dataSaver,
    )
    val zone = remember { ZoneId.systemDefault() }
    val toastController = LocalToastController.current
    val narrow = LocalConfiguration.current.screenWidthDp < 980
    // Двухколоночная карточка нужна только там, где копия не помещается по высоте:
    // на телефоне в альбомной ориентации (~360 dp), но не на планшете и не на ТВ.
    val compactCard = LocalConfiguration.current.screenHeightDp < 500

    // Возврат с настроек или из плеера должен показать свежие каналы и передачу.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh(force = true) }

    // Время на экране идёт, пока он открыт: иначе прогресс передачи «застывает».
    LaunchedEffect(Unit) {
        while (true) {
            delay(HOME_TICK_MS)
            viewModel.tick()
        }
    }

    var searchOpen by remember { mutableStateOf(false) }

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

    AppShell(
        destination = content?.section?.toDestination() ?: ShellDestination.TV,
        onSelect = { destination ->
            if (destination == ShellDestination.SETTINGS) {
                onOpenSettings()
            } else {
                viewModel.selectSection(destination.toSection())
            }
        },
        onSearch = { searchOpen = true },
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
                onWatch = { onChannelOpen(it) },
                onOpenProgramme = viewModel::openProgramme,
                onCollapseProgramme = viewModel::closeProgramme,
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
            onClose = {
                searchOpen = false
                viewModel.closeSearch()
            },
        )
    }
}

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
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (content.section == HomeSection.TV) {
            CategoryRow(
                categories = content.categories,
                selectedId = content.selectedCategoryId,
                onSelect = onSelectCategory,
            )
            Spacer(Modifier.height(14.dp))
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(25.dp),
        ) {
            ChannelListPanel(
                title = if (content.section == HomeSection.TV) {
                    content.categories.firstOrNull { it.id == content.selectedCategoryId }?.name
                        ?: "Все каналы"
                } else {
                    null
                },
                channels = content.channels,
                selectedChannelId = content.selectedChannelId,
                now = content.now,
                zone = zone,
                showLogos = showLogos,
                archiveVerified = content.archiveVerified,
                unstable = content.unstable,
                emptyTitle = emptyTitleFor(content.section, content.query),
                emptySubtitle = emptySubtitleFor(content.section, content.query),
                onSelect = onSelectChannel,
                onShowProgramme = onOpenProgramme,
                narrow = narrow,
                modifier = Modifier.weight(if (content.programmeOpen) 0.68f else 1.15f),
            )

            ChannelCard(
                channel = content.previewChannel,
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
                modifier = Modifier.weight(if (content.programmeOpen) 1.32f else 0.85f),
            )
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
