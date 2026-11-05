package com.mops1k.smotrishka.tv.core.model

/**
 * Разобранная программа передач: каналы EPG и их передачи.
 *
 * @param channels каналы, объявленные в файле;
 * @param programmes передачи только для объявленных каналов.
 */
data class Epg(
    val channels: List<EpgChannel>,
    val programmes: List<Programme>,
)
