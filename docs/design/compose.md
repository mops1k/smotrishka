# Маппинг на Compose

Как блоки макета ложатся на приложение (Compose, Material3, TV). Ориентир — существующие
экраны `PlayerScreen.kt` и `SettingsScreen.kt`: они уже сделаны вручную, без TV-библиотеки.

## Общие правила переноса

- Числа из [`tokens.md`](tokens.md) берутся как `dp`/`sp` (1 px макета ≈ 1 dp при 1080p).
  Округление допускается вверх на 1–2 dp, если элемент «прилипает» на ТВ.
- Цвета — в `Color(0xFF…)`; полупрозрачности — `Color.White.copy(alpha = …)`.
- Градиенты — `Brush.linearGradient` / `Brush.radialGradient` в `Modifier.background`.
- `backdrop-filter` (размытие подложки) в Compose напрямую недоступен: заменяется на
  полупрозрачный фон без размытия либо `graphicsLayer`+`RenderEffect` (Android 12+) — для ТВ
  достаточно полупрозрачного фона.
- Скругления — `RoundedCornerShape`, обводки — `Modifier.border(1.dp, color, shape)`.
- Текст — `MaterialTheme.typography` с точечными `fontSize`/`fontWeight`, как в
  `SettingsScreen`; для крупных размеров задавать `fontSize` вручную.
- Обрезка длинных строк — `maxLines = 1, overflow = TextOverflow.Ellipsis`.

## Блоки

