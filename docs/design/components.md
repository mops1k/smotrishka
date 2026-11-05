# Библиотека компонентов

Атомарные части интерфейса крупным планом: как выглядят, из чего собраны, какие состояния.
Снимки — `screens/components/`, обрезка по элементу с увеличением (×2 и больше), чтобы размеры
и шрифты читались без макета.

## Каркас

| Компонент | Снимок | Параметры |
|---|---|---|
| Сайдбар | [`shell-sidebar.jpg`](screens/components/shell-sidebar.jpg) | ширина 236, фон `rgba(10,13,19,.88)`, `blur 20`, бордер справа |
| Пункт меню активный | [`shell-sidebar-active-item.jpg`](screens/components/shell-sidebar-active-item.jpg) | `min-height 52`, радиус 13, градиент `rgba(130,89,226,.28)→.09`, вставка слева 3 px `var(--accent)`, иконка `#bc9eff` |
| Пункт меню в покое | [`shell-sidebar-item.jpg`](screens/components/shell-sidebar-item.jpg) | текст `#858c9b` 14/600, иконка того же цвета |
| Шапка контента | [`shell-topbar.jpg`](screens/components/shell-topbar.jpg) | h1 29/600 `-.8px` слева, справа поиск и часы; строки над заголовком нет |
| Кнопка поиска | [`shell-search-button.jpg`](screens/components/shell-search-button.jpg) | 45×45, радиус 13, фон `var(--panel)`, рамка `var(--line)`, иконка `#b2b8c3` |

## Список каналов

| Компонент | Снимок | Параметры |
|---|---|---|
| Плашка категорий | [`screens-category-row.jpg`](screens/components/screens-category-row.jpg) | зазор 8, кнопка `padding 10px 18px`, радиус 11, 12/600; активная — фон `#f0ecfa`, текст `#17131e` |
| Шапка панели списка | [`screens-section-heading.jpg`](screens/components/screens-section-heading.jpg) | высота 69, `padding 0 22`, h2 17/600 + счётчик 10 через 9 px; сортировки нет |
| Список целиком | [`screens-channel-list.jpg`](screens/components/screens-channel-list.jpg) | `padding 9`, скролл, строки с зазором 12 по сетке |
| Строка канала | [`row-channel.jpg`](screens/components/row-channel.jpg) | `min-height 67`, `padding 7px 11px`, прямая 5 колонок `43/1fr/92/105/18`, зазор 12, радиус 13 |
| Строка выбранная | [`row-channel-selected.jpg`](screens/components/row-channel-selected.jpg) | градиент `rgba(160,127,241,.16)→.04`, рамка `rgba(176,145,251,.21)`, шеврон `#b99eff` |
| Строка без архива | [`row-channel-no-archive.jpg`](screens/components/row-channel-no-archive.jpg) | колонка бейджей пустая, но занимает 92 px — время и прогресс не съезжают |
| Бейдж «Архив» | [`row-channel-archive.jpg`](screens/components/row-channel-archive.jpg) | иконка часов 11 + текст 8/700 uppercase, цвет `#c1a7ff`, фон `rgba(169,135,255,.1)`, рамка `.22`, радиус 6, `padding 3px 6px` |
| Время и прогресс | [`row-channel-time-progress.jpg`](screens/components/row-channel-time-progress.jpg) | текст «до HH:MM» 9 `#717887`; дорожка 83×3, радиус 5, фон `#272c37`, заливка `#ab8af9`; зазор 7 |
| Логотип 39 | [`logo-small.jpg`](screens/components/logo-small.jpg) | 39×39, радиус 11, 11/800, тень `0 5px 17px rgba(0,0,0,.2)` |
| Логотип 94 | [`logo-large.jpg`](screens/components/logo-large.jpg) | 94×94, радиус 26, 27/800, тень `0 16px 40px rgba(0,0,0,.32)` |

## Карточка канала

