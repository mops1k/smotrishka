package com.mops1k.smotrishka.tv.ui.home.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mops1k.smotrishka.tv.core.designsystem.component.TvPanel
import com.mops1k.smotrishka.tv.core.designsystem.component.TvScrollbar
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvColors
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvType
import com.mops1k.smotrishka.tv.ui.common.TextFormat
import com.mops1k.smotrishka.tv.ui.home.ChannelUi
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
 * @param onSelect выбор канала;
 * @param onShowProgramme открыть телепрограмму канала;
 * @param modifier размеры панели.
 */
@Composable
fun ChannelListPanel(
    title: String?,
    channels: List<ChannelUi>,
    selectedChannelId: com.mops1k.smotrishka.tv.core.model.ChannelId?,
    now: Instant,
    zone: ZoneId,
    showLogos: Boolean,
    archiveVerified: Set<String>,
    unstable: Set<String>,
    emptyTitle: String,
    emptySubtitle: String,
    onSelect: (com.mops1k.smotrishka.tv.core.model.ChannelId) -> Unit,
    onShowProgramme: () -> Unit,
    narrow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    TvPanel(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (title != null) {
                ListHeader(title = title, count = channels.size)
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
                    onSelect = onSelect,
                    onShowProgramme = onShowProgramme,
                    narrow = narrow,
                )
            }
        }
    }
}

/**
 * Шапка панели: высота 69, имя категории 17/600 и счётчик 10 со склонением.
 *
 * @param title имя категории;
 * @param count число каналов в списке.
 */
@Composable
private fun ListHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TvDimens.listHeaderHeight)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.Start),
    ) {
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
 * @param onSelect выбор канала;
 * @param onShowProgramme открыть телепрограмму;
 * @param narrow узкий режим: блок времени в строке скрыт.
 */
@Composable
private fun ChannelList(
    channels: List<ChannelUi>,
    selectedChannelId: com.mops1k.smotrishka.tv.core.model.ChannelId?,
    now: Instant,
    zone: ZoneId,
    showLogos: Boolean,
    archiveVerified: Set<String>,
    unstable: Set<String>,
    onSelect: (com.mops1k.smotrishka.tv.core.model.ChannelId) -> Unit,
    onShowProgramme: () -> Unit,
    narrow: Boolean,
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val selectedIndex = channels.indexOfFirst { it.channel.id == selectedChannelId }

    // Список прокручивается к выбранному каналу: после возврата из плеера видно, где были.
    LaunchedEffect(selectedChannelId, channels.size) {
        if (selectedIndex >= 0) listState.scrollToItem(selectedIndex)
    }

    // Пульт остаётся на канале, который смотрели: иначе после Back из плеера фокус улетал
    // на первый элемент (проверено на устройстве 05.11.2026).
    var focusRestored by remember(channels.size) { mutableStateOf(false) }
    LaunchedEffect(channels.size) {
        if (focusRestored) return@LaunchedEffect
        if (selectedIndex >= 0) listState.scrollToItem(selectedIndex)
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
            items(items = channels, key = { it.channel.id.value }) { item ->
                ChannelRow(
                    item = item,
                    selected = item.channel.id == selectedChannelId,
                    now = now,
                    zone = zone,
                    showLogo = showLogos,
                    // Заявленный архив показываем сразу; после проверки — только подтверждённые.
                    hasArchive = item.channel.archive != null &&
                        (archiveVerified.isEmpty() || item.channel.id.value in archiveVerified),
                    hasUnstable = item.channel.id.value in unstable,
                    onClick = { onSelect(item.channel.id) },
                    onFocused = { onSelect(item.channel.id) },
                    onShowProgramme = onShowProgramme,
                    narrow = narrow,
                    focusRequester = if (item.channel.id == selectedChannelId) focusRequester else null,
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
