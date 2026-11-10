package com.mops1k.smotrishka.tv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mops1k.smotrishka.tv.core.designsystem.component.TvWatchButton
import com.mops1k.smotrishka.tv.core.designsystem.component.TvSecondaryButton
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategory
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.ui.shell.rememberShellFocusTargets
import com.mops1k.smotrishka.tv.ui.shell.AppShell
import com.mops1k.smotrishka.tv.ui.shell.ShellDestination

/**
 * Экран «Медиатека»: категории портала слева, сетка постеров справа, превью выделенной карточки.
 *
 * «Медиатека» — отдельный маршрут: пункт меню показывается только у проверенного портала,
 * поэтому экран может попасть на отсутствие подключения лишь после сброса настроек — тогда он
 * предлагает вернуться в настройки.
 *
 * Догрузка следующей страницы запускается сама, когда фокус подходит к концу сетки: отдельной
 * кнопки «показать ещё» нет (решение Александра 10.11.2026).
 *
 * @param onBack вернуться на главный экран;
 * @param onOpenTitle открыть карточку фильма или сезона;
 * @param onWatch запустить воспроизведение;
 * @param onOpenSettings открыть настройки — подключить портал;
 * @param viewModel экранная модель.
 */
@Composable
fun VodCatalogScreen(
    onBack: () -> Unit,
    onOpenTitle: (VodTitleId) -> Unit = {},
    onWatch: (VodTitle) -> Unit = {},
    onOpenSettings: () -> Unit,
    // Пункт меню на этом экране открывает раздел главного экрана, а не закрывает «Медиатеку».
    onOpenSection: (ShellDestination) -> Unit = {},
    viewModel: VodCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler { onBack() }

    var searchOpen by remember { mutableStateOf(false) }

    // Меню активируется явным «влево» с категорий и принимает фокус только тогда — тот же
    // механизм, что на «Телеканалах». Самопроизвольно фокус в него больше не попадает:
    // запрет фокуса стоит в пункте меню перед clickable.
    var sidebarFocused by remember { mutableStateOf(false) }
    var inCategories by remember { mutableStateOf(false) }
    // Категория, к которой должен уйти фокус после загрузки (id). Ждём именно её данные:
    // на старом содержимом ставить фокус бессмысленно.
    var pendingCategory by remember { mutableStateOf<Int?>(null) }
    val focusTargets = rememberShellFocusTargets()
    val contentState = state as? VodCatalogUiState.Content
    LaunchedEffect(
        pendingCategory,
        contentState?.library?.selectedCategoryId?.value,
        contentState?.library?.titles?.firstOrNull()?.id,
    ) {
        val pending = pendingCategory ?: return@LaunchedEffect
        val loaded = contentState ?: return@LaunchedEffect
        if (loaded.library.selectedCategoryId?.value != pending) return@LaunchedEffect
        if (loaded.library.titles.isEmpty()) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            if (runCatching { focusTargets.list.requestFocus() }.isSuccess) {
                pendingCategory = null
                return@LaunchedEffect
            }
            delay(60)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> when {
                        sidebarFocused -> false
                        inCategories -> {
                            sidebarFocused = true
                            true
                        }
                        else -> false
                    }
                    Key.DirectionRight -> when {
                        sidebarFocused -> {
                            sidebarFocused = false
                            runCatching { focusTargets.categories.requestFocus() }
                            true
                        }
                        inCategories -> runCatching {
                            focusTargets.list.requestFocus()
                        }.isSuccess
                        else -> false
                    }
                    else -> false
                }
            },
    ) {
    AppShell(
        destination = ShellDestination.MEDIATEKA,
        sidebarFocused = sidebarFocused,
        menuFocusRequester = focusTargets.menu,
        sidebarRightFocus = focusTargets.categories,
        onSelect = { destination ->
            when (destination) {
                ShellDestination.MEDIATEKA -> Unit
                ShellDestination.SETTINGS -> onOpenSettings()
                else -> onOpenSection(destination)
            }
        },
        // Поиск в медиатеке — только по фильмам и сериалам портала, отдельно от поиска каналов.
        onSearch = { searchOpen = true },
    ) {
        when (val current = state) {
            VodCatalogUiState.Loading -> VodCenteredMessage("Загружаем медиатеку…")

            VodCatalogUiState.NotConfigured -> VodMessageWithAction(
                message = "Медиатека не подключена. Укажите адрес и ключ портала в настройках.",
                actionLabel = "В настройки",
                onAction = onOpenSettings,
            )

            is VodCatalogUiState.Error -> VodMessageWithAction(
                message = current.reason,
                actionLabel = "Повторить",
                onAction = viewModel::reload,
            )

            is VodCatalogUiState.Content -> VodCatalogContent(
                categoriesFocus = focusTargets.categories,
                cardsFocus = focusTargets.list,
                focusGrid = false,
                onCategoryFocus = { inCategories = true },
                onGridFocus = { inCategories = false },
                onGridFocused = {},
                content = current,
                onSelectCategory = { id ->
                    viewModel.selectCategory(id)
                    // Категория выбрана по ОК — фокус уйдёт в её сетку, когда она загрузится.
                    pendingCategory = id.value
                },
                onFocusTitle = viewModel::focusTitle,
                onLoadMore = viewModel::loadMore,
                onOpenTitle = onOpenTitle,
                onWatch = onWatch,
            )
        }
    }

        if (searchOpen) {
            VodSearchOverlay(
                onDismiss = { searchOpen = false },
                onOpenTitle = { titleId ->
                    searchOpen = false
                    onOpenTitle(titleId)
                },
            )
        }
    }
}

