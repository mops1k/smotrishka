package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.Channel
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.playlist.EpgIndex
import com.mops1k.smotrishka.tv.core.playlist.EpgMatcher
import com.mops1k.smotrishka.tv.core.playlist.XmltvStreamParser
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Потоковый импорт публичной программы передач.
 *
 * Большие гиды (десятки и сотни мегабайт после распаковки) не помещаются в память целиком,
 * поэтому передачи читаются порциями и сразу дописываются в базу — существующие записи
 * провайдера не перетираются.
 *
 * Публичные гиды живут в своём пространстве идентификаторов (`epg.one` — числа,
 * `iptvx.one` — слаги), поэтому каналы гида сопоставляются с каталогом по тем же правилам,
 * что и везде ([EpgIndex]): `tvg-id`, точное имя и бренд без языковых суффиксов. Бренд нужен,
 * чтобы в базе был свежий одноимённый канал другого гида и провайдер мог на него переключиться.
 * Передачи чужих каналов в базу не пишутся — иначе она раздувается до сотен мегабайт.
 *
 * @param client потоковый HTTP-клиент;
 * @param store хранилище программы передач;
 * @param parser потоковый разбор XMLTV;
 * @param clock источник времени: окно передач считается от него;
 * @param matcher сопоставление каналов каталога с каналами гида;
 * @param tracker учёт незавершённых импортов; `null` — без учёта.
 */
class EpgStreamImporter(
    private val client: HttpBytesClient,
    private val store: EpgStore,
    private val parser: XmltvStreamParser = XmltvStreamParser(),
    private val clock: Clock = Clock.systemUTC(),
    private val matcher: EpgMatcher = EpgMatcher(),
    private val tracker: EpgImportTracker? = null,
) {

    /**
     * Загружает гид и дописывает недостающие передачи.
     *
     * @param url адрес XMLTV;
     * @param backHours сколько часов назад брать передачи;
     * @param forwardHours сколько часов вперёд брать передачи;
     * @param catalogChannels каналы каталога для сопоставления; пустой список — без фильтра;
     * @param replace `true` — прежняя программа стирается (гид провайдера), `false` — гид
     *   дополняет уже загруженную программу (гид пользователя).
     * @return результат импорта.
     */
    suspend fun import(
        url: String,
        backHours: Long,
        forwardHours: Long,
        catalogChannels: List<Channel> = emptyList(),
        replace: Boolean = false,
    ): EpgStreamResult {
        val now = clock.instant()
        val from = now.minus(backHours, ChronoUnit.HOURS)
        val to = now.plus(forwardHours, ChronoUnit.HOURS)

        val filtering = catalogChannels.isNotEmpty()
        val matched = HashSet<String>()
        val guideChannels = ArrayList<EpgChannel>()
        var added = 0

        // Учёт начинается до запроса: если процесс убьют посреди загрузки, запись останется
        // и следующий запуск перекачает гид заново (требование Александра 10.11.2026).
        tracker?.start(url, now.toEpochMilli())
        val result = client.withStream(url) { stream ->
            runBlocking {
                // Ответ получен успешно, поэтому стирать прежнюю программу уже безопасно:
                // при 500/404 или обрыве сети старая программа останется на месте.
                if (replace) {
                    store.clear()
                }
                parser.parse(stream) { programmes, channels ->
                    // Каналы объявляются в начале файла: к первой пачке передач
                    // сопоставление уже готово, дальше просто накапливается.
                    if (channels.isNotEmpty() && filtering) {
                        guideChannels += channels
                        // Те же правила, что у провайдера: основной канал и брендовые
                        // варианты. Иначе свежий одноимённый гид в базу не попадал.
                        val index = EpgIndex(guideChannels, matcher)
                        catalogChannels.forEach { matched += index.candidates(it) }
                    }

                    val inWindow = programmes.filter { it.start.isBefore(to) && it.end.isAfter(from) }
                    val wanted = if (filtering) inWindow.filter { it.channelId in matched } else inWindow
                    val channelsToStore = if (filtering) channels.filter { it.id in matched } else channels
                    added += runBlocking { store.mergeBatch(channelsToStore, wanted) }
                    // Прогресс пишется по ходу разбора: после обрыва видно, где остановились.
                    runBlocking {
                        tracker?.progress(
                            source = url,
                            parsedChannels = matched.size,
                            addedProgrammes = added,
                            now = clock.instant().toEpochMilli(),
                        )
                    }
                }
            }
        }
        return when (result) {
            is HttpStreamResult.Success -> {
                // Запись снимается только после полного разбора файла.
                tracker?.finish(url)
                android.util.Log.d(
                    "SmotrishkaSync",
                    "epg stream: источник=$url добавлено=$added всего=${result.value}",
                )
                EpgStreamResult.Success(added)
            }

            is HttpStreamResult.HttpError -> {
                tracker?.interrupted(url, clock.instant().toEpochMilli())
                EpgStreamResult.Failure("Гид ответил кодом ${result.code}")
            }

            is HttpStreamResult.NetworkError -> {
                tracker?.interrupted(url, clock.instant().toEpochMilli())
                EpgStreamResult.Failure("Гид недоступен: ${result.reason}")
            }
        }
    }
}

/** Результат потокового импорта программы передач. */
sealed interface EpgStreamResult {

    /** Импорт завершён: [added] передач добавлено. */
    data class Success(val added: Int) : EpgStreamResult

    /** Импорт не удался: [reason] — причина для пользователя. */
    data class Failure(val reason: String) : EpgStreamResult
}
