package com.mops1k.smotrishka.tv.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mops1k.smotrishka.tv.core.designsystem.component.TvPanel
import com.mops1k.smotrishka.tv.core.designsystem.component.TvScrollbar
import com.mops1k.smotrishka.tv.core.designsystem.component.tvFocusRing
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvRadius
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.common.TextFormat
import com.mops1k.smotrishka.tv.ui.home.ChannelUi
import com.mops1k.smotrishka.tv.ui.home.ListCentering
import java.time.Instant
import java.time.ZoneId

/**
 * Панель списка каналов: шапка (только в «Телеканалах»), ленивый список и пустое состояние.
 *
 * @param title имя категории для шапки, `null` — шапки нет (разделы «Избранное» и «Недавние»);
 * @param channels каналы после фильтров;
 * @param selectedChannelId выбранный канал;
 * @param now текущий момент;
 * @param zone часовой пояс;
 * @param showLogos грузить ли логотипы;
 * @param archiveVerified каналы с проверенным архивом;
 * @param unstable каналы с нестабильным потоком;
 * @param emptyTitle заголовок пустого состояния;
 * @param emptySubtitle подпись пустого состояния;
 * @param onSelect выбор канала (наведение фокуса, превью);
 * @param onOpen открытие канала в плеере (только OK);
 * @param onShowProgramme открыть телепрограмму канала;
 * @param modifier размеры панели;
 * @param listKey что за список показан (раздел, категория, запрос).
 */
@Composable
fun ChannelListPanel(
    title: String?,
    channels: List<ChannelUi>,
    selectedChannelId: com.mops1k.smotrishka.tv.core.model.ChannelId?,
    playingChannelId: com.mops1k.smotrishka.tv.core.model.ChannelId? = null,
    epgPending: Boolean = false,
    now: Instant,
    zone: ZoneId,
    showLogos: Boolean,
    archiveVerified: Set<String>,
    unstable: Set<String>,
    emptyTitle: String,
    emptySubtitle: String,
    onSelect: (com.mops1k.smotrishka.tv.core.model.ChannelId) -> Unit,
    onOpen: (com.mops1k.smotrishka.tv.core.model.ChannelId) -> Unit = onSelect,
    onShowProgramme: () -> Unit,
    narrow: Boolean = false,
    modifier: Modifier = Modifier,
    // Куда уходит фокус по «влево» из строки списка: категории либо меню.
    leftTarget: FocusRequester? = null,
    // Адрес для внешнего перевода фокуса в список (из карточки по «влево»).
    rowFocusRequester: FocusRequester? = null,
    // Нажатие «влево», когда категорий нет: активирует боковое меню.
    onActivateMenu: (() -> Unit)? = null,
    // Сообщает наверх, что фокус внутри списка: по нему экран решает, куда ведёт «влево».
    onFocusInList: (Boolean) -> Unit = {},
    // Что за список показан (раздел, категория, запрос): по нему сбрасываются прокрутка и фокус.
    listKey: String,
    // «←» к списку категорий: на тач-экране системной кнопки «назад» нет.
    onBackToCategories: (() -> Unit)? = null,
) {
    TvPanel(
        modifier = modifier.onFocusChanged { onFocusInList(it.hasFocus) },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (title != null) {
                ListHeader(title = title, count = channels.size, onBack = onBackToCategories)
            }
            if (channels.isEmpty()) {
                EmptyState(title = emptyTitle, subtitle = emptySubtitle)
            } else {
                ChannelList(
                    channels = channels,
                    selectedChannelId = selectedChannelId,
                    now = now,
                    zone = zone,
                    showLogos = showLogos,
                    archiveVerified = archiveVerified,
                    unstable = unstable,
                    leftTarget = leftTarget,
                    rowFocusRequester = rowFocusRequester,
                    onActivateMenu = onActivateMenu,
                    onSelect = onSelect,
                    playingChannelId = playingChannelId,
                    epgPending = epgPending,
                    onOpen = onOpen,
                    onShowProgramme = onShowProgramme,
                    narrow = narrow,
                    listKey = listKey,
                )
            }
        }
    }
}

/**
 * Кнопка «←» к списку категорий.
 *
 * На тач-экране без системных кнопок вернуться к категориям было нечем: панель категорий
 * открывалась только обработчиком клавиши «назад» (замечание Александра 09.11.2026).
 *
 * @param onClick открыть список категорий.
 */
@Composable
private fun BackToCategories(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(TvRadius.small)
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
            .tvFocusRing(focused = focused, shape = shape)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "←",
            style = TvType.panelTitle.copy(fontSize = 16.sp),
            color = if (focused) TvColors.accent else TvColors.muted,
        )
    }
}

/**
 * Шапка панели: высота 69, имя категории 17/600 и счётчик 10 со склонением.
 *
 * @param title имя категории;
 * @param count число каналов в списке;
 * @param onBack «←» к категориям, `null` — списка категорий нет (раздел, поиск).
 */
@Composable
private fun ListHeader(title: String, count: Int, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimens.listHeaderHeight)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.Start),
    ) {
        if (onBack != null) {
            BackToCategories(onBack)
        }
        Text(
            text = title,
            style = TvType.panelTitle.copy(fontSize = 15.sp),
            color = TvColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = TextFormat.channels(count),
            style = TvType.caption.copy(fontSize = 9.sp),
            color = TvColors.muted,
            maxLines = 1,
        )
        Spacer(Modifier.weight(1f))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TvColors.line),
    )
}

