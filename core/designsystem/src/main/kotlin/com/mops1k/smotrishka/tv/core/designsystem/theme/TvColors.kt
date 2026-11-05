package com.mops1k.smotrishka.tv.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Палитра редизайна: значения сняты из `docs/design/tokens.md` (CSS-переменные макета).
 *
 * Цвета заданы константами, а не через `MaterialTheme.colorScheme`: макет использует
 * больше оттенков, чем помещается в схему Material3 (бейджи, строки, панели плеера).
 */
object TvColors {

    /** Фон приложения. */
    val bg = Color(0xFF090C12)

    /** Поверхность кнопок-иконок и чипов в покое. */
    val panel = Color(0xFF11151E)

    /** Вторичная поверхность. */
    val panelSoft = Color(0xFF171C27)

    /** Все границы и разделители: `rgba(255,255,255,.08)`. */
    val line = Color(0x14FFFFFF)

    /** Граница карточки-панели плеера: `rgba(255,255,255,.12)`. */
    val lineStrong = Color(0x1FFFFFFF)

    /** Основной текст. */
    val text = Color(0xFFF5F6F8)

    /** Подписи и второстепенный текст. */
    val muted = Color(0xFF9299A8)

    /** Акцент: ссылки-действия и активные состояния. */
    val accent = Color(0xFFA987FF)

    /** Градиент кнопок. */
    val accentStrong = Color(0xFF7C55E8)

    /** Точка «в эфире» и успех в тосте. */
    val green = Color(0xFF45DF9A)

    // Строка канала.

    /** Название канала в строке. */
    val rowTitle = Color(0xFFF4F4F6)

    /** Передача в строке канала. */
    val rowProgramme = Color(0xFF8A91A0)

    /** Текст строки вне выбора, время «до …». */
    val rowIdle = Color(0xFF717886)

    /** Дорожка прогресса в строке. */
    val progressTrack = Color(0xFF272C37)

    /** Заливка прогресса в строке. */
    val progressFill = Color(0xFFAB8AF9)

    /** Шеврон выбранной строки. */
    val chevronSelected = Color(0xFFB99EFF)

    // Каркас.

    /** Пункт меню сайдбара в покое. */
    val menuItem = Color(0xFF858C9B)

    /** Иконка активного пункта меню. */
    val menuIconActive = Color(0xFFBC9EFF)

    /** Категория в покое. */
    val categoryIdle = Color(0xFF9399A7)

    /** Фон активной категории. */
    val categoryActiveBg = Color(0xFFF0ECFA)

    /** Текст активной категории. */
    val categoryActiveText = Color(0xFF17131E)

    /** Иконка поиска. */
    val searchIcon = Color(0xFFB2B8C3)

    // Карточка канала.

    /** Надпись категории (eyebrow) и заголовки секций. */
    val eyebrow = Color(0xFF9D80E8)

    /** «Сейчас» и время текущей передачи. */
    val now = Color(0xFFBDA6F5)

    /** Подпись «Сейчас» в блоке программы. */
    val nowLabel = Color(0xFF7F8695)

    /** Кнопка «Телепрограмма канала». */
    val programmeButtonText = Color(0xFFC3C9D4)

    /** Прошедшие передачи. */
    val programmePast = Color(0xFF9AA1AE)

    /** Время прошедшей передачи. */
    val programmePastTime = Color(0xFF8D94A2)

    /** Конец передачи в расписании. */
    val programmeEndTime = Color(0xFF656C7B)

    /** Градиент прогресса в карточке: начало. */
    val cardProgressStart = Color(0xFF8057E8)

    /** Градиент прогресса в карточке: конец. */
    val cardProgressEnd = Color(0xFFB596FF)

    /** Дорожка прогресса в карточке. */
    val cardProgressTrack = Color(0xFF292E39)

    // Бейджи и состояния.

    /** Текст бейджа «Архив». */
    val badgeArchive = Color(0xFFC1A7FF)

