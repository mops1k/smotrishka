package com.mops1k.smotrishka.tv.ui.player

import androidx.media3.common.MediaItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TDD-спецификация элемента живого потока.
 *
 * 05.11.2026 принудительная live-конфигурация (отступ 8 с и скорости догоняния) сломала эфир
 * на ТВ-приставке: провайдер отдаёт конечное окно, позиция уходила в минус, кадра не было.
 * Тест фиксирует, что мы больше не навязываем живой режим и отдаём плееру обычный адрес.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerLiveTest {

    @Test
    fun `поток отдаётся без принудительной live-конфигурации`() {
        val url = "http://provider.example/live/index.m3u8"
        val item = PlayerLive.mediaItem(url)

        // Ничего не навязываем: элемент совпадает со стандартным, который Media3 собирает сам.
        // Проверка на null не годится — Media3 подставляет собственные значения по умолчанию.
        assertThat(item).isEqualTo(MediaItem.fromUri(url))
        assertThat(item.localConfiguration?.uri.toString()).isEqualTo(url)
    }
}
