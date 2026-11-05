package com.mops1k.smotrishka.tv.ui.home

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Признак «живое превью сейчас не должно занимать декодер».
 *
 * Включается в момент открытия канала: пока идёт переход в плеер, превью отдаёт аппаратный
 * декодер, иначе канал в HEVC не запускается (у приставки t950s_be311 декодер один).
 */
val LocalPreviewSuppressed = staticCompositionLocalOf { false }
