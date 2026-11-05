package com.mops1k.smotrishka.tv.core.data

import com.mops1k.smotrishka.tv.core.model.EpgGuide
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Хранилище пользовательских гидов программы передач.
 *
 * Гида добавляют только после [EpgGuideValidator]: недоступный адрес в базу не попадает,
 * иначе фон каждые 6 часов ходил бы в мёртвый адрес.
 *
 * @param database база приложения;
 * @param clock источник текущего времени — по нему выставляется порядок гидов.
 */
class EpgGuideRepository(
    private val database: SmotrishkaDatabase,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    private val dao: EpgGuideDao get() = database.epgGuideDao()

    /** Сохранённые гиды в порядке добавления. */
    suspend fun guides(): List<EpgGuide> = dao.guides().map { it.toModel() }

    /**
     * Добавляет стартовый набор, если гидов ещё нет.
     *
     * Нужен, чтобы фоновое обновление работало сразу после установки, без ручной настройки.
     *
     * @return сколько гидов добавлено.
     */
    suspend fun ensureDefaults(): Int {
        val existing = dao.guides().map { it.url }.toSet()
        var added = 0
        EpgGuideDefaults.ALL.forEach { draft ->
            if (draft.url !in existing) {
                dao.upsert(draft.toEntity(id = newId(), addedAt = nextAddedAt()))
                added++
            }
        }
        return added
    }

    /**
     * Добавляет гид.
     *
     * @param url проверенный адрес XMLTV;
     * @param name имя источника; пустое — подставляется хост адреса.
     * @return сохранённый гид либо уже существующий с этим адресом.
     */
    suspend fun add(url: String, name: String = ""): EpgGuide {
        dao.byUrl(url)?.let { return it.toModel() }

        val guide = EpgGuide(
            id = newId(),
            name = name.trim().ifEmpty { guideHost(url) ?: url },
            url = url,
            addedAt = nextAddedAt(),
        )
        dao.upsert(guide.toEntity())
        return guide
    }

    /**
     * Удаляет гид.
     *
     * @param id идентификатор записи.
     */
    suspend fun remove(id: String) {
        dao.delete(id)
    }

    /**
     * Момент добавления с сохранением порядка.
     *
     * Часы могут вернуть одно и то же время (а на старте — время на миллисекунду раньше
     * предыдущей записи), поэтому новый гид всегда получает строго большее значение.
     */
    private suspend fun nextAddedAt(): Instant {
        val now = clock.instant()
        val last = dao.maxAddedAt() ?: return now
        return if (now.toEpochMilli() <= last) Instant.ofEpochMilli(last + 1) else now
    }

    private fun EpgGuideEntity.toModel() = EpgGuide(
        id = id,
        name = name,
        url = url,
        addedAt = Instant.ofEpochMilli(addedAt),
    )

    private fun EpgGuide.toEntity() = EpgGuideEntity(
        id = id,
        name = name,
        url = url,
        addedAt = addedAt.toEpochMilli(),
    )

    private fun EpgGuideDraft.toEntity(id: String, addedAt: Instant) = EpgGuideEntity(
        id = id,
        name = name,
        url = url,
        addedAt = addedAt.toEpochMilli(),
    )
}
