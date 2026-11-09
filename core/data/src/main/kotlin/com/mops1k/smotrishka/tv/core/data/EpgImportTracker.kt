package com.mops1k.smotrishka.tv.core.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Учёт незавершённых импортов гида.
 *
 * Запись создаётся в начале импорта и удаляется только после полного разбора: если процесс
 * убили или сеть оборвалась, запись остаётся, и следующий запуск заново загружает этот гид.
 *
 * @param dao доступ к таблице состояний.
 */
@Singleton
class EpgImportTracker @Inject constructor(
    private val dao: EpgImportDao,
) {

    /**
     * Отмечает начало импорта.
     *
     * @param source адрес гида;
     * @param now текущий момент.
     */
    suspend fun start(source: String, now: Long) {
        android.util.Log.d("SmotrishkaSync", "epg import: начат $source")
        dao.upsert(
            EpgImportEntity(
                source = source,
                running = true,
                startedAt = now,
                updatedAt = now,
                parsedChannels = 0,
                addedProgrammes = 0,
            ),
        )
    }

    /**
     * Записывает прогресс разбора.
     *
     * @param source адрес гида;
     * @param parsedChannels сколько каналов гида уже сохранено;
     * @param addedProgrammes сколько передач добавлено;
     * @param now текущий момент.
     */
    suspend fun progress(source: String, parsedChannels: Int, addedProgrammes: Int, now: Long) {
        val current = dao.bySource(source) ?: return
        dao.upsert(
            current.copy(
                updatedAt = now,
                parsedChannels = parsedChannels,
                addedProgrammes = addedProgrammes,
            ),
        )
    }

    /**
     * Отмечает импорт завершённым: запись удаляется.
     *
     * @param source адрес гида.
     */
    suspend fun finish(source: String) {
        android.util.Log.d("SmotrishkaSync", "epg import: завершён $source")
        dao.delete(source)
    }

    /**
     * Отмечает импорт прерванным: запись остаётся для повтора.
     *
     * @param source адрес гида;
     * @param now текущий момент.
     */
    suspend fun interrupted(source: String, now: Long) {
        val current = dao.bySource(source) ?: return
        android.util.Log.d("SmotrishkaSync", "epg import: прерван $source")
        dao.upsert(current.copy(running = false, updatedAt = now))
    }

    /**
     * Оставались ли незавершённые импорты этого источника.
     *
     * @param source адрес гида.
     */
    suspend fun isUnfinished(source: String): Boolean = dao.bySource(source) != null

    /** Все незавершённые импорты. */
    suspend fun unfinished(): List<EpgImportEntity> = dao.all()
}
