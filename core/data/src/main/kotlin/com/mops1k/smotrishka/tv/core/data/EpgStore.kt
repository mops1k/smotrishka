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
                dao.insertProgrammesMissing(programmes.map(mapper::toEntity))
                added = (dao.countProgrammes() - before).toInt().coerceAtLeast(0)
            }
        }
        return added
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
        dao.programmes(channelId, from.toEpochMilli(), to.toEpochMilli()).map(mapper::toModel)

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
            .groupBy { it.channelId }

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
    }
}
