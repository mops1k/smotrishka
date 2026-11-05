# Автоматическое выравнивание звука (DRC) — исследование

Дата: 2026-11-04. Статус: **только исследование**, код не менялся.

Задача: на части каналов спецэффекты в фильмах идут очень громко, а разговоры — очень тихо.
Нужно выровнять звук на один уровень и сделать это включаемой настройкой.

Решения, зафиксированные при обсуждении:

- реализацию пока не начинаем, итог — этот документ;
- если делать, объём настройки: **вкл/выкл в настройках + тумблер в OSD плеера**.

## 1. Что технически возможно

### 1.1 Нормализация по EBU R128 невозможна для эфира

Классическая нормализация (быстрый замер всего материала → единый коэффициент усиления)
требует анализа всего файла вперёд. У линейного IPTV-канала будущего сигнала нет, метаданных
громкости провайдер не отдаёт. Для архива теоретически можно измерить передачу целиком, но это
двухпроходная обработка (сначала замер, потом воспроизведение) — для 10-секундных HLS-сегментов
в прямом эфире неприменимо.

### 1.2 Что реально решает задачу: DRC (компрессор + лимитер)

Динамическая обработка на лету — тот самый «night mode» телевизоров:

- сигнал тише порога поднимается (makeup/post-gain);
- сигнал выше порога придавливается (ratio);
- пики не дают клиппинга (лимитер с потолком около −1 dBFS).

Результат — ровный средний уровень: диалоги слышны, взрывы не «выбивают» окна. Это не
«абсолютно один уровень», а сужение динамического диапазона; для описанной проблемы это
единственный практичный вариант и он же — стандартное поведение ресиверов и телевизоров.

Ограничение по определению: обработка не должна менять длину аудиопотока, иначе поедет
синхронизация с видео. Компрессор и лимитер работают без задержки вперёд (без lookahead),
поэтому длину потока не меняют.

## 2. Как звук устроен в приложении сейчас (факты по коду)

| Факт | Где |
| --- | --- |
| Плеер создаётся в одном месте: `buildPlayer()` | `app/src/main/kotlin/com/mops1k/smotrishka/tv/ui/player/PlayerScreen.kt:1135` |
| Точка встраивания обработки звука: `DefaultRenderersFactory(...).setExtensionRendererMode(ON)` | там же, `:1166-1169` |
| Вызовы: эфир `:405`, архив `:397`, превью без звука `:1054` | там же |
| AC-3/E-AC-3 декодируются в PCM расширением jellyfin (`androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer`, media3 1.9.0 + `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1`) | `app/build.gradle.kts:80-84`, `gradle/libs.versions.toml:21,77-81` |
| Настройки плеера: DataStore, boolean-ключи по образцу `data_saver`, `direct_stream` | `core/data/src/main/kotlin/com/mops1k/smotrishka/tv/core/data/PlayerSettingsStore.kt:24,243-247` |
| Настройки экрана: `SettingsRow` + кнопка «Включить/Выключить» (образец — «Экономия трафика») | `app/src/main/kotlin/com/mops1k/smotrishka/tv/ui/settings/SettingsScreen.kt:194-200` |
| `SettingsViewModel` отдаёт `StateFlow` через `stateIn` | `.../ui/settings/SettingsViewModel.kt:89,96` |
| `PlayerViewModel` так же отдаёт настройки плеера | `.../ui/player/PlayerViewModel.kt:146-160` |
| OSD: кнопка «Звук», состояние — `PlayerControls.ControlsState` | `.../ui/player/PlayerScreen.kt:913,1194`; `.../ui/player/PlayerControls.kt:61-95` |
| `audioSessionId` не используется нигде | проверено поиском по `app`/`core` |
| В манифесте только `INTERNET` и `ACCESS_NETWORK_STATE`, `MODIFY_AUDIO_SETTINGS` нет | `app/src/main/AndroidManifest.xml:6-7` |