    /** Фон бейджа «Архив»: `rgba(169,135,255,.1)`. */
    val badgeArchiveBg = Color(0x1AA987FF)

    /** Рамка бейджа «Архив»: `rgba(169,135,255,.22)`. */
    val badgeArchiveLine = Color(0x38A987FF)

    /** Бейдж архива в выбранной строке. */
    val badgeArchiveSelected = Color(0xFFDCC9FF)

    /** Избранное (активное). */
    val favorite = Color(0xFFE76983)

    /** Фон активного избранного: `rgba(231,105,131,.08)`. */
    val favoriteBg = Color(0x14E76983)

    /** Рамка активного избранного: `rgba(231,105,131,.2)`. */
    val favoriteLine = Color(0x33E76983)

    /** Плашка «АРХИВ» в плеере. */
    val playerArchive = Color(0xFFFFC107)

    /** «Удалить» у гида. */
    val guideDelete = Color(0xFFD78AA0)

    // Фокус.

    /** Обводка фокуса: `#d1bcff`. */
    val focusOutline = Color(0xFFD1BCFF)

    /** Свечение фокуса: `rgba(169,135,255,.18)`. */
    val focusGlow = Color(0x2EA987FF)

    // Градиенты.

    /** Главная кнопка: начало. */
    val buttonStart = Color(0xFF845EE5)

    /** Главная кнопка: конец. */
    val buttonEnd = Color(0xFF9B73EF)

    /** Активный пункт меню: начало: `rgba(130,89,226,.28)`. */
    val menuActiveStart = Color(0x478259E2)

    /** Активный пункт меню: конец: `rgba(130,89,226,.09)`. */
    val menuActiveEnd = Color(0x178259E2)

    /** Выбранная строка: начало: `rgba(160,127,241,.16)`. */
    val rowSelectedStart = Color(0x29A07FF1)

    /** Выбранная строка: конец: `rgba(160,127,241,.04)`. */
    val rowSelectedEnd = Color(0x0AA07FF1)

    /** Рамка выбранной строки: `rgba(176,145,251,.21)`. */
    val rowSelectedLine = Color(0x36B091FB)

    /** Наведение строки: `rgba(255,255,255,.035)`. */
    val rowHover = Color(0x09FFFFFF)

    // Оверлеи.

    /** Фон диалога поиска. */
    val dialogBg = Color(0xFF151A24)

    /** Фон поля ввода. */
    val fieldBg = Color(0xFF0D1118)

    /** Плейсхолдер поля. */
    val placeholder = Color(0xFF565E6C)

    /** Подпись поля ввода. */
    val fieldLabel = Color(0xFF8B92A0)

    /** Текст неактивного чипа. */
    val chipIdle = Color(0xFF98A0AE)

    /** Затемнение поиска и диалога: `rgba(5,7,10,.73)`. */
    val scrim = Color(0xBA05070A)

    /** Затемнение панели плеера: `rgba(4,6,10,.38)`. */
    val playerScrim = Color(0x6104060A)

    /** Фон тоста. */
    val toastBg = Color(0xFF222731)

    /** Кружок галочки тоста: `rgba(69,223,154,.12)`. */
    val toastIconBg = Color(0x1F45DF9A)

    /** Галочка тоста. */
    val toastIcon = Color(0xFF64E3AA)

    /** Текст ошибки на плашке. */
    val errorText = Color(0xFFFF9DB0)

    /** Рамка плашки ошибки: `#e76983` с прозрачностью 32 %. */
    val errorLine = Color(0x52E76983)

    /** Фон плашки ошибки. */
    val errorBg = Color(0x14E76983)

    /** Текст экрана загрузки плеера. */
    val loadingText = Color(0xFFCFD4DD)

    /** Тонкий скроллбар. */
    val scrollbar = Color(0xFF353A47)

    /** Цвет логотипа канала: плитка с буквами. */
    val logoText = Color(0xFF091017)

    /** Текст заглушки пустого списка. */
    val emptyText = Color(0xFF666E7D)
}
