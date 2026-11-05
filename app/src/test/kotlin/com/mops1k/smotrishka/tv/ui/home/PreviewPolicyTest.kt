package com.mops1k.smotrishka.tv.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация политики живого превью на главном экране.
 *
 * Решение Александра (05.11.2026): живое превью показывается и на ТВ-приставке — раньше оно
 * гасит экономия трафика, а на малопамятных устройствах оно выключено принудительно: у приставки
 * t950s_be311 один аппаратный HEVC-декодер, и занятое превью не давало открыть канал.
 */
class PreviewPolicyTest {

    @Test
    fun `на телевизоре живое превью есть`() {
        assertThat(PreviewPolicy.showLive(isTv = true, isLowRam = false)).isTrue()
    }

    @Test
    fun `на малопамятном устройстве живое превью выключено`() {
        // Один аппаратный HEVC-декодер: превью не должно занимать его перед открытием канала.
        assertThat(PreviewPolicy.showLive(isTv = false, isLowRam = true)).isFalse()
    }

    @Test
    fun `на телевизоре без нехватки памяти живое превью есть`() {
        assertThat(PreviewPolicy.showLive(isTv = true, isLowRam = false)).isTrue()
    }

    @Test
    fun `на телефоне живое превью есть`() {
        assertThat(PreviewPolicy.showLive(isTv = false, isLowRam = false)).isTrue()
    }

    @Test
    fun `экономия трафика выключает превью на любом устройстве`() {
        assertThat(
            PreviewPolicy.showLive(isTv = false, isLowRam = false, dataSaver = true),
        ).isFalse()
        assertThat(
            PreviewPolicy.showLive(isTv = true, isLowRam = false, dataSaver = true),
        ).isFalse()
        assertThat(
            PreviewPolicy.showLive(isTv = true, isLowRam = false, dataSaver = true),
        ).isFalse()
    }

    @Test
    fun `устройство считается телевизором по признаку Leanback`() {
        assertThat(PreviewPolicy.isTelevision(hasLeanback = true, isLowRam = false)).isTrue()
        assertThat(PreviewPolicy.isTelevision(hasLeanback = false, isLowRam = true)).isTrue()
        assertThat(PreviewPolicy.isTelevision(hasLeanback = false, isLowRam = false)).isFalse()
    }
}