/**
 * Содержимое экрана: три области в ряд.
 *
 * @param content состояние каталога;
 * @param onSelectCategory выбор категории;
 * @param onFocusTitle выделение карточки;
 * @param onLoadMore запрос следующей страницы;
 * @param onOpenTitle открыть карточку;
 * @param onWatch запустить карточку.
 */
@Composable
private fun VodCatalogContent(
    categoriesFocus: FocusRequester,
    cardsFocus: FocusRequester,
    focusGrid: Boolean,
    onCategoryFocus: () -> Unit,
    onGridFocus: () -> Unit,
    onGridFocused: () -> Unit,
    content: VodCatalogUiState.Content,
    onSelectCategory: (com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId) -> Unit,
    onFocusTitle: (VodTitle) -> Unit,
    onLoadMore: () -> Unit,
    onOpenTitle: (VodTitleId) -> Unit,
    onWatch: (VodTitle) -> Unit,
) {
    // Возврат из карточки: фокус встаёт на тот фильм, с которого ушли.
    val selectedCardFocus = remember { FocusRequester() }
    // Запоминаем выбор на момент входа в экран: дальше он меняется при движении по сетке,
    // и повторные заявки на фокус дёргали бы его обратно.
    val restoredSelection = remember { content.selected }
    LaunchedEffect(Unit) {
        val target = restoredSelection ?: return@LaunchedEffect
        if (content.library.titles.isEmpty()) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            if (runCatching { selectedCardFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }

    // Вход в раздел — на первую категорию; выбор категории по ОК — на её сетку.
    // Ключ — первая категория: при входе данных ещё нет, и заявка на фокус уходила в пустоту.
    LaunchedEffect(content.library.categories.firstOrNull()?.id) {
        if (content.library.categories.isEmpty()) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            if (runCatching { categoriesFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(60)
        }
    }
    // Фокус в сетку после выбора категории: повторяем заявку, пока карточки не скомпонованы —
    // сетка перерисовывается после загрузки новой категории.
    LaunchedEffect(focusGrid, content.library.titles.firstOrNull()?.id) {
        if (!focusGrid) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            if (runCatching { cardsFocus.requestFocus() }.isSuccess) {
                onGridFocused()
                return@LaunchedEffect
            }
            delay(60)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TvDimens.headerBottom),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VodCategoryColumn(
            categories = content.library.categories,
            selectedId = content.library.selectedCategoryId?.value,
            onSelect = onSelectCategory,
            modifier = Modifier.width(CATEGORY_COLUMN_WIDTH),
            firstFocus = categoriesFocus,
            rightFocus = cardsFocus,
            onCategoryFocus = onCategoryFocus,
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                content.library.isEmpty && content.loadingMore -> VodCenteredMessage("Загружаем фильмы…")
                content.library.isEmpty -> VodCenteredMessage("В этой категории пока ничего нет")
                else -> VodPosterGrid(
                    firstFocus = cardsFocus,
                    focusCardId = restoredSelection?.id?.value,
                    focusCardRequester = selectedCardFocus,
                    onCardFocus = onGridFocus,
                    titles = content.library.titles,
                    loadingMore = content.loadingMore,
                    onFocusTitle = onFocusTitle,
                    onLoadMore = onLoadMore,
                    onOpenTitle = onOpenTitle,
                    onWatch = onWatch,
                )
            }
        }

        VodPreviewPanel(
            title = content.selected ?: content.library.titles.firstOrNull(),
            onOpenTitle = onOpenTitle,
            onWatch = onWatch,
            modifier = Modifier.width(PREVIEW_WIDTH),
        )
    }
}

/** Колонка категорий: строки как в «Телеканалах», выбранная выделена. */
@Composable
private fun VodCategoryColumn(
    categories: List<VodCategory>,
    selectedId: Int?,
    onSelect: (com.mops1k.smotrishka.tv.core.vod.domain.model.VodCategoryId) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester? = null,
    rightFocus: FocusRequester? = null,
    onCategoryFocus: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(TvDimens.categoryGap),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        itemsIndexed(categories, key = { _, category -> category.id.value }) { index, category ->
            var focused by remember { mutableStateOf(false) }
            val active = category.id.value == selectedId
            val shape = RoundedCornerShape(TvRadius.item)
            Row(
                modifier = Modifier
                    .then(
                        if (index == 0 && firstFocus != null) {
                            Modifier.focusRequester(firstFocus)
                        } else {
                            Modifier
                        },
                    )
                    .then(
                        if (rightFocus != null) {
                            Modifier.focusProperties { right = rightFocus }
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxWidth()
                    .heightIn(min = TvDimens.menuItemMinHeight)
                    .clip(shape)
                    .background(
                        when {
                            focused -> TvColors.panelSoft
                            active -> TvColors.categoryActiveBg
                            else -> TvColors.panel
                        },
                    )
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onCategoryFocus()
                    }
                    .tvFocusRing(focused = focused, shape = shape)
                    .clickable { onSelect(category.id) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = category.title,
                    style = TvType.category,
                    color = when {
                        focused -> TvColors.focusOutline
                        active -> TvColors.categoryActiveText
                        else -> TvColors.categoryIdle
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Сетка постеров с автодогрузкой у конца списка. */
@Composable
private fun VodPosterGrid(
    firstFocus: FocusRequester? = null,
    focusCardId: Int? = null,
    focusCardRequester: FocusRequester? = null,
    onCardFocus: () -> Unit = {},
    titles: List<VodTitle>,
    loadingMore: Boolean,
    onFocusTitle: (VodTitle) -> Unit,
    onLoadMore: () -> Unit,
    onOpenTitle: (VodTitleId) -> Unit,
    onWatch: (VodTitle) -> Unit,
) {
    val gridState = rememberLazyGridState()

    // Подгружаем заранее, за ряд до конца: фокус не должен упираться в пустоту.
    LaunchedEffect(gridState, titles.size, loadingMore) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                val threshold = titles.lastIndex - PREFETCH_ROWS
                if (!loadingMore && lastVisible >= threshold) onLoadMore()
            }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = POSTER_MIN_WIDTH),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        // Отступ сверху и слева: кольцо фокуса рисуется вокруг карточки и без запаса
        // срезается краем сетки у первого ряда.
        contentPadding = PaddingValues(start = 6.dp, top = 6.dp, end = 8.dp, bottom = 16.dp),
    ) {
        items(titles, key = { it.id.value }) { title ->
            VodPosterCard(
                title = title,
                onFocus = {
                    onCardFocus()
                    onFocusTitle(title)
                },
                onOpen = { onOpenTitle(title.id) },
                onWatch = { onWatch(title) },
                modifier = Modifier
                    .then(
                        when {
                            focusCardId != null && title.id.value == focusCardId &&
                                focusCardRequester != null -> Modifier.focusRequester(focusCardRequester)
                            title.id == titles.first().id && firstFocus != null ->
                                Modifier.focusRequester(firstFocus)
                            else -> Modifier
                        },
                    ),
            )
        }
    }
}

/** Постер в сетке: картинка, название и метки качества; фокус ведёт превью. */
@Composable
private fun VodPosterCard(
    title: VodTitle,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
    onWatch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember(title.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.control)
    Column(
        modifier = modifier
            // Рамка рисуется до клипа: иначе клип срезает её наружную половину и фокус
            // почти не виден.
            .tvFocusRing(focused = focused, shape = shape)
            .clip(shape)
            .background(if (focused) TvColors.panelSoft else TvColors.panel)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable { onOpen() }
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT)
                .clip(RoundedCornerShape(TvRadius.control))
                .background(TvColors.progressTrack),
        ) {
            if (title.gridPosterUrl != null) {
                AsyncImage(
                    model = title.gridPosterUrl,
                    contentDescription = title.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = title.title,
            style = TvType.caption,
            color = if (focused) TvColors.text else TvColors.rowTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (title.is4k || title.isFhd || title.isHdr) {
            Text(
                text = listOfNotNull(
                    "4K".takeIf { title.is4k },
                    "FHD".takeIf { title.isFhd },
                    "HDR".takeIf { title.isHdr },
                ).joinToString(" · "),
                style = TvType.badge,
                color = TvColors.accent,
            )
        }
    }
}

/** Панель превью выделенной карточки: постер, год, описание и действия. */
@Composable
private fun VodPreviewPanel(
    title: VodTitle?,
    onOpenTitle: (VodTitleId) -> Unit,
    onWatch: (VodTitle) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (title == null) return

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(TvRadius.panel))
            .background(TvColors.panel)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT)
                .clip(RoundedCornerShape(TvRadius.control))
                .background(TvColors.progressTrack),
        ) {
            if (title.gridPosterUrl != null) {
                AsyncImage(
                    model = title.gridPosterUrl,
                    contentDescription = title.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = title.title,
            style = TvType.panelTitle,
            color = TvColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(
                title.year?.toString(),
                "4K".takeIf { title.is4k },
                "FHD".takeIf { title.isFhd },
                "HDR".takeIf { title.isHdr },
            ).joinToString(" · "),
            style = TvType.eyebrow,
            color = TvColors.accent,
        )
        // Действия идут сразу после года и качества одним рядом: кнопки делят ширину панели
        // пополам, поэтому размер у них одинаковый, а стиль — общий с превью канала.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TvWatchButton(onClick = { onWatch(title) })
            }
            Box(modifier = Modifier.weight(1f)) {
                TvSecondaryButton(
                    onClick = { onOpenTitle(title.id) },
                    label = "Инфо",
                    icon = Icons.Filled.Info,
                )
            }
        }
        // Описание берём в локальную переменную: свойство из другого модуля не поддаётся
        // smart cast, а проверять его дважды не нужно.
        val description = title.description
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = TvType.caption,
                color = TvColors.muted,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Сообщение по центру области. */
@Composable
private fun VodCenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, style = TvType.panelTitle, color = TvColors.muted)
    }
}

/** Сообщение с одной кнопкой действия: ошибка и «Повторить», нет подключения и «В настройки». */
@Composable
private fun VodMessageWithAction(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, style = TvType.panelTitle, color = TvColors.muted)
        Spacer(Modifier.size(14.dp))
        Button(onClick = onAction) {
            Text(actionLabel, style = TvType.watchButton)
        }
    }
}

/** Ширина колонки категорий. */
private val CATEGORY_COLUMN_WIDTH = 210.dp

/** Ширина панели превью. */
private val PREVIEW_WIDTH = 300.dp

/** Минимальная ширина постера в сетке. */
private val POSTER_MIN_WIDTH = 124.dp

/** Пропорция постера: как у плакатов провайдера. */
private const val POSTER_ASPECT = 0.68f

/** За сколько карточек до конца просить следующую страницу. */
private const val PREFETCH_ROWS = 8
