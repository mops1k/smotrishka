package com.mops1k.smotrishka.tv.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Размеры и радиусы редизайна из `docs/design/tokens.md`.
 *
 * 1 px макета ≈ 1 dp (решение Д10): значения переносятся из документа как есть.
 */
object TvDimens {

    /** Ширина сайдбара. */
    val sidebarWidth: Dp = 236.dp

    /** Ширина свёрнутого сайдбара в узком режиме. */
    val sidebarWidthNarrow: Dp = 72.dp

    /** Отступы контента раздела: `30px 42px 38px`. */
    val contentTop: Dp = 30.dp
    val contentHorizontal: Dp = 42.dp
    val contentBottom: Dp = 38.dp

    /** Предельная ширина контента. */
    val contentMaxWidth: Dp = 1700.dp

    /** Отступ шапки снизу. */
    val headerBottom: Dp = 29.dp

    /** Отступ шапки снизу в узком режиме. */
    val headerBottomNarrow: Dp = 17.dp

    /** Кнопка поиска и круглая кнопка «назад» в шапке. */
    val iconButton: Dp = 45.dp

    /** Пункт меню: минимальная высота. */
    val menuItemMinHeight: Dp = 52.dp

    /** Зазор между пунктами меню. */
    val menuGap: Dp = 8.dp

    /** Знак бренда в сайдбаре. */
    val brandMark: Dp = 30.dp

    /** Марка онбординга. */
    val onboardingMark: Dp = 38.dp

    /** Карточка онбординга: ширина и предельная ширина полей. */
    val onboardingCardWidth: Dp = 1040.dp
    val onboardingFieldsMaxWidth: Dp = 700.dp

    /** Правая колонка «Как это работает». */
    val onboardingAsideWidth: Dp = 256.dp

    /** Плашка категорий: горизонтальный зазор. */
    val categoryGap: Dp = 8.dp

    /** Шапка панели списка. */
    val listHeaderHeight: Dp = 52.dp

    /** Отступ списка внутри панели. */
    val listPadding: Dp = 9.dp

    /** Минимальная высота строки канала. */
    val channelRowMinHeight: Dp = 56.dp

    /** Колонки строки канала: логотип, бейджи, время, шеврон. */
    val rowLogo: Dp = 38.dp
    val rowBadges: Dp = 92.dp
    val rowTime: Dp = 105.dp
    val rowChevron: Dp = 18.dp

    /** Логотип канала в строке. */
    val logoSmall: Dp = 39.dp

    /** Логотип канала в карточке. */
    val logoLarge: Dp = 94.dp

    /** Логотип в развороте телепрограммы. */
    val logoMedium: Dp = 66.dp

    /** Визуал карточки канала. */
    val cardVisual: Dp = 230.dp

    /** Визуал карточки в развороте телепрограммы. */
    val cardVisualCompact: Dp = 126.dp

    /** Минимальная высота панели карточки. */
    val cardPanelMinHeight: Dp = 533.dp

    /** Кнопка «Телепрограмма канала». */
    val programmeButtonMinHeight: Dp = 44.dp

    /** Кнопка «Смотреть». */
    val watchButtonMinHeight: Dp = 49.dp

    /** Кнопка избранного. */
    val favoriteButton: Dp = 39.dp

    /** Ширина полосы прогресса в карточке. */
    val cardProgressWidth: Dp = 83.dp

    /** Высота полосы прогресса в карточке. */
    val cardProgressHeight: Dp = 3.dp

    /** Ширина полосы прогресса в строке. */
    val rowProgressWidth: Dp = 83.dp

    /** Высота полосы прогресса в строке. */
    val rowProgressHeight: Dp = 3.dp

    /** Строка передачи в расписании. */
    val scheduleRowMinHeight: Dp = 42.dp

    /** Колонка времени в расписании. */
    val scheduleTimeWidth: Dp = 76.dp

    /** Кнопка «Свернуть» в развороте. */
    val collapseButtonMinHeight: Dp = 39.dp

    /** Карточка настроек: отступ внутри. */
    val settingsCardPadding: Dp = 23.dp

    /** Иконка шапки карточки настроек. */
    val settingsIcon: Dp = 43.dp

    /** Предельная ширина страницы настроек. */
    val settingsMaxWidth: Dp = 1100.dp

    /** Кнопка варианта в настройках. */
    val choiceMinHeight: Dp = 40.dp

    /** Поле ввода: минимальная высота. */
    val fieldMinHeight: Dp = 46.dp

    /** Кнопка «Подключить» в онбординге. */
    val submitMinHeight: Dp = 48.dp

    /** Кнопка «Отмена» в онбординге. */
    val cancelMinHeight: Dp = 44.dp

    /** Плашка файла плейлиста. */
    val filePickerMinHeight: Dp = 74.dp

    /** Чип способа подключения. */
    val chipMinHeight: Dp = 40.dp

    /** Круглая кнопка OSD. */
    val osdButton: Dp = 43.dp

    /** Главная кнопка OSD. */
    val osdMainButton: Dp = 56.dp

    /** Боковая стрелка канала в плеере. */
    val osdSideArrow: Dp = 46.dp

    /** Отступ боковых стрелок снизу. */
    val osdSideArrowBottom: Dp = 148.dp

    /** Полоса перемотки OSD. */
    val osdSeekBarHeight: Dp = 4.dp

    /** Карточка панели плеера. */
    val playerPanelWidth: Dp = 900.dp

    /** Диалог поиска. */
    val searchDialogWidth: Dp = 650.dp

    /** Диалог добавления гида. */
    val guideDialogWidth: Dp = 520.dp

    /** Тост: отступ снизу. */
    val toastBottom: Dp = 29.dp

    /** Тост: кружок с галочкой. */
    val toastIcon: Dp = 27.dp
}

/** Радиусы макета. */
object TvRadius {

    /** Панели и карточка панели плеера. */
    val panel: Dp = 20.dp

    /** Диалог поиска и диалог гида. */
    val dialog: Dp = 17.dp

    /** Пункт меню, строка канала, кнопка-иконка, главная кнопка. */
    val item: Dp = 13.dp

    /** Бренд-кнопка, «Телепрограмма канала», поле ввода. */
    val control: Dp = 12.dp

    /** Логотип канала 39, категория, кнопка «Свернуть», строка расписания. */
    val small: Dp = 11.dp

    /** Кнопки вариантов, второстепенная кнопка. */
    val choice: Dp = 10.dp

    /** Плашка «Сейчас в эфире». */
    val plaque: Dp = 8.dp

    /** Бейджи «Архив». */
    val badge: Dp = 6.dp

    /** `HD` на визуале. */
    val hd: Dp = 5.dp

    /** Логотип в карточке. */
    val logoLarge: Dp = 26.dp

    /** Марка онбординга. */
    val mark: Dp = 12.dp
}

/** Готовые формы скруглений для частых случаев. */
object TvShapes {
    val panel = RoundedCornerShape(TvRadius.panel)
    val dialog = RoundedCornerShape(TvRadius.dialog)
    val item = RoundedCornerShape(TvRadius.item)
    val control = RoundedCornerShape(TvRadius.control)
    val small = RoundedCornerShape(TvRadius.small)
    val choice = RoundedCornerShape(TvRadius.choice)
    val plaque = RoundedCornerShape(TvRadius.plaque)
    val badge = RoundedCornerShape(TvRadius.badge)
}
