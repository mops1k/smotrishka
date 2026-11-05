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
    }
}
