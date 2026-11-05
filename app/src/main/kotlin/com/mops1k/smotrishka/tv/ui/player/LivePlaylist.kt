package com.mops1k.smotrishka.tv.ui.player

/**
 * Разбор плейлиста эфира.
 *
 * Плейлист провайдера — обычный HLS-список: строки `#EXTINF`, за ними ссылки на сегменты.
 * Разбор отделён от сети, чтобы порядок и дедупликация проверялись тестом, а не на устройстве.
 */
object LivePlaylist {

    /**
     * Достаёт сегменты из плейлиста.
     *
     * @param body текст плейлиста;
     * @return ссылки на сегменты в порядке плейлиста, без повторов по метке времени.
     */
    fun segments(body: String): List<String> = LiveSegments.unique(
        body.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("http") }
            .toList(),
    )

    /**
     * Метка времени последнего сегмента.
     *
     * @param body текст плейлиста;
     * @return метка в миллисекундах либо `null`, если сегментов нет.
     */
    fun lastTimestamp(body: String): Long? =
        segments(body).lastOrNull()?.let(LiveSegments::timestampOf)
}