| Компонент | Снимок | Параметры |
|---|---|---|
| Визуал | [`card-visual.jpg`](screens/components/card-visual.jpg) | `min-height 230`, радиальный градиент `#3c3055→#10131b`, сетка 32 px, два пятна `blur 50` |
| Копия | [`card-copy.jpg`](screens/components/card-copy.jpg) | `padding 19px 21px 21px`: категория 9/700 uppercase, h2 22/600, блок «Сейчас», кнопки, «Далее» |
| Избранное (активно) | [`card-favorite-active.jpg`](screens/components/card-favorite-active.jpg) | 39×39, радиус 11, `#e76983`, фон `.08`, рамка `.2`, сердце залито |
| Блок «Сейчас» | [`card-program-block.jpg`](screens/components/card-program-block.jpg) | сетка `48/1fr/auto`, подпись 9, название 11/500, интервал 9, полоса 3 px с градиентом |
| Кнопка телепрограммы | [`card-program-button.jpg`](screens/components/card-program-button.jpg) | `min-height 44`, радиус 12, рамка, иконка списка акцентом, шеврон справа; hover — акцентная рамка |
| Кнопка «Смотреть» | [`card-watch-button.jpg`](screens/components/card-watch-button.jpg) | `min-height 49`, радиус 13, градиент `#845ee5→#9b73ef`, 13/700, круг 28 с play |
| «Далее в …» | [`card-up-next.jpg`](screens/components/card-up-next.jpg) | подпись 9 `#7f8695`, название 11/500, разнесены по краям |

## Состояния

| Состояние | Снимок | Параметры |
|---|---|---|
| Строка под курсором | [`state-row-hover.jpg`](screens/components/state-row-hover.jpg) | фон `rgba(255,255,255,.035)` |
| Фокус с клавиатуры | [`state-focus.jpg`](screens/components/state-focus.jpg) | `outline 3px #d1bcff`, отступ 3, свечение `rgba(169,135,255,.18)` |
| Пустой список | [`state-empty-list.jpg`](screens/components/state-empty-list.jpg) | `min-height 290`, иконка 34, заголовок 14, подпись 10 |

## Телепрограмма в карточке

| Компонент | Снимок | Параметры |
|---|---|---|
| Развёрнутая панель | [`card-programme-panel.jpg`](screens/components/card-programme-panel.jpg) | визуал 126, лого 66, сетка передач под шапкой |
| Список передач | [`schedule-rows.jpg`](screens/components/schedule-rows.jpg) | строки `76/1fr/auto`, `min-height 42`, радиус 11, зазор 4 |
| Строка «сейчас» | [`schedule-row-now.jpg`](screens/components/schedule-row-now.jpg) | градиент + рамка, время `#bda6f5`, бейдж «Сейчас» 8/700 uppercase |
| Строка прошедшая | [`schedule-row-past.jpg`](screens/components/schedule-row-past.jpg) | название `#9aa1ae`, справа бейдж «Архив» |
| Кнопка «Свернуть» | [`schedule-close-button.jpg`](screens/components/schedule-close-button.jpg) | `min-height 39`, радиус 11, иконка назад + текст 10/600 |

## Настройки

| Компонент | Снимок | Параметры |
|---|---|---|
| Карточка «Источник каналов» | [`settings-card-source.jpg`](screens/components/settings-card-source.jpg) | 4 строки «значение + действие + подсказка» |
| Карточка «Воспроизведение» | [`settings-card-playback.jpg`](screens/components/settings-card-playback.jpg) | 5 настроек, четыре группы вариантов и переключатель |
| Карточка «Программа передач» | [`settings-card-epg.jpg`](screens/components/settings-card-epg.jpg) | окно, глубина, список гидов |
| Карточка «Плеер» | [`settings-card-player.jpg`](screens/components/settings-card-player.jpg) | две колонки: кадр и режим эфира |
| Строка настройки | [`settings-row.jpg`](screens/components/settings-row.jpg) | `padding 13px 0`, название 12/600, значение 10 `#8b92a0`, действие 10/600 акцентом, подсказка 9 |
| Группа вариантов | [`settings-choice-group.jpg`](screens/components/settings-choice-group.jpg) | зазор 7, кнопка `min-height 40`, радиус 10; активная — рамка `#a481f3`, фон `rgba(148,108,238,.16)` |
| Переключатель выкл | [`settings-toggle-off.jpg`](screens/components/settings-toggle-off.jpg) | 39×22, радиус 20, фон `#333946`, кружок `#9299a6` |
| Переключатель вкл | [`settings-toggle-on.jpg`](screens/components/settings-toggle-on.jpg) | фон `#7c59d2`, белый кружок, сдвиг 17 px |
| Строка гида | [`settings-guide-item.jpg`](screens/components/settings-guide-item.jpg) | фон `#0d1118`, радиус 11, иконка списка акцентом, имя 11/600, адрес 9, «Удалить» `#d78aa0` |

## Оверлеи