Номера строк в `PlayerScreen.kt` на момент написания отчёта: файл активно правится, ориентир —
имена функций (`buildPlayer`, `toggleSound`).

Важно: звук в приложении проходит через наш `AudioSink` в PCM, в том числе для AC-3 через
FFmpeg-декодер. Значит любая обработка ниже декодера возможна для всех каналов.

## 3. Варианты реализации

### Вариант A — `android.media.audiofx.DynamicsProcessing` на аудиосессии плеера

Платформенный DSP, API 28+. Проверено по `android-35/android.jar`: класс есть, содержит
`setInputGainAllChannelsTo`, `setPreEq*`, `setMbc*` (многополосный компрессор), `setPostEq*`,
`setLimiter*`; у `MbcBand` — `setThreshold`, `setRatio`, `setAttackTime`, `setReleaseTime`,
`setKneeWidth`, `setNoiseGateThreshold`, `setExpanderRatio`, `setPreGain`, `setPostGain`;
у `Limiter` — `setThreshold`, `setRatio`, `setAttackTime`, `setReleaseTime`, `setLinkGroup`.

Подключение: после `prepare()` взять `player.audioSessionId` (в media3 `Player.getAudioSessionId()`
есть) и создать эффект на этой сессии; при пересоздании плеера — заново.

Плюсы: минимум кода (~150 строк), рендереры не трогаем, A/V sync не затрагивается, CPU почти
бесплатно (DSP в аудиомикшере).

Минусы:

- результат зависит от прошивки: на ТВ-приставках эффект иногда создаётся, но не работает
  (проверяется только на устройстве);
- нужен `MODIFY_AUDIO_SETTINGS` в манифесте;
- не действует, если звук уходит passthrough-битстримом в HDMI-ресивер;
- не тестируется в JVM — только ручная проверка на устройстве.

### Вариант B — свой `AudioProcessor` в аудиоцепочке ExoPlayer (рекомендуемый)

Подтверждено по байткоду media3 1.9.0: `AudioProcessor` и `BaseAudioProcessor` находятся в
`androidx.media3.common.audio`; `DefaultAudioSink.Builder.setAudioProcessors(AudioProcessor...)`
оборачивает переданные процессоры в стандартную `DefaultAudioSink$DefaultAudioProcessorChain`,
которая **сохраняет** `SilenceSkippingAudioProcessor` и `SonicAudioProcessor` (то есть управление
скоростью воспроизведения и пропуск тишины не ломаются); подключение — переопределение
`protected buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParameters)` в
наследнике `DefaultRenderersFactory` (метод есть, дефолт собирает `DefaultAudioSink.Builder`
без явных capabilities); `MediaCodecAudioRenderer` принимает готовый `AudioSink` в конструкторе.

Схема: `LevelingAudioProcessor` (компрессор + лимитер по PCM) → `setAudioProcessors(...)` →
свой `AudioSink` → свой `RenderersFactory` → `buildPlayer()`.

Плюсы:

- работает при любом PCM-декодере, включая FFmpeg AC-3, и не зависит от прошивки;
- ядро — чистый Kotlin (`FloatArray`/`ByteBuffer`), а значит **покрывается юнит-тестами до кода**
  по принятому в проекте TDD, без устройства;
- включение/выключение на лету: флаг внутри процессора, плеер не пересоздаётся;
- нет новых разрешений;
- passthrough можно отключить только при включённой настройке (передать в синк
  `AudioCapabilities` без AC-3/E-AC-3: конструктор `AudioCapabilities(int[], int)` и
  `supportsEncoding` — публичные).

Минусы: свой DSP ~200–250 строк + тесты; нужно аккуратно держать формат (PCM16/float,
1/2/6 каналов) и не менять длину потока; при 5.1 обрабатывать все каналы одинаково (иначе
поедет стереопанорама).

### Вариант C — dialnorm/DRC внутри FFmpeg-декодера

У AC-3/E-AC-3 есть штатный `dialnorm` и параметр `drc_scale` в FFmpeg. Если сборка
`org.jellyfin.media3:media3-ffmpeg-decoder` выставляет эту опцию наружу, часть проблемы решалась
бы силами декодера.

Минусы: только AC-3/E-AC-3 (AAC и MP2 не покрыты), наружу опция не документирована — без
проверки на устройстве утверждать нельзя. Как единственное решение не годится; возможно как
дополнение для AC-3-каналов.

### Сравнение

| Критерий | A: DynamicsProcessing | B: свой AudioProcessor | C: FFmpeg dialnorm |
| --- | --- | --- | --- |
| Объём кода | ~150 строк | ~250 строк + тесты | неизвестно |
| Предсказуемость на ТВ | низкая | высокая | средняя |
| Тесты в JVM | нет | да (ядро) | нет |
| Работает при AC-3 через FFmpeg | да (PCM) | да (PCM) | да (только AC-3) |
| Новые разрешения | `MODIFY_AUDIO_SETTINGS` | нет | нет |
| Переключение на лету | да (`setEnabled`) | да (флаг) | нет (пересоздание декодера) |
| Риск для A/V sync | низкий | средний (контролируемый) | низкий |

## 4. Общие подводные камни

- **Passthrough.** Если звук уходит битстримом (AC-3/E-AC-3 в HDMI), приложение его не
  обрабатывает. При включённой настройке нужно форсировать PCM и проверить это на приставке с
  ресивером.
- **5.1 не ломать.** Одинаковые параметры на все каналы; для DynamicsProcessing — `setAllChannelsTo`,
  для своего процессора — независимые состояния на канал с одинаковой настройкой.
- **Потолок лимитера** ≤ −1 dBFS, иначе клиппинг на ЦАПе.
- **Длина потока не меняется** (без lookahead): только так исключается расхождение с видео.
- **Не пересоздавать плеер** при переключении настройки: флаг процессора (B) или `setEnabled` (A).
- **Превью в списках** идут без звука — обработку туда не подключать.

## 5. Стартовые параметры DSP

Вариант A (`DynamicsProcessing`, значения в dBFS и мс):

| Параметр | Значение |
| --- | --- |
| input gain | 0 dB |
| MBC threshold | −30 dBFS |
| MBC ratio | 6 |
| attack / release | 20 / 200 мс |
| knee | 6 dB |
| post gain | +6 dB |
| limiter threshold | −1 dBFS |

Вариант B (свой процессор): RMS-детектор, целевой уровень −18 dBFS, attack 30 мс,
release 300 мс, диапазон усиления −12…+18 dB, потолок −1 dBFS, ratio 6 после порога
−30 dBFS, плавное изменение усиления (сглаживание по сэмплам), чтобы не было «дыхания».

Точные значения подбираются на слух на реальном проблемном канале.

## 6. Как это ляжет в архитектуру (план на будущее, кода пока нет)

1. **Хранение.** `PlayerSettingsStore`: `booleanPreferencesKey("audio_leveling")` + `setAudioLeveling`,
   по образцу `data_saver` (`PlayerSettingsStore.kt:243`).
2. **ViewModel.** `PlayerViewModel`: `val audioLeveling: StateFlow<Boolean>` через `stateIn`,
   по образцу `audioLanguage` (`PlayerViewModel.kt:160`).
3. **Экран настроек.** `SettingsViewModel.setAudioLeveling`, в `SettingsScreen` — строка
   «Выравнивание звука», значение «включено/выключено», кнопка «Включить/Выключить»
   (образец — «Экономия трафика», `SettingsScreen.kt:194-200`), подсказка о смысле.
4. **OSD.** В `PlayerControls.ControlsState` добавить `leveling: Boolean` и `toggleLeveling()`
   рядом с `toggleSound()` (`PlayerControls.kt:61-95`); в OSD — кнопка рядом с «Звук».
   Тумблер в OSD меняет **ту же сохранённую настройку**, а не временный флаг: иначе поведение
   при следующем запуске будет неожиданным.
