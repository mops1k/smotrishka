package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.EpgGuide
import com.mops1k.smotrishka.tv.core.model.PlaylistSource
import com.mops1k.smotrishka.tv.core.playlist.M3uParseResult
import com.mops1k.smotrishka.tv.core.playlist.XmltvParseResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Обновление данных приложения: источник → каталог → программа передач → база.
 *
 * Порядок намеренный: каталог сохраняется сразу после разбора плейлиста, поэтому
 * сбой программы передач оставляет приложение с рабочими каналами и лишь частичным
 * результатом (как и сбой обновления EPG в реальной жизни).
 *
 * Программа передач собирается из трёх слоёв: гид провайдера (`x-tvg-url`), затем гиды
 * пользователя из настроек. Гиды пользователя применяются и тогда, когда у плейлиста
 * своего адреса нет, — иначе каналы остались бы без программы.
 *
 * @param catalogStore хранилище каталога;
 * @param epgStore хранилище программы;
 * @param playlistLoader загрузка плейлиста любого источника;
 * @param epgLoader загрузка программы передач;
 * @param settings настройки окна программы передач;
 * @param clock источник текущего времени для обрезки окна EPG;
 * @param guideRepository гиды пользователя; `null` — дополнение выключено;
 * @param streamImporter потоковый импорт больших гидов;
 * @param guideState память о загрузках гидов для троттлинга; `null` — без троттлинга.
 */
