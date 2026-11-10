package com.mops1k.smotrishka.tv.ui.shell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.mops1k.smotrishka.tv.core.designsystem.component.TvAppBackground
import com.mops1k.smotrishka.tv.core.designsystem.theme.TvDimens
import com.mops1k.smotrishka.tv.ui.vod.VodAvailabilityViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/** Порог узкого режима: при меньшей ширине сайдбар сворачивается в иконки. */
private val NARROW_WIDTH = 980.dp

/**
 * Признак узкого режима для содержимого раздела: экраны выбирают компактную раскладку,
 * когда каркас свёрнут (телефон, планшет в портрете, маленькая приставка).
 */
val LocalNarrowLayout = staticCompositionLocalOf { false }

/**
 * Каркас приложения: сайдбар, шапка раздела и содержимое.
 *
 * Пункт «Медиатека» каркас решает сам — по проверенному подключению портала. Флаг не передаётся
 * от экрана к экрану: иначе любой новый экран мог бы забыть его передать, и пункт пропадал бы.
 *
 * @param destination текущий раздел;
 * @param onSelect выбор раздела;
 * @param onSearch открыть поиск, `null` — поиска нет (например, в плеере каркаса нет вовсе);
 * @param modifier размеры каркаса;
 * @param content содержимое раздела.
 */
@Composable
fun AppShell(
    destination: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    onSearch: (() -> Unit)?,
    modifier: Modifier = Modifier,
    // Меню принимает фокус только по явному «влево»; иначе вверх/вниз уводили в меню.
    sidebarFocused: Boolean = false,
    // Адрес фокуса текущего пункта меню: по нему «влево» с края контента попадает в меню.
    menuFocusRequester: FocusRequester? = null,
    // Куда ведёт «вправо» из меню: видимая панель контента.
    sidebarRightFocus: FocusRequester? = null,
    // «Влево» с кнопки поиска открывает боковое меню.
    onSearchLeftEdge: (() -> Unit)? = null,
    // Адрес кнопки поиска: на неё ведёт «вверх» из карточки канала.
    searchFocusRequester: FocusRequester? = null,
    // «Вниз» с поиска ведёт на превью карточки: между шапкой и карточкой не должно
    // быть прыжков в пустоту.
    searchDownFocusRequester: FocusRequester? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val now = rememberClock()
    val availability: VodAvailabilityViewModel = hiltViewModel()
    val showMediateka by availability.connected.collectAsStateWithLifecycle()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val narrow = maxWidth < NARROW_WIDTH
        TvAppBackground(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
                AppSidebar(
                    current = destination,
                    onSelect = onSelect,
                    narrow = narrow,
                    showMediateka = showMediateka,
                    focusEnabled = sidebarFocused,
                    menuFocusRequester = menuFocusRequester,
                    rightFocus = sidebarRightFocus,
                    updateAvailable = LocalUpdateBadge.current,
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = if (narrow) 14.dp else TvDimens.contentHorizontal,
                            end = if (narrow) 14.dp else TvDimens.contentHorizontal,
                            top = if (narrow) 12.dp else TvDimens.contentTop,
                            bottom = if (narrow) 14.dp else TvDimens.contentBottom,
                        ),
                ) {
                    ContentHeader(
                        title = destination.title,
                        now = now,
                        zone = ZoneId.systemDefault(),
                        onSearch = onSearch,
                        narrow = narrow,
                        onSearchLeftEdge = onSearchLeftEdge,
                        searchFocusRequester = searchFocusRequester,
                        searchDownFocusRequester = searchDownFocusRequester,
                    )
                    CompositionLocalProvider(LocalNarrowLayout provides narrow) {
                        content()
                    }
                }
            }
        }
    }
}

/**
 * Текущий момент, обновляемый раз в минуту: часы в шапке не должны отставать.
 *
 * @param clock источник времени (подменяется в тестах);
 * @param zone часовой пояс пользователя.
 */
@Composable
fun rememberClock(
    clock: Clock = Clock.systemDefaultZone(),
    zone: ZoneId = ZoneId.systemDefault(),
): Instant {
    var now by remember { mutableStateOf(clock.instant()) }
    LaunchedEffect(clock) {
        while (true) {
            val current = clock.instant()
            now = current
            // Ждём до начала следующей минуты: обновление ровно по границе минуты.
            val millisToNextMinute = 60_000L - current.toEpochMilli() % 60_000L
            delay(millisToNextMinute.coerceIn(1_000L, 60_000L))
        }
    }
    return now
}
