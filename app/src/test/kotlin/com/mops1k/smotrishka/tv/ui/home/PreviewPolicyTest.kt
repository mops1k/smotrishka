package com.mops1k.smotrishka.tv.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация политики живого превью на главном экране.
 *
 * Решение Александра: базовый вариант — статичный визуал из макета; живое превью включается
 * только на телефонах и планшетах, потому что на ТВ-приставке лишний декодер отнимает ресурсы
 * у плеера.
 */
class PreviewPolicyTest {

    @Test
    fun `на телевизоре живого превью нет`() {
        assertThat(PreviewPolicy.showLive(isTv = true, isLowRam = false)).isFalse()
    }

    @Test
    fun `на малопамятном устройстве живого превью нет`() {
        assertThat(PreviewPolicy.showLive(isTv = false, isLowRam = true)).isFalse()
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
    }

    @Test
    fun `устройство считается телевизором по признаку Leanback`() {
        assertThat(PreviewPolicy.isTelevision(hasLeanback = true, isLowRam = false)).isTrue()
        assertThat(PreviewPolicy.isTelevision(hasLeanback = false, isLowRam = true)).isTrue()
        assertThat(PreviewPolicy.isTelevision(hasLeanback = false, isLowRam = false)).isFalse()
    }
}
