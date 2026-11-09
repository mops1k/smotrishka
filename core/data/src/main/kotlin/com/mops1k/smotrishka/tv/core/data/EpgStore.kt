package com.mops1k.smotrishka.tv.core.data

import androidx.room.withTransaction
import com.mops1k.smotrishka.tv.core.model.Epg
import com.mops1k.smotrishka.tv.core.model.EpgChannel
import com.mops1k.smotrishka.tv.core.model.Programme
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Программа передач в Room.
 *
 * Окно хранения: назад — глубина архива из настроек (до семи суток), вперёд — 48 часов.
 * «Сейчас/далее» этого хватает, а база не растёт бесконечно. Обрезка вызывается после
 * загрузки нового EPG и обязана совпадать с окном хранения, иначе выбранная глубина
 * архива не работает: передачи старше жёсткого порога удалялись бы сразу.
 *
 * @param database база каталога и программы;
 * @param mapper перевод модели в строки и обратно.
 */
class EpgStore(
    private val database: SmotrishkaDatabase,
    private val mapper: EpgMapper = EpgMapper(),
) {

    private val dao: EpgDao get() = database.epgDao()

    /** Число передач в базе: поток обновляется после каждой записи гида. */
    val programmeCount: kotlinx.coroutines.flow.Flow<Int> = dao.observeProgrammeCount()

    /**
     * Заменяет программу передач целиком.
     *
     * @param epg разобранный XMLTV.
     */
    suspend fun replace(epg: Epg) {
        database.withTransaction {
            dao.deleteProgrammes()
            dao.deleteChannels()
            dao.insertChannels(epg.channels.map(mapper::toEntity))
            dao.insertProgrammes(epg.programmes.map(mapper::toEntity))
        }
    }

    /**
     * Дополняет программу передач, не перетирая уже загруженную.
     *
     * Нужно для публичных источников EPG: провайдер покрывает не все каналы, и программа
     * из общего гида добавляет только те передачи, которых ещё нет.
     *
     * @param epg разобранный XMLTV дополнительного источника.
     * @return сколько передач фактически добавлено.
     */
    suspend fun merge(epg: Epg): Int {
        var added = 0
        database.withTransaction {
            dao.insertChannels(epg.channels.map(mapper::toEntity))
            val entities = epg.programmes.map(mapper::toEntity)
            val before = dao.countProgrammes()
            dao.insertProgrammesMissing(entities)
            fillDescriptions(entities)
            added = (dao.countProgrammes() - before).toInt().coerceAtLeast(0)
        }
        return added
    }

    /**
     * Дописывает пачку передач, не перетирая уже загруженные.
     *
     * Используется потоковым импортом публичных гидов: пачки приходят по мере разбора.
     *
     * @param channels каналы EPG из той же пачки;
     * @param programmes передачи пачки.
     * @return сколько передач фактически добавлено.
     */
    suspend fun mergeBatch(channels: List<EpgChannel>, programmes: List<Programme>): Int {
        var added = 0
        database.withTransaction {
            if (channels.isNotEmpty()) dao.insertChannels(channels.map(mapper::toEntity))
            if (programmes.isNotEmpty()) {
                val before = dao.countProgrammes()
                val entities = programmes.map(mapper::toEntity)
                dao.insertProgrammesMissing(entities)
                fillDescriptions(entities)
                added = (dao.countProgrammes() - before).toInt().coerceAtLeast(0)
            }
        }
        return added
    }

    /**
     * Дописывает описания уже загруженным передачам.
     *
     * Провайдерский гид и публичный могут описывать одну и ту же передачу: название и время
     * остаются провайдерскими, а описание дозаполняется, если его не было (замечание
     * Александра 09.11.2026: описаний не было нигде).
     *
     * @param entities передачи из импортированной пачки.
     */
    private suspend fun fillDescriptions(entities: List<ProgrammeEntity>) {
        entities.forEach { entity ->
            val description = entity.description?.takeIf { it.isNotBlank() } ?: return@forEach
            dao.fillDescription(
                channelId = entity.channelId,
                startAt = entity.startAt,
                description = description,
            )
        }
    }

    /**
     * Очищает программу передач целиком.
     *
     * Нужно при смене источника и перед ручным обновлением: иначе на экране остаются передачи
     * прежнего провайдера, привязанные к тем же идентификаторам каналов.
     */
    suspend fun clear() {
        database.withTransaction {
            dao.deleteProgrammes()
            dao.deleteChannels()
        }
    }

    /**
     * Очищает программу передач и сжимает файл базы.
     *
     * Нужно после неудачных импортов гидов: удалённые строки не уменьшают файл сами,
     * и база остаётся в сотни мегабайт (`VACUUM` переписывает её заново).
     */
    suspend fun compact() {
        clear()
        // VACUUM нельзя выполнять внутри транзакции — вызываем отдельно.
        database.openHelper.writableDatabase.execSQL("VACUUM")
    }

    /**
     * Передачи канала в интервале.
     *
     * @param channelId идентификатор канала EPG;
     * @param from начало интервала;
     * @param to конец интервала.
     */
    suspend fun programmes(channelId: String, from: Instant, to: Instant): List<Programme> =
        dao.programmes(channelId, from.toEpochMilli(), to.toEpochMilli())
            .map(mapper::toModel)
            // Один канал каталога может получать сетку из нескольких гидов, и одна и та же
            // передача приходит несколько раз. Правило дедупликации одно для всех экранов —
            // см. [withoutGuideDuplicates].
            .withoutGuideDuplicates()

    /**
     * Передачи всего окна сразу, сгруппированные по каналу EPG.
     *
     * Экран каналов раньше спрашивал базу отдельно на каждый канал: на каталоге в 3752 канала это
     * давало больше десяти секунд до появления списка. Один запрос на всё окно решает проблему.
     *
     * @param from начало интервала;
     * @param to конец интервала.
     */
    suspend fun programmesByChannel(from: Instant, to: Instant): Map<String, List<Programme>> =
        // Нижняя граница сдвинута на максимальную длительность передачи: иначе передачи,
        // начавшиеся до окна и всё ещё идущие, в выборку не попадут.
        dao.programmesStartingBetween(
            fromAt = from.minus(PROGRAMME_MAX_HOURS, ChronoUnit.HOURS).toEpochMilli(),
            toAt = to.toEpochMilli(),
        )
            .map(mapper::toModel)
            // Пересечение с окном проверяем уже в памяти — по индексированному диапазону.
            .filter { it.end.isAfter(from) }
            // Сначала группируем по каналу, и только потом склеиваем дубли из разных гидов:
            // иначе склейка сравнивала бы передачи разных каналов и теряла почти всё.
            .groupBy { it.channelId }
            // То же правило, что у одиночной выборки: одинаковое название и перекрытие.
            .mapValues { (_, programmes) -> programmes.withoutGuideDuplicates() }

    /**
     * До какого момента гид покрывает эфир.
     *
     * По этому времени планируется обновление: гид обновляется за час до конца покрытия,
     * а не по расписанию «каждые N часов». Глубина покрытия зависит от источника и настроек.
     */
    suspend fun coverageEnd(): Instant? = dao.maxEndAt()?.let { Instant.ofEpochMilli(it) }

    /** Сколько каналов гида имеют иконку: по этому признаку видно, нужен ли переимпорт. */
    suspend fun iconCount(): Int = dao.countIcons()

    /**
     * Иконки каналов гида.
     *
     * @param epgIds нужные идентификаторы каналов гида.
     * @return соответствие «канал гида → адрес иконки» (только непустые).
     */
    suspend fun icons(epgIds: Collection<String>): Map<String, String> {
        if (epgIds.isEmpty()) return emptyMap()
        return dao.iconsFor(epgIds.toList())
            .mapNotNull { row -> row.iconUrl?.takeIf { it.isNotBlank() }?.let { row.id to it } }
            .toMap()
    }

    /** Каналы EPG. */
    suspend fun epgChannels(): List<EpgChannel> = dao.channels().map(mapper::toModel)

    /**
     * Сколько разных каналов имеют передачи в интервале.
     *
     * @param from начало интервала;
     * @param to конец интервала.
     */
    suspend fun coveredChannels(from: Instant, to: Instant): Int =
        dao.countCoveredChannels(from.toEpochMilli(), to.toEpochMilli())

    /**
     * Каналы гида, у которых есть передачи в интервале.
     *
     * Нужны, чтобы выбрать свежий гид, когда привязанный по `tvg-id` источник перестал
     * обновлять канал: у одного канала в разных гидах разные идентификаторы.
     *
     * @param from начало интервала;
     * @param to конец интервала.
     */
    suspend fun coveredChannelIds(from: Instant, to: Instant): Set<String> =
        dao.coveredChannelIds(from.toEpochMilli(), to.toEpochMilli()).toSet()

    /**
     * Каналы гида со свежими передачами вокруг момента.
     *
     * @param now текущий момент;
     * @param hours половина окна в часах: проверяются передачи от `now - hours` до `now + hours`.
     */
    suspend fun freshChannelIds(now: Instant, hours: Long = FRESH_HOURS): Set<String> =
        coveredChannelIds(now.minus(hours, ChronoUnit.HOURS), now.plus(hours, ChronoUnit.HOURS))

    /**
     * Похожи ли сетки двух каналов гида в окне.
     *
     * Сверяются времена начала передач с допуском: один и тот же канал в разных гидах имеет
     * одинаковую сетку, а разные каналы с похожим названием — нет. Строки-заглушки
     * («Програма відсутня», «Нет программы») не участвуют: у epg.one они занимают по 12 часов
     * и никогда не совпадают с реальной сеткой другого гида (проверено 10.11.2026).
     * Проверка защищает фолбэк на свежий гид от подмены программы чужого канала.
     *
     * @param first канал гида из сохранённой связи;
     * @param second канал гида-кандидата;
     * @param from начало окна сверки;
     * @param to конец окна сверки;
     * @return `true`, если совпала хотя бы половина передач (и минимум две), либо сверить
     *   нечем — у одного из каналов в окне нет настоящих передач.
     */
    suspend fun sameSchedule(first: String, second: String, from: Instant, to: Instant): Boolean {
        val left = dao.startsWithTitles(first, from.toEpochMilli(), to.toEpochMilli())
            .filterNot { EpgPlaceholders.isPlaceholder(it.title) }
            .map { it.startAt }
        val right = dao.startsWithTitles(second, from.toEpochMilli(), to.toEpochMilli())
            .filterNot { EpgPlaceholders.isPlaceholder(it.title) }
            .map { it.startAt }
        if (left.isEmpty() || right.isEmpty()) return true
        val common = left.count { start -> right.any { kotlin.math.abs(it - start) <= MINUTE_MS } }
        return common >= MIN_COMMON_PROGRAMMES && common * 2 >= minOf(left.size, right.size)
    }

    /**
     * Название передачи канала, идущей в момент.
     *
     * @param channelId канал гида;
     * @param at момент;
     * @return название или `null`, если передачи нет.
     */
    suspend fun currentTitle(channelId: String, at: Instant): String? =
        dao.currentTitle(channelId, at.toEpochMilli())

    /**
     * Удаляет передачи за пределами окна хранения.
     *
     * @param now текущий момент;
     * @param backHours сколько часов назад хранить — глубина архива из настроек.
     */
    suspend fun prune(now: Instant, backHours: Long) {
        dao.deleteOutside(
            fromAt = now.minus(backHours, ChronoUnit.HOURS).toEpochMilli(),
            toAt = now.plus(WINDOW_FORWARD_HOURS, ChronoUnit.HOURS).toEpochMilli(),
        )
    }

    private companion object {
        const val WINDOW_FORWARD_HOURS = 48L

        /** Максимальная длительность передачи: запас для нижней границы выборки. */
        const val PROGRAMME_MAX_HOURS = 8L

        /** Половина окна свежести гида: столько часов назад и вперёд ищем передачи. */
        const val FRESH_HOURS = 12L

        /** Допуск при сверке сеток: время начала может отличаться на несколько минут. */
        const val MINUTE_MS = 10L * 60_000L

        /** Минимум общих передач, чтобы считать сетки одинаковыми: у «умирающего» гида
         * за сутки может остаться одна настоящая передача, и одного совпадения достаточно. */
        const val MIN_COMMON_PROGRAMMES = 1
    }
}
