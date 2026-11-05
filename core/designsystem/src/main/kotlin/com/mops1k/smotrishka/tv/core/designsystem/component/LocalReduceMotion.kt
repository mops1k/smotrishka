package com.mops1k.smotrishka.tv.core.designsystem.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Признак «убрать анимации».
 *
 * На слабых приставках анимации съедают кадры и делают управление пультом вялым, поэтому
 * интерфейс переключается сразу, без промежуточных состояний. Значение задаётся один раз на
 * всё приложение по профилю устройства.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }
