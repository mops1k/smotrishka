package com.mops1k.smotrishka.tv.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.mops1k.smotrishka.tv.core.data.AspectMode

/**
 * Иконки режимов кадра для OSD.
 *
 * В стандартном наборе Material нет иконок «вписать / растянуть / обрезать / 1:1», поэтому
 * рисуем их сами: маленькие векторные значки, по одному на режим.
 */
object AspectIcons {

    /** Иконка для режима. */
    fun of(mode: AspectMode): ImageVector = when (mode) {
        AspectMode.FIT -> frame(width = 20f, height = 12f, inner = true)
        AspectMode.STRETCH -> arrows()
        AspectMode.CROP -> crop()
        AspectMode.ORIGINAL -> frame(width = 12f, height = 12f, inner = false)
    }

    /** Рамка экрана и внутренний прямоугольник — «вписать» и «1:1». */
    private fun frame(width: Float, height: Float, inner: Boolean): ImageVector =
        ImageVector.Builder(
            name = if (inner) "AspectFit" else "AspectSquare",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(stroke = SolidColor(Color.White), strokeLineWidth = 1.6f) {
                moveTo(3f, 5f)
                lineTo(21f, 5f)
                lineTo(21f, 19f)
                lineTo(3f, 19f)
                close()
            }
            val left = (24f - width) / 2f
            val top = (24f - height) / 2f
            path(stroke = SolidColor(Color.White), strokeLineWidth = 1.6f) {
                moveTo(left, top)
                lineTo(left + width, top)
                lineTo(left + width, top + height)
                lineTo(left, top + height)
                close()
            }
        }.build()

    /** Две стрелки — «растянуть». */
    private fun arrows(): ImageVector = ImageVector.Builder(
        name = "AspectStretch",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 1.6f) {
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(8f, 9f)
            lineTo(4f, 12f)
            lineTo(8f, 15f)
            moveTo(16f, 9f)
            lineTo(20f, 12f)
            lineTo(16f, 15f)
        }
    }.build()

    /** Уголки — «обрезать». */
    private fun crop(): ImageVector = ImageVector.Builder(
        name = "AspectCrop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(stroke = SolidColor(Color.White), strokeLineWidth = 1.6f) {
            moveTo(3f, 8f)
            lineTo(3f, 3f)
            lineTo(8f, 3f)
            moveTo(16f, 3f)
            lineTo(21f, 3f)
            lineTo(21f, 8f)
            moveTo(21f, 16f)
            lineTo(21f, 21f)
            lineTo(16f, 21f)
            moveTo(8f, 21f)
            lineTo(3f, 21f)
            lineTo(3f, 16f)
        }
    }.build()
}
