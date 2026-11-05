package com.mops1k.smotrishka.tv.core.model

/**
 * Режим архива (catchup), как его объявляет канал в плейлисте.
 *
 * Значения соответствуют распространённым провайдерским схемам:
 * `default` — параметры `utc`/`utcend` в query, `append` — время в пути,
 * `shift`/`flussonic` — `timeshift`, `flussonic-hls` — HLS-архив Flussonic.
 */
enum class CatchupMode {
    DEFAULT,
    APPEND,
    SHIFT,
    FLUSSONIC,
    FLUSSONIC_HLS,

    /** Режим указан, но незнаком: архив считаем неизвестным. */
    UNKNOWN,
}