| Компонент | Снимок | Параметры |
|---|---|---|
| Диалог поиска | [`overlay-search-dialog.jpg`](screens/components/overlay-search-dialog.jpg) | 650 px, `padding 10`, радиус 17, поле 16, `kbd Esc` |
| Тост | [`overlay-toast.jpg`](screens/components/overlay-toast.jpg) | снизу по центру, круг с галочкой 27, фон `#222731`, 11 px |
| Диалог гида | [`overlay-guide-dialog.jpg`](screens/components/overlay-guide-dialog.jpg) | 520 px, заголовок 15/600, поле 43, две кнопки |

## Плеер

| Компонент | Снимок | Параметры |
|---|---|---|
| Экран загрузки | [`player-loading.jpg`](screens/components/player-loading.jpg) | по центру 13 px `#cfd4dd`, OSD и картинки нет |
| Верхняя полоса OSD | [`osd-top.jpg`](screens/components/osd-top.jpg) | назад 43, канал 17/600 + передача 11, 4 кнопки 43, часы 15/600 |
| Верхняя полоса в архиве | [`osd-top-archive.jpg`](screens/components/osd-top-archive.jpg) | плашка «АРХИВ» `#ffc107` и кнопка «В эфир» |
| Плашка архива | [`player-archive-badge.jpg`](screens/components/player-archive-badge.jpg) | 9/700, `letter-spacing 1.2px`, подчёркнутая ссылка |
| Нижняя полоса OSD | [`osd-bottom.jpg`](screens/components/osd-bottom.jpg) | строка передачи 10, полоса 4 px, ряд управления, статистика 9 |
| Главная кнопка | [`osd-play-button.jpg`](screens/components/osd-play-button.jpg) | круг 56, белый фон, тёмная иконка 26 |
| Кнопка ±10 с | [`osd-seek-button.jpg`](screens/components/osd-seek-button.jpg) | круг 43, иконка 22 и цифра 10 7/700. В приложении цифра не рисуется отдельно: используются иконки Material `Replay10` / `Forward10`, где «10» уже внутри — макет упрощает |
| Боковая стрелка | [`osd-side-arrow.jpg`](screens/components/osd-side-arrow.jpg) | круг 46, `bottom 148`, только в эфире |
| Подсказка режима кадра | [`player-aspect-hint.jpg`](screens/components/player-aspect-hint.jpg) | по центру, 11/600, живёт 1.6 с |

## Панели плеера

| Компонент | Снимок | Параметры |
|---|---|---|
| Карточка «Недавние» | [`panel-recents-card.jpg`](screens/components/panel-recents-card.jpg) | 900 px, стекло `.84` + `blur 18`, радиус 20, оверлей `.38` |
| Шапка панели | [`panel-recents-head.jpg`](screens/components/panel-recents-head.jpg) | назад 43, заголовок 16/600, счётчик 10 справа |
| Строка канала в панели | [`panel-recents-row.jpg`](screens/components/panel-recents-row.jpg) | `39/1fr/auto`, `padding 9px 12px`, активная — градиент и рамка |
| Карточка «Телепрограмма» | [`panel-programme-card.jpg`](screens/components/panel-programme-card.jpg) | то же стекло, строки передач внутри |
| Строка «сейчас» в панели | [`panel-programme-row-now.jpg`](screens/components/panel-programme-row-now.jpg) | подсвечена, фокус сюда ставится при открытии |
| Строка прошедшая в панели | [`panel-programme-row-past.jpg`](screens/components/panel-programme-row-past.jpg) | приглушена, с бейджем «Архив» |

## Порядок фокуса по Tab

Снят обходом с клавиатуры на главном экране (`screens/focus/01…23.jpg` — рамка фокуса на каждом
шаге). Это эталонный порядок для пульта и тача: 23 остановки, дальше цикл повторяется.

| Шаг | Элемент | Класс | Размер |
|---|---|---|---|
| 1 | Бренд «Смотришка ТВ — на главную» | `brand` | 195×38 |
| 2 | Телеканалы (активен) | `.active` | 195×52 |
| 3 | Избранное | — | 195×52 |
| 4 | Недавние | — | 195×52 |
| 5 | Настройки | — | 195×52 |
| 6 | Поиск | `icon-button` | 45×45 |
| 7 | Все каналы (активна) | `.active` | 100×38 |
| 8–12 | Новости, Кино, Музыка, Спорт, Детям | — | 65–84×38 |
| 13–20 | Восемь строк каналов | `channel-row`, у выбранной `.selected` | 518×67 |
| 21 | Добавить в избранное | `favorite.active` | 39×39 |
| 22 | Открыть телепрограмму канала | `program-button` | 353×44 |
| 23 | Смотреть | `watch-button` | 353×49 |