5. **Подключение.** Свой `DefaultRenderersFactory` (наследник) → `buildAudioSink` с
   `setAudioProcessors(levelingProcessor)`; при выключенной настройке процессор неактивен
   (`isActive() == false`) и звук проходит бит-в-бит.
6. **Живое переключение.** Передать в процессор `AtomicBoolean`-флаг из `buildPlayer()`,
   менять его из OSD/настроек без пересоздания плеера.
7. **Ограничения прошивки.** Если выбирать вариант A — при включении проверять, что эффект
   реально применён (`enabled`), и при неудаче показывать понятное сообщение, а не тихо не работать.
8. **Passthrough.** При включённой настройке отдавать в синк capabilities без AC-3/E-AC-3
   (variant B), проверить на приставке.

## 7. Тесты (TDD, до кода)

Ядро DSP в `:core:data` (или новый `:core:audio`), вход/выход — буферы, без Android:

- тихий сигнал (диалог, −30 dBFS) поднимается к целевому уровню;
- громкий сигнал (эффект, 0 dBFS) ограничивается потолком −1 dBFS, клиппинга нет;
- при выключенной настройке выход побитово равен входу;
- смена формата: PCM16 и float, 1/2/6 каналов, разные частоты;
- длина потока на выходе равна входу (проверка синхронизации);
- «дыхание»: ступенчатое изменение громкости не даёт скачков усиления больше заданного лимита.

Работа платформенного эффекта (вариант A) и passthrough проверяются только на устройстве.

## 8. Оценка работ

| Вариант | Работы | Оценка |
| --- | --- | --- |
| A | эффект + ключ настройки + строка + OSD-тумблер | ~0,5 дня + проверка на устройстве |
| B | ядро DSP + тесты + адаптер + рендереры + настройка + OSD | ~1–1,5 дня |
| C | проверка опций сборки FFmpeg | не рекомендуется как самостоятельное решение |

## 9. Что проверить на устройстве (риски, снятые не полностью)

- Поддержка `DynamicsProcessing` на Realme RMX5090 и на будущей Android TV-приставке.
- Поведение passthrough для AC-3 при включённой настройке (5.1 не должно превратиться в стерео).
- Нагрузка на CPU/батарею при включённой настройке.
- Слуховая проверка: проблемный канал с фильмами и BCU Comedy HD (AC-3 5.1).
- Отсутствие рассинхронизации A/V после 10–15 минут просмотра.

## 10. Открытые вопросы

- Нужен ли уровень «сильно» (второй набор параметров) — решается после прослушивания на устройстве.
- Запоминать ли настройку отдельно по каналам (как `buffered_channels`) или она общая.
- Оставлять ли обработку включённой для архива и для каналов с нормальным звуком.

## Приложение: чем проверялись факты

- API media3 1.9.0: `javap` по `media3-exoplayer-1.9.0.aar` и `media3-common-1.9.0.aar` из кэша
  Gradle (методы `buildAudioSink`, `setAudioProcessors`, `setAudioProcessorChain`,
  `AudioProcessor`, `BaseAudioProcessor`, `Player.getAudioSessionId`, `AudioCapabilities`;
  байткод `DefaultAudioProcessorChain` — наличие `SonicAudioProcessor` и
  `SilenceSkippingAudioProcessor`).
- API платформы: `javap` по `platforms/android-35/android.jar` (`DynamicsProcessing`,
  `DynamicsProcessing$Limiter`, `DynamicsProcessing$MbcBand`, `LoudnessEnhancer`).
- Код проекта: `PlayerScreen.kt`, `PlayerControls.kt`, `PlayerSettingsStore.kt`,
  `SettingsViewModel.kt`, `SettingsScreen.kt`, `PlayerViewModel.kt`, `AndroidManifest.xml`,
  `build.gradle.kts`, `gradle/libs.versions.toml`.
