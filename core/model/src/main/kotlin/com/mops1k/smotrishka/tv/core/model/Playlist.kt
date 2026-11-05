package com.mops1k.smotrishka.tv.core.model

/**
 * Разобранный плейлист: категории в порядке появления и каналы в порядке вещания.
 *
 * @param categories категории каналов;
 * @param channels каналы плейлиста;
 * @param epgUrl адрес программы передач из заголовка `#EXTM3U` (`url-tvg`), если провайдер его указал.
 */
data class Playlist(
    val categories: List<Category>,
    val channels: List<Channel>,
    val epgUrl: String? = null,
)