/**
 * Ленивый список каналов с тонким скроллбаром.
 *
 * @param channels каналы;
 * @param selectedChannelId выбранный канал;
 * @param now текущий момент;
 * @param zone часовой пояс;
 * @param showLogos грузить ли логотипы;
 * @param archiveVerified каналы с проверенным архивом;
 * @param unstable каналы с нестабильным потоком;
 * @param onSelect выбор канала (наведение фокуса, превью);
 * @param onOpen открытие канала в плеере (только OK);
 * @param onShowProgramme открыть телепрограмму;
 * @param narrow узкий режим: блок времени в строке скрыт;
 * @param listKey что за список показан: по нему сбрасываются прокрутка и фокус.
 */
@Composable
private fun ChannelList(
    channels: List<ChannelUi>,
    selectedChannelId: com.mops1k.smotrishka.tv.core.model.ChannelId?,
    playingChannelId: com.mops1k.smotrishka.tv.core.model.ChannelId?,
    epgPending: Boolean,
    now: Instant,
    zone: ZoneId,
    showLogos: Boolean,
    archiveVerified: Set<String>,
    unstable: Set<String>,
    onSelect: (com.mops1k.smotrishka.tv.core.model.ChannelId) -> Unit,
    onOpen: (com.mops1k.smotrishka.tv.core.model.ChannelId) -> Unit = onSelect,
    onShowProgramme: () -> Unit,
    narrow: Boolean,
    leftTarget: FocusRequester? = null,
    rowFocusRequester: FocusRequester? = null,
    onActivateMenu: (() -> Unit)? = null,
    listKey: String = "",
) {
    // Список пересоздаётся при смене раздела, категории или запроса: иначе позиция прокрутки
    // и «фокус уже восстановлен» переезжали из одного списка в другой, и строки в «Избранном»
    // листались без центрирования.
    val listState = remember(listKey) { LazyListState() }
    val internalRequester = remember { FocusRequester() }
    val focusRequester = rowFocusRequester ?: internalRequester
    // true — фокус ставим на первую строку (выбранного канала в списке нет).
    var focusFirstRow by remember(listKey, channels.size) { mutableStateOf(false) }
    val selectedIndex = channels.indexOfFirst { it.channel.id == selectedChannelId }

    /** Индекс строки с фокусом: список центрируется по нему в одном месте. */
    var focusedIndex by remember(listKey) { mutableStateOf(-1) }

    /**
     * Ставит строку в середину видимой области.
     *
     * При листании фокус остаётся в центре, а список прокручивается под ним — так строка
     * не «прилипает» к краю и видно следующие каналы.
     */
    suspend fun centerOn(index: Int) {
        val rows = listState.layoutInfo.visibleItemsInfo.size.takeIf { it > 0 } ?: DEFAULT_VISIBLE_ROWS
        val target = ListCentering.targetIndex(index, rows) ?: return
        listState.scrollToItem(target)
    }

    // Один эффект на центрирование: и при переходе фокуса, и при возврате на выбранный канал.
    LaunchedEffect(focusedIndex, selectedChannelId, channels.size) {
        if (focusedIndex >= 0) {
            centerOn(focusedIndex)
        } else {
            centerOn(selectedIndex)
        }
    }

    // Пульт остаётся на канале, который смотрели: иначе после Back из плеера фокус улетал
    // на первый элемент (проверено на устройстве 05.11.2026).
    var focusRestored by remember(listKey, channels.size) { mutableStateOf(false) }
    LaunchedEffect(listKey, channels.size) {
        if (focusRestored) return@LaunchedEffect
        // Если выбранного канала нет в этом списке (сменилась категория), фокус ставится
        // на первую строку: иначе он оставался в левом меню, а список стоял без фокуса.
        val target = if (selectedIndex >= 0) selectedIndex else 0
        focusFirstRow = selectedIndex < 0
        centerOn(target)
        withFrameNanos { }
        runCatching { focusRequester.requestFocus() }
        focusRestored = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(TvDimens.listPadding)
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(items = channels, key = { _, item -> item.channel.id.value }) { index, item ->
                ChannelRow(
                    item = item,
                    selected = item.channel.id == selectedChannelId,
                    playing = item.channel.id == playingChannelId,
                    epgPending = epgPending,
                    now = now,
                    zone = zone,
                    showLogo = showLogos,
                    // Архив показываем так, как заявил плейлист: фоновая проверка давала
                    // ложные «нет архива» у каналов, где он есть (решение Александра
                    // 09.11.2026 — доверять плейлисту).
                    hasArchive = item.channel.archive != null,
                    hasUnstable = item.channel.id.value in unstable,
                    leftTarget = leftTarget,
                    onActivateMenu = onActivateMenu,
                    onClick = { onOpen(item.channel.id) },
                    onFocused = {
                        onSelect(item.channel.id)
                        // Фокус ушёл на строку — центрируем её одним эффектом выше.
                        focusedIndex = index
                    },
                    onShowProgramme = onShowProgramme,
                    narrow = narrow,
                    focusRequester = if (
                        (focusFirstRow && index == 0) || (!focusFirstRow && item.channel.id == selectedChannelId)
                    ) {
                        focusRequester
                    } else {
                        null
                    },
                )
            }
        }
        TvScrollbar(
            state = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(vertical = TvDimens.listPadding, horizontal = 2.dp),
        )
    }
}

/**
 * Пустое состояние списка: иконка поиска 34, заголовок 14 и подпись 10.
 *
 * @param title заголовок;
 * @param subtitle подпись.
 */
@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 290.dp)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = TvColors.emptyText,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = TvType.clock,
            color = TvColors.emptyText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = TvType.rowProgramme,
            color = TvColors.emptyText,
            textAlign = TextAlign.Center,
        )
    }
}

/** Сколько строк считать видимыми, пока список не разложен (для центрирования). */
private const val DEFAULT_VISIBLE_ROWS = 5