| Блок макета | Compose |
|---|---|
| онбординг целиком | подробная таблица — в [`onboarding.md`](onboarding.md#маппинг-на-compose): карточка `Surface` + `Row` 1f/256.dp, чипы `FlowRow`+`FilterChip`, поля `OutlinedTextField`, плашка файла `rememberLauncherForActivityResult(OpenDocument)`, загрузка `CircularProgressIndicator`, ошибка `Text(color = colorScheme.error)` |
| каркас `.app-shell` | `Row(Modifier.fillMaxSize())` + фон `Box` с radial-градиентом |
| сайдбар | `Column(Modifier.width(236.dp))` на `Surface` с бордером справа |
| бренд | `Row` + `Box` 30.dp с бордером, `rotate(-8f)` через `graphicsLayer`, `Text` 19.sp/700 |
| пункт меню | `Surface(shape = RoundedCornerShape(13.dp))` + `Row` (`Icon` 22.dp, `Text` 14.sp/600), `Modifier.focusable()`; активный — градиент + `drawBehind` с полосой 3.dp слева |
| шапка контента | `Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = SpaceBetween)`; h1 29.sp/600 |
| кнопка поиска | `IconButton` в `Surface` 45.dp, радиус 13.dp |
| часы | `Column(horizontalAlignment = Alignment.End)` |
| плашка категорий | `LazyRow(horizontalArrangement = spacedBy(8.dp))` с чипами `Surface(shape = RoundedCornerShape(11.dp))`; активный — светлый фон `#f0ecfa` и тёмный текст |
| сетка «список + карточка» | `Row(Modifier.fillMaxSize())` с `weight(1.15f)` и `weight(.85f)`, зазор `spacedBy(25.dp)`; в развороте телепрограммы веса меняются на `.68f`/`1.32f` |
| панель списка | `Column` на `Surface(border = 1.dp var(--line), shape = RoundedCornerShape(20.dp))` |
| шапка списка | `Row(Modifier.height(69.dp))` с `Text` 17.sp/600 и счётчиком 10.sp |
| список каналов | `LazyColumn(Modifier.padding(9.dp))` с `key = { it.id }` и `rememberLazyListState()` |
| строка канала | `Row(Modifier.heightIn(min = 67.dp).padding(7.dp, 11.dp))` и фиксированные слоты: логотип `width(43.dp)`, имя `weight(1f)`, бейджи `width(92.dp)` (правое выравнивание), время `width(105.dp)`, шеврон `width(18.dp)`. **Пустой слот бейджей обязателен** — иначе съезжает время |
| логотип канала | `Box(39.dp, RoundedCornerShape(11.dp))` с буквами 11.sp/800; крупный — 94.dp/26.dp/27.sp |
| бейдж «Архив» | `Row` в `Surface(RoundedCornerShape(6.dp), border 1.dp)` + `Icon(11.dp)` + `Text(8.sp/700)`, `padding(3.dp, 6.dp)` |
| время и прогресс | `Column(horizontalAlignment = Alignment.End)`: `Text` 9.sp, ниже `Box(width 83.dp, height 3.dp)` с `fillMaxWidth(fraction)` |
| пустое состояние | `Column` по центру, `min-height 290.dp` |
| карточка канала: визуал | `Box(heightIn(min = 230.dp))` с радиальными градиентами, сеткой (`drawBehind`), двумя размытыми кругами (`graphicsLayer`+`RenderEffect` либо `Brush.radialGradient`) и логотипом 94.dp |
| `HD`, «Сейчас в эфире» | `Surface`/`Row` с полупрозрачным фоном, абсолютно в `Box` через `align` |
| блок «Сейчас» | `Row` (48.dp | weight(1f) | auto) + `Box` 3.dp с градиентной заливкой |
| «Телепрограмма канала» | `Surface`/`Button` `heightIn(min = 44.dp)`, `border 1.dp`, `RoundedCornerShape(12.dp)`, `Row` с иконкой списка, текстом 11.sp/600 и шевроном справа |
| «Смотреть» | `Box(Modifier.fillMaxWidth().heightIn(min = 49.dp).background(Brush.linearGradient(...), RoundedCornerShape(13.dp)).focusable().clickable { … })` с `Row` внутри (круг 28.dp + текст 13.sp/700) |
| избранное | `IconToggleButton` либо `Box` 39.dp с сердцем: `#777e8d` / `#e76983` + заливка |
| разворот телепрограммы | тот же `Row` с другими весами; внутри правой панели — `Column` с шапкой и `LazyColumn` строк |
| строка передачи | `Row(Modifier.heightIn(min = 42.dp))` со слотами 76.dp | weight(1f) | auto; текущая — градиентный фон и бордер |
| карточка настроек | `Surface(border 1.dp, RoundedCornerShape(20.dp))` + `Column(Modifier.padding(23.dp))` |
| строка настройки | `Column` c `Row` (название `weight(1f)`, значение, действие) и подсказкой ниже — как `SettingsRow` в `SettingsScreen.kt` |
| группа вариантов | `Row(horizontalArrangement = spacedBy(7.dp))` с чипами `Surface(RoundedCornerShape(10.dp))`, `heightIn(min = 40.dp)`; активный — рамка `#a481f3` и фон `rgba(148,108,238,.16)`; при 5+ вариантах — `FlowRow` или два `Row` |
| переключатель | `Switch` (Material3) либо ручной `Box` 39×22 с `animateDpAsState` |
| список гидов | `Column(spacedBy(7.dp))` с `Surface(#0d1118, RoundedCornerShape(11.dp))` на каждой строке |
| диалог гида | `Dialog(onDismissRequest = …)` с `usePlatformDefaultWidth = false`, внутри `Surface` 520.dp |
| поиск | `Dialog` с `Modifier.fillMaxSize()` и `Column(horizontalAlignment = CenterHorizontally)`; `BasicTextField` + `FocusRequester`, фокус в поле при открытии |
| тост | `Snackbar`/`Surface` в `Box` снизу по центру, скрытие через `LaunchedEffect` 2.4 с |
| плеер | `Box(Modifier.fillMaxSize().background(Color.Black))` + `AndroidView(factory = { PlayerView(it) })` (как в `PlayerScreen`) + OSD в `AnimatedVisibility(visible = osdVisible, fadeIn/fadeOut)` |
| OSD верх | `Row` в `Column(Modifier.fillMaxSize())` с `verticalArrangement = SpaceBetween` и скрим-градиентами фоном |
| OSD низ | `Column(Modifier.padding(horizontal = 56.dp))` |
| полоса перемотки | `Slider` со скрытой ручкой либо `Box` 4.dp + `fillMaxWidth(fraction)`, `onSeek` — как `ProgrammeSeekBar` |
| ряд управления | `Row(horizontalArrangement = Center, spacedBy(26.dp))`; главная кнопка — `FilledIconButton` 56.dp с белым контейнером и тёмной иконкой |
| боковые стрелки | `FilledIconButton` 46.dp в `Box` с `align(BottomStart/BottomEnd)` и `padding(bottom = 148.dp)` |
| панели плеера | `Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f)))` + `Surface` 900.dp по центру (скругление 20.dp, бордер) |
| фокус плеера/панели | `FocusRequester` на текущей передаче при открытии программы + `LazyListState.scrollToItem` |
| подсказка режима кадра | `Surface` по центру, время показа через `LaunchedEffect` 1.6 с |

## Навигация и фокус

В приложении фокус считается вручную (`keydown`-подход макета — его веб-аналог). Требования:

1. Каждый интерактивный элемент — `Modifier.focusable()` + видимая рамка фокуса
   (`outline 3.dp` цвета `#d1bcff`, свечение снаружи).
2. Стрелки двигают фокус к геометрически ближайшему элементу в нужную сторону
   (вес = расстояние по оси + 2.4 × поперечное), как в макете и как уже сделано в
   `PlayerScreen`.
3. В OSD плеера Up/Down переключают канал, если OSD-навигация не активна.
4. Панель плеера держит фокус внутри себя до закрытия.

## Соответствие реальным данным приложения

| Данные макета | Где в приложении |
|---|---|
| канал: `id`, `name`, `category`, `now`, `next`, `time`, `progress` | `Channel` из `:core:model`, текущая и следующая передача — из `EpgGuide`/программы |
| `archiveDays` (глубина архива канала) | признак архива у канала (`archiveAvailable`) + настройка «Глубина архива»; в макете глубина на канал — упрощение |
| `color`, `dark`, `logo` | в приложении нет логотипов-картинок: плитка с буквами, цвет генерируется по каналу |
| категории каналов | категории плейлиста |
| избранное | локальное хранилище (в приложении есть `favorites` в плеере) |
| настройки | `PlayerSettingsStore`: `BUFFER_CHOICES 2/5/10/20` (умолч. 5), `VideoQuality` Авто/360p/480p/720p/1080p, `RETRY_CHOICES 3/5/8` (умолч. 5), `EPG_WINDOW_CHOICES 12/24/48/72/96/120/144/168` (умолч. 24), `ARCHIVE_DEPTH_CHOICES 24…168` (умолч. 72), `AudioLanguage` Русский/Английский/Оригинал, `dataSaver`, `AspectMode`, `directStream` |
| гиды XMLTV | `EpgGuide(name, url)`, репозиторий гидов |
| «Источник: iedem.tv · логин 4821…» | `SettingsUiState.Content(sourceLabel, sourceDetail)` |
| «Плейлист обновлён …» | статус из `SettingsViewModel.status` |
| статистика плеера | `PlayerStats.describe(height, bitrate, bufferMs)` |
| режим кадра | `AspectModes.ORDER` + `AspectModes.label` |
| «Сейчас в эфире» / «АРХИВ» | `channel.isArchive`, `programmeStart/End` |

Чего в приложении пока нет и что потребует кода:

1. **История просмотров** для «Недавних» (и раздела, и панели плеера).
2. **Обложки/логотипы каналов** — макет довольствуется цветными плитками, приложение тоже.
3. **Экраны ошибок плеера** — нарисовать по мотивам `UnavailableStream` и ошибки архива.

## Ограничения переноса

- Веб-эффекты (`backdrop-filter`, `box-shadow` с большим blur) на ТВ выглядят иначе —
  проверять на устройстве.
- Спейшиал-навигация макета — не эталон: в приложении уже есть своя и её нужно сохранить.
- Мок-данные макета (8 каналов) не отражают объём реального плейлиста (тысячи каналов):
  списки обязаны быть ленивыми, без полного прохода по всем элементам.
