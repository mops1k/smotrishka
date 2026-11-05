package com.mops1k.smotrishka.tv.core.model

/**
 * Идентификатор канала.
 *
 * Берётся из `tvg-id` плейлиста, а если его нет — строится из URL потока,
 * чтобы id оставался стабильным между обновлениями каталога.
 */
@JvmInline
value class ChannelId(val value: String)
