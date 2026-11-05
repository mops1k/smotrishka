package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.AspectMode
import org.junit.Test

/**
 * TDD-спецификация режимов кадра.
 */
class AspectModesTest {

    @Test
    fun `режимы переключаются по кругу`() {
        assertThat(AspectModes.next(AspectMode.FIT)).isEqualTo(AspectMode.STRETCH)
        assertThat(AspectModes.next(AspectMode.STRETCH)).isEqualTo(AspectMode.CROP)
        assertThat(AspectModes.next(AspectMode.CROP)).isEqualTo(AspectMode.ORIGINAL)
        assertThat(AspectModes.next(AspectMode.ORIGINAL)).isEqualTo(AspectMode.FIT)
    }

    @Test
    fun `у каждого режима есть подпись`() {
        AspectModes.ORDER.forEach { mode ->
            assertThat(AspectModes.label(mode)).isNotEmpty()
        }
    }
}