Фокус-рамка: [`focus/13.jpg`](screens/focus/13.jpg) — видно обводку 3 px `#d1bcff` с отступом 3 и
внешним свечением.

## Вариации архива

| Снимок | Что показывает |
|---|---|
| [`archive-programme-musicbox.jpg`](screens/components/archive-programme-musicbox.jpg) | панель программы «Music Box»: архив 3 суток, у трёх прошедших бейдж «Архив» |
| [`archive-osd-musicbox.jpg`](screens/components/archive-osd-musicbox.jpg) | архивный режим другого канала: «АРХИВ», «В эфир», «воспроизведение …» |
| [`archive-programme-no-archive.jpg`](screens/components/archive-programme-no-archive.jpg) | панель «Планета»: архива нет, бейджей нет, строки помечены `no-archive` |
| [`archive-past-without-archive.jpg`](screens/components/archive-past-without-archive.jpg) | клик по прошедшей без архива: подсказка «Архив недоступен для этого канала», плеер не переключается |
| [`no-archive-main-panel.jpg`](screens/components/no-archive-main-panel.jpg) | то же в телепрограмме основного экрана (карточка канала) |
| [`no-archive-main-toast.jpg`](screens/components/no-archive-main-toast.jpg) | подсказка «Архив недоступен для этого канала» в телепрограмме карточки |

Поведение (реализовано в макете, повторить в приложении): прошедшая передача без архива
получает класс `no-archive` — без hover-подсветки и с обычным курсором; клик не включает архив и
не открывает запись, а показывает подсказку «Архив недоступен для этого канала». Тост
«начнётся в …» применяется только к будущим передачам.

## Онбординг

| Компонент | Снимок | Параметры |
|---|---|---|
| Марка и название | [`onb-brand.jpg`](screens/components/onb-brand.jpg) | марка 38×38 радиус 12 градиент `#845ee5→#a987ff`, «Смотришка» 13/700, «телевидение» 9 uppercase |
| Ряд способов подключения | [`onb-kind-row.jpg`](screens/components/onb-kind-row.jpg) | зазор 8, перенос, четыре чипа |
| Чип активный | [`onb-chip-active.jpg`](screens/components/onb-chip-active.jpg) | `min-height 40`, радиус 11, фон `#f0ecfa`, текст `#17131e` |
| Чип в покое | [`onb-chip.jpg`](screens/components/onb-chip.jpg) | фон `var(--panel)`, текст `#98a0ae`, рамка `var(--line)` |
| Поле ввода | [`onb-field.jpg`](screens/components/onb-field.jpg) | подпись 10/600, бокс `min-height 46`, радиус 12, фон `#0d1118` |
| Поле в фокусе | [`onb-field-focus.jpg`](screens/components/onb-field-focus.jpg) | рамка `rgba(169,135,255,.6)` + свечение `0 0 0 3px rgba(169,135,255,.16)` |
| Поле пароля | [`onb-field-password.jpg`](screens/components/onb-field-password.jpg) | кнопка «Скрыть/Показать» 9/700 uppercase, радиус 8 |
| Плашка файла | [`onb-file-picker.jpg`](screens/components/onb-file-picker.jpg) | пунктирная рамка, имя 12/600, подпись 9, кнопка справа |
| Кнопка «Подключить» | [`onb-submit.jpg`](screens/components/onb-submit.jpg) | `min-height 48`, радиус 13, градиент, 13/700, тень акцентом |
| Кнопка «Отмена» | [`onb-ghost.jpg`](screens/components/onb-ghost.jpg) | `min-height 44`, радиус 12, фон `var(--panel)`, текст 11/600 |
| Плашка ошибки | [`onb-error.jpg`](screens/components/onb-error.jpg) | круг 16 с «!», рамка `#e76983` 32 %, текст 11 `#ff9db0` |
| Состояние загрузки | [`onb-loading.jpg`](screens/components/onb-loading.jpg) | спиннер 17, текст «Загружаем каналы…» 12 |
| Блок «Как это работает» | [`onb-steps.jpg`](screens/components/onb-steps.jpg) | номера 10/800 акцентом, текст 11 `#98a0ae` |
| Пара кнопок при входе из настроек | [`onb-cancel.jpg`](screens/components/onb-cancel.jpg) | «Отмена» + «Подключить», зазор 10 |

Порядок фокуса онбординга — `screens/focus/onboarding-01…06.jpg`: четыре чипа, поле, кнопка
«Подключить». Экран целиком — [`onboarding.md`](onboarding.md).
