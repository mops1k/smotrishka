package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.ArchiveInfo
import java.time.Instant

/**
 * Запрос на архивное воспроизведение одной передачи.
 *
 * @param streamUrl прямой адрес канала;
 * @param archive параметры архива канала (`null` — архива нет);
 * @param programmeStart начало передачи, `null` — время неизвестно;
 * @param programmeEnd конец передачи, `null` — передача считается идущей;
 * @param now текущий момент: им проверяется глубина архива и строятся относительные URL.
 */
data class ArchiveRequest(
    val streamUrl: String,
    val archive: ArchiveInfo?,
    val programmeStart: Instant?,
    val programmeEnd: Instant?,
    val now: Instant,
)