class CatalogUpdater(
    private val catalogStore: CatalogStore,
    private val epgStore: EpgStore,
    private val playlistLoader: PlaylistSourceLoader,
    private val epgLoader: EpgLoader,
    private val settings: PlayerSettingsStore,
    private val clock: Clock = Clock.systemUTC(),
    private val guideRepository: EpgGuideRepository? = null,
    private val streamImporter: EpgStreamImporter? = null,
    private val guideState: EpgGuideStateStore? = null,
    private val importTracker: EpgImportTracker? = null,
) {

    /**
     * Один проход синхронизации за раз.
     *
     * Фоновых запусков два (периодическая задача и разовый запуск при старте), и без этого
     * мьютекса они качали один и тот же гид дважды подряд (лог 10.11.2026).
     */
    private val syncMutex = Mutex()

    /**
     * Обновляет каталог и программу передач.
     *
     * Работа идёт на [Dispatchers.IO]: сюда входят сетевые загрузки и разбор больших файлов,
     * которым нечего делать на главном потоке.
     *
     * @param source настроенный источник.
     */
    suspend fun update(source: PlaylistSource): UpdateResult = withContext(Dispatchers.IO) {
        syncMutex.withLock { updateInternal(source) }
    }

    /**
     * Загружает только каталог каналов, без программы передач.
     *
     * Нужно экрану подключения: гид весит десятки мегабайт и разбирается минутами, а каналы
     * должны появляться сразу. Программу передач затем догружает [EpgUpdater]-подобный фон.
     *
     * @param source настроенный источник.
     */
    suspend fun updateCatalogOnly(source: PlaylistSource): CatalogUpdateResult =
        withContext(Dispatchers.IO) {
            val playlist = when (val loaded = playlistLoader.load(source)) {
                is M3uParseResult.Failure -> return@withContext CatalogUpdateResult.Failure(loaded.reason)
                is M3uParseResult.Success -> loaded.playlist
            }

            if (playlist.channels.isEmpty()) {
                return@withContext CatalogUpdateResult.Failure(
                    "Плейлист пуст: проверьте источник и код доступа",
                )
            }

            catalogStore.replaceCatalog(playlist)

            CatalogUpdateResult.Success(
                channels = playlist.channels.size,
                categories = playlist.categories.size,
                epgUrl = playlist.epgUrl?.takeIf { it.isNotBlank() },
            )
        }

    private suspend fun updateInternal(source: PlaylistSource): UpdateResult {
        val playlist = when (val loaded = playlistLoader.load(source)) {
            is M3uParseResult.Failure -> return UpdateResult.Failure(loaded.reason)
            is M3uParseResult.Success -> loaded.playlist
        }

        if (playlist.channels.isEmpty()) {
            return UpdateResult.Failure("Плейлист пуст: проверьте источник и код доступа")
        }

        catalogStore.replaceCatalog(playlist)

        val now = clock.instant()
        // Окно назад — не меньше глубины архива, иначе прошедшие передачи не попадут в список.
        val epgWindow = settings.epgWindowHours.first().toLong()
        val archiveDepth = settings.archiveDepthHours.first().toLong()
        val backHours = maxOf(epgWindow, archiveDepth)
        val windowFrom = now.minus(backHours, ChronoUnit.HOURS)
        val windowTo = now.plus(EPG_FORWARD_HOURS, ChronoUnit.HOURS)

        var programmes = 0
        var reason: String? = null
        var loadedProviderUrl: String? = null

        val epgUrl = playlist.epgUrl?.takeIf { it.isNotBlank() }
        val importer = streamImporter
        if (epgUrl == null) {
            reason = "В плейлисте нет адреса программы передач"
        } else if (importer != null) {
            // Гид провайдера пишется пачками: у крупного провайдера это сотни тысяч передач,
            // и держать их все объектами в памяти незачем — это и был пик памяти в ANR-снимке.
            // Прежнюю программу НЕ стираем: пока новый гид пишется, у играющего канала должна
            // оставаться передача. Лишние старые записи срежет prune по окну.
            when (
                val imported = importer.import(
                    url = epgUrl,
                    backHours = backHours,
                    forwardHours = EPG_FORWARD_HOURS,
                    catalogChannels = playlist.channels,
                )
            ) {
                is EpgStreamResult.Success -> {
                    epgStore.prune(now, backHours = backHours)
                    programmes = imported.added
                    loadedProviderUrl = epgUrl
                }

                is EpgStreamResult.Failure ->
                    reason = "Программа передач не загружена: ${imported.reason}"
            }
        } else when (val loaded = epgLoader.load(url = epgUrl, from = windowFrom, to = windowTo)) {
            is XmltvParseResult.Failure ->
                reason = "Программа передач не загружена: ${loaded.reason}"

            is XmltvParseResult.Success -> {
                epgStore.replace(loaded.epg)
                epgStore.prune(now, backHours = backHours)
                programmes = loaded.epg.programmes.size
                loadedProviderUrl = epgUrl
            }
        }

        programmes += mergeUserGuides(
            now = now,
            backHours = backHours,
            catalogChannels = playlist.channels,
            loadedProviderUrl = loadedProviderUrl,
        )

        val failureReason = reason
        return if (failureReason == null) {
            UpdateResult.Success(
                channels = playlist.channels.size,
                categories = playlist.categories.size,
                programmes = programmes,
            )
        } else {
            UpdateResult.Partial(
                reason = failureReason,
                channels = playlist.channels.size,
                categories = playlist.categories.size,
                programmes = programmes,
            )
        }
    }

    /**
     * Загружает и заменяет программу передач по указанному адресу.
     *
     * Нужно после импорта файла плейлиста: у каждого плейлиста может быть своя ссылка на
     * программу передач (заголовок `x-tvg-url`), и старая программа к нему не относится.
     * Если адреса нет, программа всё равно собирается из гидов пользователя.
     *
     * @param epgUrl адрес XMLTV либо `null`, если у плейлиста его нет;
     * @param force полное обновление: кэш программы сбрасывается, а гиды перекачиваются
     *   независимо от того, как давно их загружали.
     */
    suspend fun updateEpgOnly(epgUrl: String?, force: Boolean = false): UpdateResult =
        withContext(Dispatchers.IO) {
            syncMutex.withLock { updateEpgOnlyLocked(epgUrl, force) }
        }

    /** Тело [updateEpgOnly] под мьютексом синхронизации. */
    private suspend fun updateEpgOnlyLocked(epgUrl: String?, force: Boolean): UpdateResult {
        val now = clock.instant()
        val epgWindow = settings.epgWindowHours.first().toLong()
        val archiveDepth = settings.archiveDepthHours.first().toLong()
        val backHours = maxOf(epgWindow, archiveDepth)
        val windowFrom = now.minus(backHours, ChronoUnit.HOURS)
        val windowTo = now.plus(EPG_FORWARD_HOURS, ChronoUnit.HOURS)

        var programmes = 0
        var reason: String? = null
        var loadedProviderUrl: String? = null

        val catalogChannels = runCatching { catalogStore.playlist().channels }.getOrDefault(emptyList())
        val importer = streamImporter
        val url = epgUrl?.takeIf { it.isNotBlank() }
        if (force) {
            // Полное обновление: стираем всё, что было, чтобы не осталось данных от гидов,
            // которые больше не должны участвовать (просьба Александра 10.11.2026).
            android.util.Log.d("SmotrishkaSync", "epg full refresh: кэш программы сброшен")
            epgStore.clear()
        }
        if (url == null) {
            // Адреса нет: программа собирается только из гидов пользователя, старое чистим.
            epgStore.clear()
            reason = "В плейлисте нет адреса программы передач"
        } else if (importer != null) {
            // Программу не стираем: пока новый гид пишется пачками, старая программа должна
            // оставаться доступной в плеере. Устаревшее срежет prune по окну.
            when (
                val imported = importer.import(
                    url = url,
                    backHours = backHours,
                    forwardHours = EPG_FORWARD_HOURS,
                    catalogChannels = catalogChannels,
                )
            ) {
                is EpgStreamResult.Success -> {
                    epgStore.prune(now, backHours = backHours)
                    programmes = imported.added
                    loadedProviderUrl = url
                }

                is EpgStreamResult.Failure ->
                    reason = "Программа передач не загружена: ${imported.reason}"
            }
        } else {
            epgStore.clear()
            when (val loaded = epgLoader.load(url = url, from = windowFrom, to = windowTo)) {
                is XmltvParseResult.Failure ->
                    reason = "Программа передач не загружена: ${loaded.reason}"

                is XmltvParseResult.Success -> {
                    epgStore.replace(loaded.epg)
                    epgStore.prune(now, backHours = backHours)
                    programmes = loaded.epg.programmes.size
                    loadedProviderUrl = url
                }
            }
        }

        programmes += mergeUserGuides(
            now = now,
            backHours = backHours,
            catalogChannels = catalogChannels,
            loadedProviderUrl = loadedProviderUrl,
            force = force,
        )

        val failureReason = reason
        return if (failureReason == null) {
            UpdateResult.Success(channels = 0, categories = 0, programmes = programmes)
        } else {
            UpdateResult.Partial(
                reason = failureReason,
                channels = 0,
                categories = 0,
                programmes = programmes,
            )
        }
    }

    /**
     * Дополняет программу гидами пользователя.
     *
     * Гиды разбираются по порядку добавления и не перетирают провайдерские передачи:
     * `EpgStore.mergeBatch` вставляет только отсутствующие. Чтобы не тратить трафик, гид
     * пропускается, если он лежит на хосте уже загруженного гида провайдера, если каталог
     * уже покрыт программой или если гид качали меньше 12 часов назад.
     *
     * @return сколько передач добавлено суммарно.
     */
    private suspend fun mergeUserGuides(
        now: Instant,
        backHours: Long,
        catalogChannels: List<Channel>,
        loadedProviderUrl: String?,
        force: Boolean = false,
    ): Int {
        val guides = runCatching { guideRepository?.guides().orEmpty() }.getOrDefault(emptyList())
        if (guides.isEmpty()) return 0

        val from = now.minus(backHours, ChronoUnit.HOURS)
        val to = now.plus(EPG_FORWARD_HOURS, ChronoUnit.HOURS)
        val providerHost = loadedProviderUrl?.let(::guideHost)
        val expectedChannels = catalogChannels.count { it.epgId != null }
        var added = 0

        guides.forEach { guide ->
            if (providerHost != null && guideHost(guide.url) == providerHost) {
                android.util.Log.d(
                    "SmotrishkaSync",
                    "epg guide: ${guide.name} пропущен — тот же хост, что у гида провайдера",
                )
                return@forEach
            }

            // Незавершённый импорт важнее свежести: прошлая загрузка оборвалась, и программа
            // осталась полупустой (требование Александра 10.11.2026).
            val unfinished = importTracker?.isUnfinished(guide.url) == true
            if (unfinished) {
                android.util.Log.d(
                    "SmotrishkaSync",
                    "epg guide: ${guide.name} прошлый импорт не завершён — качаем заново",
                )
            }

            // Принудительное обновление игнорирует «каталог уже покрыт» и «качали недавно»:
            // пользователь явно попросил перекачать программу целиком (10.11.2026).
            if (!force && !unfinished && isCatalogCovered(from, to, expectedChannels)) {
                android.util.Log.d(
                    "SmotrishkaSync",
                    "epg guide: ${guide.name} пропущен — каталог уже покрыт программой",
                )
                return@forEach
            }

            if (!force && !unfinished && isFresh(guide, now)) {
                android.util.Log.d(
                    "SmotrishkaSync",
                    "epg guide: ${guide.name} пропущен — загружали меньше ${GUIDE_REFRESH_HOURS} ч назад",
                )
                return@forEach
            }

            added += importGuide(guide, backHours, catalogChannels, from, to, now)
        }
        return added
    }

    private suspend fun importGuide(
        guide: EpgGuide,
        backHours: Long,
        catalogChannels: List<Channel>,
        from: Instant,
        to: Instant,
        now: Instant,
    ): Int {
        streamImporter?.let { importer ->
            return when (
                val imported = importer.import(
                    url = guide.url,
                    backHours = backHours,
                    forwardHours = EPG_FORWARD_HOURS,
                    catalogChannels = catalogChannels,
                )
            ) {
                is EpgStreamResult.Success -> {
                    guideState?.markFetched(guide.name, now)
                    android.util.Log.d(
                        "SmotrishkaSync",
                        "epg guide: ${guide.name} добавлено=${imported.added}",
                    )
                    imported.added
                }

                is EpgStreamResult.Failure -> {
                    android.util.Log.w("SmotrishkaSync", "epg guide: ${guide.name} ${imported.reason}")
                    0
                }
            }
        }

        return when (val loaded = epgLoader.load(url = guide.url, from = from, to = to)) {
            is XmltvParseResult.Failure -> {
                android.util.Log.w(
                    "SmotrishkaSync",
                    "epg guide: ${guide.name} не загружен: ${loaded.reason}",
                )
                0
            }

            is XmltvParseResult.Success -> {
                val merged = epgStore.merge(loaded.epg)
                guideState?.markFetched(guide.name, now)
                android.util.Log.d(
                    "SmotrishkaSync",
                    "epg guide: ${guide.name} добавлено=$merged всего=${loaded.epg.programmes.size}",
                )
                merged
            }
        }
    }

    /** Каталог уже покрыт программой: остальные гиды качать незачем. */
    private suspend fun isCatalogCovered(from: Instant, to: Instant, expectedChannels: Int): Boolean {
        if (expectedChannels == 0) return false
        val covered = epgStore.coveredChannels(from, to)
        return covered.toLong() * 100 >= expectedChannels.toLong() * COVERAGE_PERCENT
    }

    /** Гид загружали меньше [GUIDE_REFRESH_HOURS] часов назад. */
    private suspend fun isFresh(guide: EpgGuide, now: Instant): Boolean {
        val state = guideState ?: return false
        val last = state.lastFetched(guide.name) ?: return false
        return last.plus(GUIDE_REFRESH_HOURS, ChronoUnit.HOURS).isAfter(now)
    }

    private companion object {
        /**
         * Сколько часов программы хранить вперёд.
         *
         * Провайдеры отдают программу вперёд не дальше нескольких суток, и окно задано
         * фиксированным: иначе при глубине архива 168 часов гид разбирался бы на две недели
         * вперёд, а строки тут же удалялись обрезкой.
         */
        const val EPG_FORWARD_HOURS = 48L

        /** Сколько часов действует успешная загрузка гида. */
        const val GUIDE_REFRESH_HOURS = 12L

        /** Покрытие каталога, при котором остальные гиды не нужны. */
        const val COVERAGE_PERCENT = 95
    }
}
